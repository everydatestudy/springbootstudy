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

package org.springframework.web.servlet.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * 最后我们总结下@EnableWebMvc这个注解够干了什么。
 * 
 * 初始化RequestMappingHandlerMapping
 * 
 * 初始化视图路径匹配器PathMatcher
 * 
 * 初始化url路径匹配器UrlPathHelper
 * 
 * 初始化媒体类型转换器ContentNegotiationManager
 * 
 * 初始化viewControllerHandlerMapping
 * 
 * 初始化BeanNameUrlHandlerMapping
 * 
 * 初始化resourceHandlerMapping
 * 
 * 初始化ResourceUrlProvider
 * 
 * 初始化defaultServletHandlerMapping
 * 
 * 初始化RequestMappingHandlerAdapter
 * 
 * 初始化默认请求参数解析器
 * 
 * 初始化默认绑定参数解析器
 * 
 * 初始化默认返回值参数解析器
 * 
 * 初始化FormattingConversionService
 * 
 * 初始化Validator
 * 
 * 初始化CompositeUriComponentsContributor
 * 
 * 初始化HttpRequestHandlerAdapter
 * 
 * 初始化SimpleControllerHandlerAdapter
 * 
 * 初始化HandlerExceptionResolver
 * 
 * 初始化ViewResolver
 * 
 * 初始化HandlerMappingIntrospector
 * 
 * Adding this annotation to an {@code @Configuration} class imports the Spring
 * MVC configuration from {@link WebMvcConfigurationSupport}, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableWebMvc
 * &#064;ComponentScan(basePackageClasses = MyConfiguration.class)
 * public class MyConfiguration {
 *
 * }
 * </pre>
 *
 * <p>
 * To customize the imported configuration, implement the interface
 * {@link WebMvcConfigurer} and override individual methods, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableWebMvc
 * &#064;ComponentScan(basePackageClasses = MyConfiguration.class)
 * public class MyConfiguration implements WebMvcConfigurer {
 *
 * 	&#064;Override
 * 	public void addFormatters(FormatterRegistry formatterRegistry) {
 * 		formatterRegistry.addConverter(new MyConverter());
 * 	}
 *
 * 	&#064;Override
 * 	public void configureMessageConverters(List&lt;HttpMessageConverter&lt;?&gt;&gt; converters) {
 * 		converters.add(new MyHttpMessageConverter());
 * 	}
 *
 * }
 * </pre>
 *
 * <p>
 * <strong>Note:</strong> only one {@code @Configuration} class may have the
 * {@code @EnableWebMvc} annotation to imports the Spring Web MVC configuration.
 * There can however be multiple {@code @Configuration} classes implementing
 * {@code WebMvcConfigurer} in order to customize the provided configuration.
 *
 * <p>
 * If {@link WebMvcConfigurer} does not expose some more advanced setting that
 * needs to be configured consider removing the {@code @EnableWebMvc} annotation
 * and extending directly from {@link WebMvcConfigurationSupport} or
 * {@link DelegatingWebMvcConfiguration}, e.g.:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;ComponentScan(basePackageClasses = { MyConfiguration.class })
 * public class MyConfiguration extends WebMvcConfigurationSupport {
 *
 * 	&#064;Override
 * 	public void addFormatters(FormatterRegistry formatterRegistry) {
 * 		formatterRegistry.addConverter(new MyConverter());
 * 	}
 *
 * 	&#064;Bean
 * 	public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
 * 		// Create or delegate to "super" to create and
 * 		// customize properties of RequestMappingHandlerAdapter
 * 	}
 * }
 * </pre>
 *
 * @author Dave Syer
 * @author Rossen Stoyanchev
 * @since 3.1
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurer
 * @see org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport
 * @see org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(DelegatingWebMvcConfiguration.class)
public @interface EnableWebMvc {
}
