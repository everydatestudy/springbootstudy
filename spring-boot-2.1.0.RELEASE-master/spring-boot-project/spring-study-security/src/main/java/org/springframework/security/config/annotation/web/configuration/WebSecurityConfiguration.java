/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.security.config.annotation.web.configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.SecurityConfigurer;
import org.springframework.security.config.annotation.web.WebSecurityConfigurer;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.context.DelegatingApplicationListener;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

/**
 * 该配置类WebSecurityConfiguration使用一个WebSecurity对象基于用户指定的或者默认的安全配置，
 * 创建一个FilterChainProxy bean来对用户请求进行安全过滤。这个FilterChainProxy
 * bean的名称为springSecurityFilterChain,它也是一个Filter，最终会被作为Servlet过滤器链中的一个Filter应用到Servlet容器中。
 * 
 * 你可以认为WebSecurityConfiguration是Spring
 * Web安全过滤器springSecurityFilterChain的提供方。至于该安全过滤器如何被获取和使用，我们在其他文章中分析。
 * 
 * 这里的安全配置可能来自XML配置，也可能来自Java配置类。在基于Springboot的web应用中，
 * 通常基于WebSecurityConfigurerAdapter的某个子类,该子类由开发人员实现并带上注解@Configuration,用于进行定制安全配置。
 * ———————————————— 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0
 * BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/82313144 
 * Uses a
 * {@link WebSecurity} to create the {@link FilterChainProxy} that performs the
 * web based security for Spring Security. It then exports the necessary beans.
 * Customizations can be made to {@link WebSecurity} by extending
 * {@link WebSecurityConfigurerAdapter} and exposing it as a
 * {@link Configuration} or implementing {@link WebSecurityConfigurer} and
 * exposing it as a {@link Configuration}. This configuration is imported when
 * using {@link EnableWebSecurity}.
 *
 * @see EnableWebSecurity
 * @see WebSecurity
 *
 * @author Rob Winch
 * @author Keesun Baik
 * @since 3.2
 */
//Spring Web Security 的配置类 : 
//*  1. 使用一个 WebSecurity 对象基于安全配置创建一个 FilterChainProxy 对象来对用户请求进行安全过滤。 
//*  2. 也会暴露一些必要的 bean。
//*  3. 如何定制 Spring security 的web 安全，也就是 WebSecurity 对象 ?
//*     3.1 实现一个继承自 WebSecurityConfigurerAdapter 的配置类 , 
//*     3.2 或者 提供一个配置类，实现了接口 WebSecurityConfigurer
//*    该配置类的配置会在使用 @EnableWebSecurity 时应用到系统。
//————————————————
//版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/andy_zhang2007/article/details/82313144
@Configuration
public class WebSecurityConfiguration implements ImportAware, BeanClassLoaderAware {
	private WebSecurity webSecurity;

	private Boolean debugEnabled;

	private List<SecurityConfigurer<Filter, WebSecurity>> webSecurityConfigurers;

	private ClassLoader beanClassLoader;

	@Autowired(required = false)
	private ObjectPostProcessor<Object> objectObjectPostProcessor;

	@Bean
	public static DelegatingApplicationListener delegatingApplicationListener() {
		return new DelegatingApplicationListener();
	}

	@Bean
	@DependsOn(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)
	public SecurityExpressionHandler<FilterInvocation> webSecurityExpressionHandler() {
		return webSecurity.getExpressionHandler();
	}

	/**
	 * Creates the Spring Security Filter Chain
	 * 
	 * @return the {@link Filter} that represents the security filter chain
	 * @throws Exception
	 */
	@Bean(name = AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)
	public Filter springSecurityFilterChain() throws Exception {
		//查看是否有WebSecurityConfigurer的相关配置
		boolean hasConfigurers = webSecurityConfigurers != null && !webSecurityConfigurers.isEmpty();
	    //T2 如果没有，说明我们没有注入继承WebSecurityConfigurerAdapter 的对象
		if (!hasConfigurers) {
	    //T3 创建默认的配置信息WebSecurityConfigurerAdapter ,保证Spring Security的最基础的功能，如果我们要有自定义的相关，一定要重写配置

			WebSecurityConfigurerAdapter adapter = objectObjectPostProcessor
					.postProcess(new WebSecurityConfigurerAdapter() {
					});
			webSecurity.apply(adapter);
		}
		  // 根据配置 webSecurityConfigurers或者缺省 WebSecurityConfigurerAdapter 构建 
        // Filter FilterChainProxy 并返回之，这是最终加入到Servlet容器的Filter chain
        // 中的一个 Filter, 但实际上，它的内部也维护了一个自己的安全相关的 Filter chain。

		return webSecurity.build();
	}

	/**
	 * Creates the {@link WebInvocationPrivilegeEvaluator} that is necessary for the
	 * JSP tag support.
	 * 
	 * @return the {@link WebInvocationPrivilegeEvaluator}
	 * @throws Exception
	 */
	@Bean
	@DependsOn(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)
	public WebInvocationPrivilegeEvaluator privilegeEvaluator() throws Exception {
		return webSecurity.getPrivilegeEvaluator();
	}

	/**
	 * Sets the {@code <SecurityConfigurer<FilterChainProxy, WebSecurityBuilder>}
	 * instances used to create the web configuration.
	 *
	 * @param objectPostProcessor    the {@link ObjectPostProcessor} used to create
	 *                               a {@link WebSecurity} instance
	 * @param webSecurityConfigurers the
	 *                               {@code <SecurityConfigurer<FilterChainProxy, WebSecurityBuilder>}
	 *                               instances used to create the web configuration
	 * @throws Exception
	 */
	@Autowired(required = false)
	public void setFilterChainProxySecurityConfigurer(ObjectPostProcessor<Object> objectPostProcessor,
			@Value("#{@autowiredWebSecurityConfigurersIgnoreParents.getWebSecurityConfigurers()}") 
			List<SecurityConfigurer<Filter, WebSecurity>> webSecurityConfigurers)
			throws Exception {
		//实例化webSecurity
		webSecurity = objectPostProcessor.postProcess(new WebSecurity(objectPostProcessor));
		if (debugEnabled != null) {
			webSecurity.debug(debugEnabled);
		}

		Collections.sort(webSecurityConfigurers, AnnotationAwareOrderComparator.INSTANCE);

		Integer previousOrder = null;
		Object previousConfig = null;
		for (SecurityConfigurer<Filter, WebSecurity> config : webSecurityConfigurers) {
			Integer order = AnnotationAwareOrderComparator.lookupOrder(config);
			if (previousOrder != null && previousOrder.equals(order)) {
				throw new IllegalStateException("@Order on WebSecurityConfigurers must be unique. Order of " + order
						+ " was already used on " + previousConfig + ", so it cannot be used on " + config + " too.");
			}
			previousOrder = order;
			previousConfig = config;
		}
		//通过spring容器注入的
		for (SecurityConfigurer<Filter, WebSecurity> webSecurityConfigurer : webSecurityConfigurers) {
			webSecurity.apply(webSecurityConfigurer);
		}
		this.webSecurityConfigurers = webSecurityConfigurers;
	}
//	这是一个工具类，从当前bean容器中获取所有的WebSecurityConfigurer bean。
//	这些WebSecurityConfigurer通常是由开发人员实现的配置类，并且继承自WebSecurityConfigurerAdapter。
	@Bean
	public static AutowiredWebSecurityConfigurersIgnoreParents autowiredWebSecurityConfigurersIgnoreParents(
			ConfigurableListableBeanFactory beanFactory) {
		return new AutowiredWebSecurityConfigurersIgnoreParents(beanFactory);
	}

	/**
	 * A custom verision of the Spring provided AnnotationAwareOrderComparator that
	 * uses {@link AnnotationUtils#findAnnotation(Class, Class)} to look on super
	 * class instances for the {@link Order} annotation.
	 *
	 * @author Rob Winch
	 * @since 3.2
	 */
	private static class AnnotationAwareOrderComparator extends OrderComparator {
		private static final AnnotationAwareOrderComparator INSTANCE = new AnnotationAwareOrderComparator();

		@Override
		protected int getOrder(Object obj) {
			return lookupOrder(obj);
		}

		private static int lookupOrder(Object obj) {
			if (obj instanceof Ordered) {
				return ((Ordered) obj).getOrder();
			}
			if (obj != null) {
				Class<?> clazz = (obj instanceof Class ? (Class<?>) obj : obj.getClass());
				Order order = AnnotationUtils.findAnnotation(clazz, Order.class);
				if (order != null) {
					return order.value();
				}
			}
			return Ordered.LOWEST_PRECEDENCE;
		}
	}

	/*
	 * 获取导入该配置bean的配置bean上的注解元数据并设置到该配置bean
     *  这里主要是为了获取注解 @EnableWebSecurity 的属性 debugEnabled
	 *
	 * @see
	 * org.springframework.context.annotation.ImportAware#setImportMetadata(org.
	 * springframework.core.type.AnnotationMetadata)
	 */
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		Map<String, Object> enableWebSecurityAttrMap = importMetadata
				.getAnnotationAttributes(EnableWebSecurity.class.getName());
		AnnotationAttributes enableWebSecurityAttrs = AnnotationAttributes.fromMap(enableWebSecurityAttrMap);
		debugEnabled = enableWebSecurityAttrs.getBoolean("debug");
		if (webSecurity != null) {
			webSecurity.debug(debugEnabled);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(
	 * java. lang.ClassLoader)
	 */
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}
}
