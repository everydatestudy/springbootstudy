/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Complete implementation of the
 * {@link org.springframework.beans.factory.support.AutowireCandidateResolver} strategy
 * interface, providing support for qualifier annotations as well as for lazy resolution
 * driven by the {@link Lazy} annotation in the {@code context.annotation} package.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
//最后，把这个四个哥们 从上至下 简单总结如下：
//
//SimpleAutowireCandidateResolver 相当于一个简单的适配器
//GenericTypeAwareAutowireCandidateResolver 判断泛型是否匹配，支持泛型依赖注入（From Spring4.0）
//QualifierAnnotationAutowireCandidateResolver 处理 @Qualifier 和 @Value 注解
//ContextAnnotationAutowireCandidateResolver 处理 @Lazy 注解，重写了 getLazyResolutionProxyIfNecessary 方法
//官方把这个类描述为：策略接口的完整实现。它不仅仅支持上面所有描述的功能，还支持@Lazy懒处理~~~(注意此处懒处理(延迟处理)，不是懒加载~)
//@Lazy一般含义是懒加载，它只会作用于BeanDefinition.setLazyInit()。而此处给它增加了一个能力：延迟处理（代理处理）
//使用CustomAutowireConfigurer自定义qualifier注解
//这其实属于一个骚操作（不明觉厉但然并卵），炫技用，绝大部分情况下都是木有必要这么做的。但是话说回来，如果这么玩了，说不定能成为你面试的砝码，毕竟面试还是需要造飞机嘛，因此此处我写一个案例Demo供给大家参考~
//
//如果你通过本实例助攻获取到了一个offer，不要忘记请我吃饭哦，哈哈~~~
//
//其实通过上面实例已经知道了QualifierAnnotationAutowireCandidateResolver它是支持添加我们自定义的qualifier注解类型的，原理就是它，我们只是想办法往里添加就成，此处Spring给我们提供了CustomAutowireConfigurer来达到这一点。
//
//由于CustomAutowireConfigurer的源代码非常的简单，因此此处就不再展示了，提示使用时注意如下两点就行：
//
//Set<?> customQualifierTypes这个set里面可以是Class类型，也可以是全类名的String类型
//不管是什么类型，必须是annotation type（注解类型）
public class ContextAnnotationAutowireCandidateResolver extends QualifierAnnotationAutowireCandidateResolver {

	@Override
	@Nullable
	public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
		// 如果isLazy=true  那就返回一个代理，否则返回null
		// 相当于若标注了@Lazy注解，就会返回一个代理（当然@Lazy注解的value值不能是false）
		return (isLazy(descriptor) ? buildLazyResolutionProxy(descriptor, beanName) : null);
	}

	protected boolean isLazy(DependencyDescriptor descriptor) {
		for (Annotation ann : descriptor.getAnnotations()) {
			Lazy lazy = AnnotationUtils.getAnnotation(ann, Lazy.class);
			if (lazy != null && lazy.value()) {
				return true;
			}
		}
		MethodParameter methodParam = descriptor.getMethodParameter();
		if (methodParam != null) {
			Method method = methodParam.getMethod();
			if (method == null || void.class == method.getReturnType()) {
				Lazy lazy = AnnotationUtils.getAnnotation(methodParam.getAnnotatedElement(), Lazy.class);
				if (lazy != null && lazy.value()) {
					return true;
				}
			}
		}
		return false;
	}

	protected Object buildLazyResolutionProxy(final DependencyDescriptor descriptor, final @Nullable String beanName) {
		Assert.state(getBeanFactory() instanceof DefaultListableBeanFactory,
				"BeanFactory needs to be a DefaultListableBeanFactory");
		// 这里毫不客气的使用了面向实现类编程，使用了DefaultListableBeanFactory.doResolveDependency()方法~~~
		final DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) getBeanFactory();
		//TargetSource 是它实现懒加载的核心原因，在AOP那一章节了重点提到过这个接口，此处不再叙述
		// 它有很多的著名实现如HotSwappableTargetSource、SingletonTargetSource、LazyInitTargetSource、
		//SimpleBeanTargetSource、ThreadLocalTargetSource、PrototypeTargetSource等等非常多
		// 此处因为只需要自己用，所以采用匿名内部类的方式实现~~~ 此处最重要是看getTarget方法，它在被使用的时候（也就是代理对象真正使用的时候执行~~~）

		TargetSource ts = new TargetSource() {
			@Override
			public Class<?> getTargetClass() {
				return descriptor.getDependencyType();
			}
			@Override
			public boolean isStatic() {
				return false;
			}
			// getTarget是调用代理方法的时候会调用的，所以执行每个代理方法都会执行此方法，这也是为何doResolveDependency
			// 我个人认为它在效率上，是存在一定的问题的~~~所以此处建议尽量少用@Lazy~~~   
			//不过效率上应该还好，对比http、序列化反序列化处理，简直不值一提  所以还是无所谓  用吧
			@Override
			public Object getTarget() {
				Object target = beanFactory.doResolveDependency(descriptor, beanName, null, null);
				if (target == null) {
					Class<?> type = getTargetClass();
					if (Map.class == type) {
						return Collections.emptyMap();
					}
					else if (List.class == type) {
						return Collections.emptyList();
					}
					else if (Set.class == type || Collection.class == type) {
						return Collections.emptySet();
					}
					throw new NoSuchBeanDefinitionException(descriptor.getResolvableType(),
							"Optional dependency not present for lazy injection point");
				}
				return target;
			}
			@Override
			public void releaseTarget(Object target) {
			}
		};
		// 使用ProxyFactory  给ts生成一个代理
		// 由此可见最终生成的代理对象的目标对象其实是TargetSource,而TargetSource的目标才是我们业务的对象
		ProxyFactory pf = new ProxyFactory();
		pf.setTargetSource(ts);
		Class<?> dependencyType = descriptor.getDependencyType();
		if (dependencyType.isInterface()) {
			pf.addInterface(dependencyType);
		}
//		标注有@Lazy注解完成注入的时候，最终注入只是一个此处临时生成的代理对象，只有在真正执行目标方法的时候才会去容器内拿到真是的bean实例来执行目标方法。
//
//		特别注意：此代理对象非彼代理对象，这个一定一定一定要区分开来~
//		通过@Lazy注解能够解决很多情况下的循环依赖问题，它的基本思想是先'随便'给你创建一个代理对象先放着，等你真正执行方法的时候再实际去容器内找出目标实例执行~
//		我们要明白这种解决问题的思路带来的好处是能够解决很多场景下的循环依赖问题，
//		但是要知道它每次执行目标方法的时候都会去执行TargetSource.getTarget()方法，所以需要做好缓存，避免对执行效率的影响（实测执行效率上的影响可以忽略不计）
		return pf.getProxy(beanFactory.getBeanClassLoader());
	}

}
