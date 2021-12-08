/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.context.named;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.MapPropertySource;

/**命名的上下文工厂,可以理解为可命名的内置容器工厂
 * Creates a set of child contexts that allows a set of Specifications to define the beans
 * in each child context.
 *
 * Ported from spring-cloud-netflix FeignClientFactory and SpringClientFactory
 *
 * @param <C> specification
 * @author Spencer Gibb
 * @author Dave Syer
 */
// TODO: add javadoc
public abstract class NamedContextFactory<C extends NamedContextFactory.Specification>
		implements DisposableBean, ApplicationContextAware {

	private final String propertySourceName;

	private final String propertyName;
	// 内置容器集合，key是应用名称// 内置容器集合
	private Map<String, AnnotationConfigApplicationContext> contexts = new ConcurrentHashMap<>();
	// 可用配置类，key是应用名称或者default开头
	private Map<String, C> configurations = new ConcurrentHashMap<>();

	private ApplicationContext parent;
	 // 默认配置类
	private Class<?> defaultConfigType;

	public NamedContextFactory(Class<?> defaultConfigType, String propertySourceName,
			String propertyName) {
		this.defaultConfigType = defaultConfigType;
		this.propertySourceName = propertySourceName;
		this.propertyName = propertyName;
	}

	@Override
	public void setApplicationContext(ApplicationContext parent) throws BeansException {
		this.parent = parent;
	}
	 // 将RibbonClientSpecification 的配置按 name > class 的形式保存
	public void setConfigurations(List<C> configurations) {
		for (C client : configurations) {
			this.configurations.put(client.getName(), client);
		}
	}

	public Set<String> getContextNames() {
		return new HashSet<>(this.contexts.keySet());
	}

	@Override
	public void destroy() {
		Collection<AnnotationConfigApplicationContext> values = this.contexts.values();
		for (AnnotationConfigApplicationContext context : values) {
			// This can fail, but it never throws an exception (you see stack traces
			// logged as WARN).
			context.close();
		}
		this.contexts.clear();
	}

	protected AnnotationConfigApplicationContext getContext(String name) {
		//如果 map 中不存在，则创建
		if (!this.contexts.containsKey(name)) {
			synchronized (this.contexts) {
				if (!this.contexts.containsKey(name)) {
					 // 如果不存在指定服务的容器，创建一个，然后存起来以备复用
					this.contexts.put(name, createContext(name));
				}
			}
		}
		return this.contexts.get(name);
	}
	 // 创建指定服务名称对应的内置容器
	protected AnnotationConfigApplicationContext createContext(String name) {
		 // 创建容器
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		// 如果配置类上含有指定应用名称的键值对，那么将此 RibbonClientSpecification 的 configuration 类都注册到内置容器中
        // 可通过@RibbonClient将自定义Ribbon服务配置引入此容器中
		if (this.configurations.containsKey(name)) {
			for (Class<?> configuration : this.configurations.get(name)
					.getConfiguration()) {
				context.register(configuration);
			}
		}
		// 配置类的key 是否是 "default." 开头的，如果是，那么也注册到服务对应的内置容器中
        // EurekaRibbonClientConfiguration 就是默认的配置，在此步骤注册
		//这里是解析了@RibbonClients的注解,加入配置中，随后解析，实例化注解的类加入到容器中
		//这里之后增加带RibbonClients的注解信息
		for (Map.Entry<String, C> entry : this.configurations.entrySet()) {
			if (entry.getKey().startsWith("default.")) {
				for (Class<?> configuration : entry.getValue().getConfiguration()) {
					context.register(configuration);
				}
			}
		}
		 // 注册默认的配置和环境变量替换器 ， RibbonClientConfiguration 就是在此步骤注册,
		 //在这里增加RibbonClientConfiguration放到容器里面，通过容器实例化
		context.register(PropertyPlaceholderAutoConfiguration.class,
				this.defaultConfigType);
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
				this.propertySourceName,
				Collections.<String, Object>singletonMap(this.propertyName, name)));
		if (this.parent != null) {
			// Uses Environment from parent as well as beans
			context.setParent(this.parent);
			// jdk11 issue
			// https://github.com/spring-cloud/spring-cloud-netflix/issues/3101
			//由于 JDK 11 LTS 相对于 JDK 8 LTS 多了模块化，通过 ClassUtils.getDefaultClassLoader() 有所不同
			//在 JDK 8 中获取的就是定制的类加载器，JDK 11 中获取的是默认的类加载器，这样会有问题
			//所以，这里需要手动设置当前 context 的类加载器为父 context 的类加载器
			context.setClassLoader(this.parent.getClassLoader());
		}
		context.setDisplayName(generateDisplayName(name));
		context.refresh();
		return context;
	}

	protected String generateDisplayName(String name) {
		return this.getClass().getSimpleName() + "-" + name;
	}

	public <T> T getInstance(String name, Class<T> type) {
		 // 获取服务名称对应的容器
		AnnotationConfigApplicationContext context = getContext(name);
		if (BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				type).length > 0) {
			return context.getBean(type);
		}
		return null;
	}

	public <T> ObjectProvider<T> getLazyProvider(String name, Class<T> type) {
		return new ClientFactoryObjectProvider<>(this, name, type);
	}

	public <T> ObjectProvider<T> getProvider(String name, Class<T> type) {
		AnnotationConfigApplicationContext context = getContext(name);
		return context.getBeanProvider(type);
	}

	public <T> T getInstance(String name, Class<?> clazz, Class<?>... generics) {
		ResolvableType type = ResolvableType.forClassWithGenerics(clazz, generics);
		return getInstance(name, type);
	}
	/**
	 * 获取某个 name 的 ApplicationContext 里面的某个类型的 Bean
	 * @param name 子 ApplicationContext 名称
	 * @param type 类型
	 * @param <T> Bean 类型
	 * @return Bean
	 */
	@SuppressWarnings("unchecked")
	public <T> T getInstance(String name, ResolvableType type) {
		AnnotationConfigApplicationContext context = getContext(name);
		String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				type);
		if (beanNames.length > 0) {
			for (String beanName : beanNames) {
				if (context.isTypeMatch(beanName, type)) {
					return (T) context.getBean(beanName);
				}
			}
		}
		return null;
	}

	public <T> Map<String, T> getInstances(String name, Class<T> type) {
		AnnotationConfigApplicationContext context = getContext(name);
		if (BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				type).length > 0) {
			return BeanFactoryUtils.beansOfTypeIncludingAncestors(context, type);
		}
		return null;
	}

	/**
	 * Specification with name and configuration.
	 */
	public interface Specification {

		String getName();

		Class<?>[] getConfiguration();

	}

}
