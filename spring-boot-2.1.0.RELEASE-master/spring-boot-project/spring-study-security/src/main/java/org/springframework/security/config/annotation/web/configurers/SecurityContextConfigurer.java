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
package org.springframework.security.config.annotation.web.configurers;

import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 配置如下安全过滤器Filter SecurityContextPersistenceFilter
 * 如果存在共享对象SecurityContextRepository，则使用它作为安全上下文存储库，否则创建一个实现类型为HttpSessionSecurityContextRepository的存储库并使用
 * 如果配置器SessionManagementConfigurer中配置的会话创建策略SessionCreationPolicy为ALWAYS的话,则将过滤器属性forceEagerSessionCreation设置为true
 * ———————————————— 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0
 * BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/92388255 Allows
 * persisting and restoring of the {@link SecurityContext} found on the
 * {@link SecurityContextHolder} for each request by configuring the
 * {@link SecurityContextPersistenceFilter}. All properties have reasonable
 * defaults, so no additional configuration is required other than applying this
 * {@link org.springframework.security.config.annotation.SecurityConfigurer}.
 *
 * <h2>Security Filters</h2>
 *
 * The following Filters are populated
 *
 * <ul>
 * <li>{@link SecurityContextPersistenceFilter}</li>
 * </ul>
 *
 * <h2>Shared Objects Created</h2>
 *
 * No shared objects are created.
 *
 * <h2>Shared Objects Used</h2>
 *
 * The following shared objects are used:
 *
 * <ul>
 * <li>If {@link SessionManagementConfigurer}, is provided and set to always,
 * then the
 * {@link SecurityContextPersistenceFilter#setForceEagerSessionCreation(boolean)}
 * will be set to true.</li>
 * <li>{@link SecurityContextRepository} must be set and is used on
 * {@link SecurityContextPersistenceFilter}.</li>
 * </ul>
 *
 * @author Rob Winch
 * @since 3.2
 */
public final class SecurityContextConfigurer<H extends HttpSecurityBuilder<H>>
		extends AbstractHttpConfigurer<SecurityContextConfigurer<H>, H> {

	/**
	 * Creates a new instance
	 * 
	 * @see HttpSecurity#securityContext()
	 */
	public SecurityContextConfigurer() {
	}

	/**
	 * Specifies the shared {@link SecurityContextRepository} that is to be used
	 * 
	 * @param securityContextRepository the {@link SecurityContextRepository} to use
	 * @return the {@link HttpSecurity} for further customizations
	 */
	public SecurityContextConfigurer<H> securityContextRepository(SecurityContextRepository securityContextRepository) {
		getBuilder().setSharedObject(SecurityContextRepository.class, securityContextRepository);
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void configure(H http) throws Exception {
		// 准备创建 SecurityContextPersistenceFilter 过滤器所需要使用的
		// 安全上下文仓库对象 SecurityContextRepository
		// 首相从构建器 http 中尝试获取 SecurityContextRepository 共享对象，
		// 如果没有找到，则创建一个 SecurityContextRepository， 实现类使用
		// HttpSessionSecurityContextRepository

		SecurityContextRepository securityContextRepository = http.getSharedObject(SecurityContextRepository.class);
		if (securityContextRepository == null) {
			securityContextRepository = new HttpSessionSecurityContextRepository();
		}
		SecurityContextPersistenceFilter securityContextFilter = new SecurityContextPersistenceFilter(
				securityContextRepository);
		SessionManagementConfigurer<?> sessionManagement = http.getConfigurer(SessionManagementConfigurer.class);
		SessionCreationPolicy sessionCreationPolicy = sessionManagement == null ? null
				: sessionManagement.getSessionCreationPolicy();
		if (SessionCreationPolicy.ALWAYS == sessionCreationPolicy) {
			securityContextFilter.setForceEagerSessionCreation(true);
		}
		securityContextFilter = postProcess(securityContextFilter);
		http.addFilter(securityContextFilter);
	}
}
