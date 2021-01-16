/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Registers {@link EventListener} methods as individual {@link ApplicationListener} instances.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.2
 * https://blog.csdn.net/weixin_34174105/article/details/92409226 源码分析
 * 
 * https://blog.csdn.net/baidu_19473529/article/details/97646739 源码分析
 * SimpleApplicationEventMulticaster#multicastEvent->invokeListener->doInvokeListener
 * 后面就是触发事件监听了AbstractApplicationContext#publishEvent
 */
public class EventListenerMethodProcessor implements SmartInitializingSingleton, ApplicationContextAware {

	protected final Log logger = LogFactory.getLog(getClass());
	 // 用于记录检测发现`@EventListener`注解方法，生成和注册`ApplicationListener`实例的应用上下文
	@Nullable
	private ConfigurableApplicationContext applicationContext;

	private final EventExpressionEvaluator evaluator = new EventExpressionEvaluator();
    // 缓存机制，记住那些根本任何方法上没有使用注解 @EventListener 的类，避免处理过程中二次处理 
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
				"ApplicationContext does not implement ConfigurableApplicationContext");
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	private ConfigurableApplicationContext getApplicationContext() {
		Assert.state(this.applicationContext != null, "No ApplicationContext set");
		return this.applicationContext;
	}

	//找到这个方法的实现，所有的单例bean实例化之后会执行这个方法
	@Override
	public void afterSingletonsInstantiated() {
		//下面开始创建ApplicationListener，获得创建ApplicationListener的工厂集合
		List<EventListenerFactory> factories = getEventListenerFactories();
		//获得配置上下文对象
		ConfigurableApplicationContext context = getApplicationContext();
		String[] beanNames = context.getBeanNamesForType(Object.class);
		//触发所有适用bean的初始化后回调 主要是afterSingletonsInstantiated方法
		for (String beanName : beanNames) {
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
				Class<?> type = null;
				try {
					//获取bean的初始目标类的类型
					type = AutoProxyUtils.determineTargetClass(context.getBeanFactory(), beanName);
				}
				catch (Throwable ex) {
					// An unresolvable bean type, probably from a lazy bean - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
					}
				}
				if (type != null) {
					//type表示的类或接口的超类或超接口是否和ScopedObject一样
					if (ScopedObject.class.isAssignableFrom(type)) {
						try {
							Class<?> targetClass = AutoProxyUtils.determineTargetClass(
									context.getBeanFactory(), ScopedProxyUtils.getTargetBeanName(beanName));
							if (targetClass != null) {
								type = targetClass;
							}
						}
						catch (Throwable ex) {
							// An invalid scoped proxy arrangement - let's ignore it.
							if (logger.isDebugEnabled()) {
								logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
							}
						}
					}
					try {
						processBean(factories, beanName, type);
					}
					catch (Throwable ex) {
						throw new BeanInitializationException("Failed to process @EventListener " +
								"annotation on bean with name '" + beanName + "'", ex);
					}
				}
			}
		}
	}


	/**
	 * Return the {@link EventListenerFactory} instances to use to handle
	 * {@link EventListener} annotated methods.
	 */
	protected List<EventListenerFactory> getEventListenerFactories() {
		Map<String, EventListenerFactory> beans = getApplicationContext().getBeansOfType(EventListenerFactory.class);
		List<EventListenerFactory> factories = new ArrayList<>(beans.values());
		AnnotationAwareOrderComparator.sort(factories);
		return factories;
	}
	// 该方法拿到某个bean的名称和它的目标类，在这个范围上检测`@EventListener`注解方法，
	   // 生成和注册`ApplicationListener`实例
	protected void processBean(
			final List<EventListenerFactory> factories, final String beanName, final Class<?> targetType) {

		if (!this.nonAnnotatedClasses.contains(targetType)) {
			Map<Method, EventListener> annotatedMethods = null;
			try {
				// 拿到使用了@EventListener注解的方法
				   // *** 注意这里, 这里检测当前类targetType上使用了注解 @EventListener 的方法
				annotatedMethods = MethodIntrospector.selectMethods(targetType,
						(MethodIntrospector.MetadataLookup<EventListener>) method ->
								AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class));
			}
			catch (Throwable ex) {
				// An unresolvable type in a method signature, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
				}
			}
			if (CollectionUtils.isEmpty(annotatedMethods)) {
			    // 如果当前类 targetType 中没有任何使用了 注解 @EventListener 的方法，则将该类保存到
	              // 缓存 nonAnnotatedClasses, 从而避免当前处理方法重入该类，其目的应该是为了提高效率，
				this.nonAnnotatedClasses.add(targetType);
				if (logger.isTraceEnabled()) {
					logger.trace("No @EventListener annotations found on bean class: " + targetType.getName());
				}
			}
			else {
				// 发现当前类 targetType 中有些方法使用了注解 @EventListener,现在根据这些方法上的信息
	              // 对应地创建和注册ApplicationListener实例
				// Non-empty set of methods 获取配置上下文对象
				ConfigurableApplicationContext context = getApplicationContext();
				for (Method method : annotatedMethods.keySet()) {
					  // 注意，这里使用到了  this.eventListenerFactories, 这些 EventListenerFactory 是在 
		              // 该类 postProcessBeanFactory 方法调用时被记录的
					for (EventListenerFactory factory : factories) {
						if (factory.supportsMethod(method)) {
							Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
							//创建applicationListener对象，返回的是GenericApplicationListener的adaptor ApplicationListenerMethodAdapter对象
							ApplicationListener<?> applicationListener =
									factory.createApplicationListener(beanName, targetType, methodToUse);
							
							//如果applicationListenr的具体类型是ApplicationListenerMethodTransactionalAdapter，
							//把这个监听器配置到spring配置上下文中，这个监听器类型是支持事务的监听器，在spring-tx包源码解析中会具体解析
		                    // context.addApplicationListener(applicationListener);
							if (applicationListener instanceof ApplicationListenerMethodAdapter) {
								//如果applicationListenr的具体类型是ApplicationListenerMethodAdapter，就进行初始化
								((ApplicationListenerMethodAdapter) applicationListener).init(context, this.evaluator);
							}
							//如果applicationListenr的具体类型是ApplicationListenerMethodTransactionalAdapter
							//，把这个监听器配置到spring配置上下文中
							context.addApplicationListener(applicationListener);
							break;
						}
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @EventListener methods processed on bean '" +
							beanName + "': " + annotatedMethods);
				}
			}
		}
	}

}
