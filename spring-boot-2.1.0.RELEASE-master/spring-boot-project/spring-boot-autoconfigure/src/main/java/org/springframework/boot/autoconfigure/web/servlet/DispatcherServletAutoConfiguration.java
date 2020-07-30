/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.autoconfigure.web.servlet;

import java.util.Arrays;
import java.util.List;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.http.HttpProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for the Spring
 * {@link DispatcherServlet}. Should work for a standalone application where an
 * embedded web server is already present and also for a deployable application
 * using {@link SpringBootServletInitializer}.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Stephane Nicoll
 * @author Brian Clozel
 */
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@AutoConfigureAfter(ServletWebServerFactoryAutoConfiguration.class)
public class DispatcherServletAutoConfiguration {

	/*
	 * The bean name for a DispatcherServlet that will be mapped to the root URL "/"
	 */
	public static final String DEFAULT_DISPATCHER_SERVLET_BEAN_NAME = "dispatcherServlet";

	/*
	 * The bean name for a ServletRegistrationBean for the DispatcherServlet "/"
	 */
	public static final String DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME = "dispatcherServletRegistration";

	// 确保前缀为 spring.http 的配置参数被加载到 bean HttpProperties
	// 确保前缀为 spring.mvc 的配置参数被加载到 bean WebMvcProperties
	@Configuration
	@Conditional(DefaultDispatcherServletCondition.class)
	@ConditionalOnClass(ServletRegistration.class)
	@EnableConfigurationProperties({ HttpProperties.class, WebMvcProperties.class })
	protected static class DispatcherServletConfiguration {

		private final HttpProperties httpProperties;

		private final WebMvcProperties webMvcProperties;

		public DispatcherServletConfiguration(HttpProperties httpProperties, WebMvcProperties webMvcProperties) {
			this.httpProperties = httpProperties;
			this.webMvcProperties = webMvcProperties;
		}

		// 定义 bean DispatcherServlet, 使用名称 dispatcherServlet， 这是 Spring MVC 核心的
		// 前置控制器Servlet，Spring MVC 定义的所有的控制器方法都最终经由该 DispatcherServlet 派发
		@Bean(name = DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)
		public DispatcherServlet dispatcherServlet() {
			DispatcherServlet dispatcherServlet = new DispatcherServlet();
			dispatcherServlet.setDispatchOptionsRequest(this.webMvcProperties.isDispatchOptionsRequest());
			dispatcherServlet.setDispatchTraceRequest(this.webMvcProperties.isDispatchTraceRequest());
			dispatcherServlet
					.setThrowExceptionIfNoHandlerFound(this.webMvcProperties.isThrowExceptionIfNoHandlerFound());
			dispatcherServlet.setEnableLoggingRequestDetails(this.httpProperties.isLogRequestDetails());
			return dispatcherServlet;
		}

		// 定义 bean MultipartResolver，使用名称 multipartResolver,
		// 该 bean 定义的主要任务不是生成一个新的 MultipartResolver bean 实例，而是在该类型的
		// bean 存在时，给它起一个别名 multipartResolver，因为 DispatcherServlet 内部会固定使用该名字
		// 仅在类型为 MultipartResolver 的 bean 存在时才生效
		// 仅在名称为 multipartResolver 的 bean 不存在时才生效
		@Bean
		@ConditionalOnBean(MultipartResolver.class)
		@ConditionalOnMissingBean(name = DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
		public MultipartResolver multipartResolver(MultipartResolver resolver) {
			// Detect if the user has created a MultipartResolver but named it incorrectly
			return resolver;
		}

	}

	@Configuration
	@Conditional(DispatcherServletRegistrationCondition.class)
	@ConditionalOnClass(ServletRegistration.class)
	@EnableConfigurationProperties(WebMvcProperties.class)
	@Import(DispatcherServletConfiguration.class)
	protected static class DispatcherServletRegistrationConfiguration {

		private final WebMvcProperties webMvcProperties;

		private final MultipartConfigElement multipartConfig;

		// MultipartConfigElement 的配置可以参考 MultipartAutoConfiguration
		public DispatcherServletRegistrationConfiguration(WebMvcProperties webMvcProperties,
				ObjectProvider<MultipartConfigElement> multipartConfigProvider) {
			this.webMvcProperties = webMvcProperties;
			this.multipartConfig = multipartConfigProvider.getIfAvailable();
		}
		// 定义另外一个bean ServletRegistrationBean,名字为dispatcherServletRegistration,
		// 这个ServletRegistrationBean有点特殊,它不是个普通意义上的Bean,它实现了Spring SCI
		// 接口,而对于此类实现了Spring SCI接口的bean定义,在内置的Tomcat servlet容器启动阶段,
		// 严格地讲，是其中相当于web app的StarndartContext的启动阶段，会被逐个实例化并调用其
		// onStartup()方法

		// 定义 bean DispatcherServletRegistrationBean，名称为 dispatcherServletRegistration
		// DispatcherServletRegistrationBean 继承自 ServletRegistrationBean,
		// 该 bean dispatcherServletRegistration 的目的是将 DispatcherServletConfiguration
		// 配置类中
		// 所定义的 bean DispatcherServlet dispatcherServlet 注册到 Servlet 容器
		@Bean(name = DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME)
		// 仅在类型为 DispatcherServlet，名称为 dispatcherServlet 的 bean 存在时才生效
		@ConditionalOnBean(value = DispatcherServlet.class, name = DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)
		public DispatcherServletRegistrationBean dispatcherServletRegistration(DispatcherServlet dispatcherServlet) {
			DispatcherServletRegistrationBean registration = new DispatcherServletRegistrationBean(dispatcherServlet,
					this.webMvcProperties.getServlet().getPath());
			registration.setName(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME);
			registration.setLoadOnStartup(this.webMvcProperties.getServlet().getLoadOnStartup());
			if (this.multipartConfig != null) {
				registration.setMultipartConfig(this.multipartConfig);
			}
			return registration;
		}

	}

	@Order(Ordered.LOWEST_PRECEDENCE - 10)
	private static class DefaultDispatcherServletCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			ConditionMessage.Builder message = ConditionMessage.forCondition("Default DispatcherServlet");
			ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
			List<String> dispatchServletBeans = Arrays
					.asList(beanFactory.getBeanNamesForType(DispatcherServlet.class, false, false));
			if (dispatchServletBeans.contains(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)) {
				return ConditionOutcome
						.noMatch(message.found("dispatcher servlet bean").items(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME));
			}
			if (beanFactory.containsBean(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)) {
				return ConditionOutcome.noMatch(
						message.found("non dispatcher servlet bean").items(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME));
			}
			if (dispatchServletBeans.isEmpty()) {
				return ConditionOutcome.match(message.didNotFind("dispatcher servlet beans").atAll());
			}
			return ConditionOutcome.match(message.found("dispatcher servlet bean", "dispatcher servlet beans")
					.items(Style.QUOTE, dispatchServletBeans)
					.append("and none is named " + DEFAULT_DISPATCHER_SERVLET_BEAN_NAME));
		}

	}

	@Order(Ordered.LOWEST_PRECEDENCE - 10)
	private static class DispatcherServletRegistrationCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
			ConditionOutcome outcome = checkDefaultDispatcherName(beanFactory);
			if (!outcome.isMatch()) {
				return outcome;
			}
			return checkServletRegistration(beanFactory);
		}

		private ConditionOutcome checkDefaultDispatcherName(ConfigurableListableBeanFactory beanFactory) {
			List<String> servlets = Arrays
					.asList(beanFactory.getBeanNamesForType(DispatcherServlet.class, false, false));
			boolean containsDispatcherBean = beanFactory.containsBean(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME);
			if (containsDispatcherBean && !servlets.contains(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)) {
				return ConditionOutcome.noMatch(
						startMessage().found("non dispatcher servlet").items(DEFAULT_DISPATCHER_SERVLET_BEAN_NAME));
			}
			return ConditionOutcome.match();
		}

		private ConditionOutcome checkServletRegistration(ConfigurableListableBeanFactory beanFactory) {
			ConditionMessage.Builder message = startMessage();
			List<String> registrations = Arrays
					.asList(beanFactory.getBeanNamesForType(ServletRegistrationBean.class, false, false));
			boolean containsDispatcherRegistrationBean = beanFactory
					.containsBean(DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME);
			if (registrations.isEmpty()) {
				if (containsDispatcherRegistrationBean) {
					return ConditionOutcome.noMatch(message.found("non servlet registration bean")
							.items(DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME));
				}
				return ConditionOutcome.match(message.didNotFind("servlet registration bean").atAll());
			}
			if (registrations.contains(DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME)) {
				return ConditionOutcome.noMatch(message.found("servlet registration bean")
						.items(DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME));
			}
			if (containsDispatcherRegistrationBean) {
				return ConditionOutcome.noMatch(message.found("non servlet registration bean")
						.items(DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME));
			}
			return ConditionOutcome.match(message.found("servlet registration beans").items(Style.QUOTE, registrations)
					.append("and none is named " + DEFAULT_DISPATCHER_SERVLET_REGISTRATION_BEAN_NAME));
		}

		private ConditionMessage.Builder startMessage() {
			return ConditionMessage.forCondition("DispatcherServlet Registration");
		}

	}

}
