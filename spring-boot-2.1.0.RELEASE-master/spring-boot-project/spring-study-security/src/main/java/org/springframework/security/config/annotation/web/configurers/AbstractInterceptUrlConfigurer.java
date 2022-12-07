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
package org.springframework.security.config.annotation.web.configurers;

import java.util.List;

import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfigurer;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

/**
 * A base class for configuring the {@link FilterSecurityInterceptor}.
 *
 * <h2>Security Filters</h2>
 *
 * The following Filters are populated
 *
 * <ul>
 * <li>{@link FilterSecurityInterceptor}</li>
 * </ul>
 *
 * <h2>Shared Objects Created</h2>
 *
 * The following shared objects are populated to allow other
 * {@link SecurityConfigurer}'s to customize:
 * <ul>
 * <li>{@link FilterSecurityInterceptor}</li>
 * </ul>
 *
 * <h2>Shared Objects Used</h2>
 *
 * The following shared objects are used:
 *
 * <ul>
 * <li>{@link AuthenticationManager}</li>
 * </ul>
 *
 *
 * @param <C> the AbstractInterceptUrlConfigurer
 * @param <H> the type of {@link HttpSecurityBuilder} that is being configured
 *
 * @author Rob Winch
 * @since 3.2
 * @see ExpressionUrlAuthorizationConfigurer
 * @see UrlAuthorizationConfigurer
 */
abstract class AbstractInterceptUrlConfigurer<C extends AbstractInterceptUrlConfigurer<C, H>, H extends HttpSecurityBuilder<H>>
		extends AbstractHttpConfigurer<C, H> {
	private Boolean filterSecurityInterceptorOncePerRequest;

	private AccessDecisionManager accessDecisionManager;

//	对目标安全构建器 http 进行配置，http 实际上可以理解成就是HttpSecurity。
//	主要就是调用当前类所定义的各种方法创建目标FilterSecurityInterceptor并设置到目标安全构建器http。
	@Override
	public void configure(H http) throws Exception {
		FilterInvocationSecurityMetadataSource metadataSource = createMetadataSource(http);
		if (metadataSource == null) {
			return;
		}
		// 创建 FilterSecurityInterceptor， 方法 createFilterSecurityInterceptor 的逻辑由本类提供，
		// 并且被定义为 private，无需由子类覆盖，
		// 这里创建 FilterSecurityInterceptor 使用到了 :
		// 1. 目标安全构建器 http
		// 2. 上面创建的安全元数据对象 metadataSource
		// 3. 保存在目标安全构建器中的 AuthenticationManager 共享对象

		FilterSecurityInterceptor securityInterceptor = createFilterSecurityInterceptor(http, metadataSource,
				http.getSharedObject(AuthenticationManager.class));
		if (filterSecurityInterceptorOncePerRequest != null) {
			securityInterceptor.setObserveOncePerRequest(filterSecurityInterceptorOncePerRequest);
		}
		// 对新建安全拦截过滤器 FilterSecurityInterceptor 的后置处理
		securityInterceptor = postProcess(securityInterceptor);
		// 将新建的安全拦截过滤器 FilterSecurityInterceptor 添加到目标安全构建器 http 的Filter清单，
		// 也添加为目标安全构建器的一个共享对象
		http.addFilter(securityInterceptor);
		http.setSharedObject(FilterSecurityInterceptor.class, securityInterceptor);
	}

	/**
	 * 抽象方法，要求子类提供实现。用于创建安全元数据对象FilterInvocationSecurityMetadataSource,供目标安全拦截过滤器FilterSecurityInterceptor使用。
	 * Subclasses should implement this method to provide a
	 * {@link FilterInvocationSecurityMetadataSource} for the
	 * {@link FilterSecurityInterceptor}.
	 *
	 * @param http the builder to use
	 *
	 * @return the {@link FilterInvocationSecurityMetadataSource} to set on the
	 *         {@link FilterSecurityInterceptor}. Cannot be null.
	 */
	abstract FilterInvocationSecurityMetadataSource createMetadataSource(H http);

	/**
	 * 抽象方法，要求子类提供实现。创建缺省AccessDecisionManager的方法
	 * createDefaultAccessDecisionManager会用到这个方法提供的一组AccessDecisionVoter。 Subclasses
	 * should implement this method to provide the {@link AccessDecisionVoter}
	 * instances used to create the default {@link AccessDecisionManager}
	 *
	 * @param http the builder to use
	 *
	 * @return the {@link AccessDecisionVoter} instances used to create the default
	 *         {@link AccessDecisionManager}
	 */
	abstract List<AccessDecisionVoter<? extends Object>> getDecisionVoters(H http);

//	该类继承自AbstractConfigAttributeRequestMatcherRegistry,表示一个URL pattern到所需的权限的映射表。这里每个映射项内部通过AbstractConfigAttributeRequestMatcherRegistry.UrlMapping来表示，其中URL pattern通过RequestMatcher来表示，所需要的权限通过ConfigAttribute集合来表示。该映射表是构建目标FilterSecurityInterceptor所要使用的安全元数据源SecurityMetadataSource对象的来源。
//	AbstractInterceptUrlRegistry也必须要由AbstractInterceptUrlConfigurer的具体实现子类提供自己的具体实现类
//	————————————————
//	版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/andy_zhang2007/article/details/93350686
	abstract class AbstractInterceptUrlRegistry<R extends AbstractInterceptUrlRegistry<R, T>, T>
			extends AbstractConfigAttributeRequestMatcherRegistry<T> {

		/**
		 * Allows setting the {@link AccessDecisionManager}. If none is provided, a
		 * default {@link AccessDecisionManager} is created.
		 *
		 * @param accessDecisionManager the {@link AccessDecisionManager} to use
		 * @return the {@link AbstractInterceptUrlConfigurer} for further customization
		 */
		public R accessDecisionManager(AccessDecisionManager accessDecisionManager) {
			AbstractInterceptUrlConfigurer.this.accessDecisionManager = accessDecisionManager;
			return getSelf();
		}

		/**
		 * Allows setting if the {@link FilterSecurityInterceptor} should be only
		 * applied once per request (i.e. if the filter intercepts on a forward, should
		 * it be applied again).
		 *
		 * @param filterSecurityInterceptorOncePerRequest if the
		 *                                                {@link FilterSecurityInterceptor}
		 *                                                should be only applied once
		 *                                                per request
		 * @return the {@link AbstractInterceptUrlConfigurer} for further customization
		 */
		public R filterSecurityInterceptorOncePerRequest(boolean filterSecurityInterceptorOncePerRequest) {
			AbstractInterceptUrlConfigurer.this.filterSecurityInterceptorOncePerRequest = filterSecurityInterceptorOncePerRequest;
			return getSelf();
		}

		/**
		 * Returns a reference to the current object with a single suppression of the
		 * type
		 *
		 * @return a reference to the current object
		 */
		@SuppressWarnings("unchecked")
		private R getSelf() {
			return (R) this;
		}
	}

	/**
	 * 创建目标FilterSecurityInterceptor用到的AccessDecisionManager可以由调用者设置，
	 * 如果不设置，则使用该方法创建一个缺省的AccessDecisionManager,该缺省的AccessDecisionManager实现类使用AffirmativeBased,一票赞成即可通过。
	 * ———————————————— 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0
	 * BY-SA版权协议，转载请附上原文出处链接及本声明。
	 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/93350686
	 * 
	 * Creates the default {@code AccessDecisionManager}
	 * 
	 * @return the default {@code AccessDecisionManager}
	 */
	private AccessDecisionManager createDefaultAccessDecisionManager(H http) {
		// 是一个 AffirmativeBased， 也就是只要有一票赞成即通过
		AffirmativeBased result = new AffirmativeBased(getDecisionVoters(http));
		return postProcess(result);
	}

	/**
	 * 该方法被configure用于获取最终用于目标FilterSecurityInterceptor要使用的AccessDecisionManager。
	 * 如果调用者设置了当前安全配置器的AccessDecisionManager属性则使用它，
	 * 否则使用createDefaultAccessDecisionManager创建一个缺省的AccessDecisionManager。
	 * 
	 * If currently null, creates a default {@link AccessDecisionManager} using
	 * {@link #createDefaultAccessDecisionManager(HttpSecurityBuilder)}. Otherwise
	 * returns the {@link AccessDecisionManager}.
	 *
	 * @param http the builder to use
	 *
	 * @return the {@link AccessDecisionManager} to use
	 */
	private AccessDecisionManager getAccessDecisionManager(H http) {
		 // 获取配置过程最终要使用的 AccessDecisionManager， 该 AccessDecisionManager
	       // 可以由调用者指定，如果不指定，则使用缺省值，一个 AffirmativeBased:一票赞成即通过
		if (accessDecisionManager == null) {
			accessDecisionManager = createDefaultAccessDecisionManager(http);
		}
		return accessDecisionManager;
	}

	/**
	 * 创建createFilterSecurityInterceptor的具体过程方法。configure正式使用该方法创建FilterSecurityInterceptor，然后设置到目标安全构建器http。
	 * Creates the {@link FilterSecurityInterceptor}
	 *
	 * @param http                  the builder to use
	 * @param metadataSource        the
	 *                              {@link FilterInvocationSecurityMetadataSource}
	 *                              to use
	 * @param authenticationManager the {@link AuthenticationManager} to use
	 * @return the {@link FilterSecurityInterceptor}
	 * @throws Exception
	 */
	private FilterSecurityInterceptor createFilterSecurityInterceptor(H http,
			FilterInvocationSecurityMetadataSource metadataSource, AuthenticationManager authenticationManager)
			throws Exception {
		 // 创建最终要配置到安全构建器 http 的安全拦截过滤器 FilterSecurityInterceptor，
	       // 所需要的各种属性都由该类定义的方法提供，这些方法有的由本类提供实现，有的需要由
	       // 实现子类提供实现。
		FilterSecurityInterceptor securityInterceptor = new FilterSecurityInterceptor();
		securityInterceptor.setSecurityMetadataSource(metadataSource);
		securityInterceptor.setAccessDecisionManager(getAccessDecisionManager(http));
		securityInterceptor.setAuthenticationManager(authenticationManager);
		securityInterceptor.afterPropertiesSet();
		return securityInterceptor;
	}
}