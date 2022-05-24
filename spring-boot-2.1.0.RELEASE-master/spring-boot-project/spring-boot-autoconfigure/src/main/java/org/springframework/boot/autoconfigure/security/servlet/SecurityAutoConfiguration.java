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

package org.springframework.boot.autoconfigure.security.servlet;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.SecurityDataConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;

/**
 * 如果用户没有提供自定义的WebSecurityConfigurerAdapter,则提供一个缺省的WebSecurityConfigurerAdapter用于配置Sping
 * Security Web安全。 {@link EnableAutoConfiguration Auto-configuration} for Spring
 * Security.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Madhura Bhave
 */
//SpringBootWebSecurityConfiguration
@Configuration
@ConditionalOnClass(DefaultAuthenticationEventPublisher.class)
@EnableConfigurationProperties(SecurityProperties.class)
@Import({
		// 如果用户没有提供自定义的WebSecurityConfigurerAdapter,则提供一个缺省的WebSecurityConfigurerAdapter用于配置Sping
		// Security Web安全。
		SpringBootWebSecurityConfiguration.class,
		// 在Servlet环境中，bean WebSecurityConfigurerAdapter 存在,
		// 并且名为springSecurityFilterChain的bean不存在的情况下,使用注解@EnableWebSecurity。
		// 该配置文件的作用是万一用户忘记了使用注解@EnableWebSecurity，这里保证该注解被使用从而保障springSecurityFilterChain
		// bean的定义。这里WebMvcSecurityConfiguration这个也使用@EnableWebSecurity这个
		WebSecurityEnablerConfiguration.class,
		// 配置Spring Security跟Spring Data的整合，仅在Spring Security Data被使用时才启用。
		// 具体来讲，是在SecurityEvaluationContextExtension类存在于classpath(Spring Security
		// Data包的一个类)
		// 上并且容器中不存在该类型的bean时，向容器注册一个这样的bean。
		SecurityDataConfiguration.class

})
public class SecurityAutoConfiguration {
	// 定义一个认证事件发布器bean，仅在类型为 AuthenticationEventPublisher 的bean不
	// 存在于容器上时才生效
	@Bean
	@ConditionalOnMissingBean(AuthenticationEventPublisher.class)
	public DefaultAuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
		// 所定义的认证事件发布器bean类型使用 DefaultAuthenticationEventPublisher
		return new DefaultAuthenticationEventPublisher(publisher);
	}

}
