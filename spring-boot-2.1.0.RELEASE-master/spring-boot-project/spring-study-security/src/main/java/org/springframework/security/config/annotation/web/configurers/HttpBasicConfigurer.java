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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;

/**
 * Adds HTTP basic based authentication. All attributes have reasonable defaults
 * making all parameters are optional.
 *
 * <h2>Security Filters</h2>
 *
 * The following Filters are populated 介绍
 * 作为一个配置HttpSecurity的SecurityConfigurer,HttpBasicConfigurer的配置任务如下 :
 * 
 * 配置如下安全过滤器Filter
 * 
 * BasicAuthenticationFilter 创建的共享对象
 * 
 * AuthenticationEntryPoint 缺省是 BasicAuthenticationEntryPoint
 * 
 * 在配置过程中，HttpBasicConfigurer会使用到如下共享对象 :
 * 
 * AuthenticationManager RememberMeServices ContentNegotiationStrategy
 * 也用到了其他安全配置器:
 * 
 * ExceptionHandlingConfigurer 用于注册自己定义的认证入口点
 * 
 * LogoutConfigurer 用于注册一个退出成功处理器(总是返回状态字204) ————————————————
 * 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/91355905
 * <ul>
 * <li>{@link BasicAuthenticationFilter}</li>
 * </ul>
 *
 * <h2>Shared Objects Created</h2>
 *
 * <ul>
 * <li>AuthenticationEntryPoint - populated with the
 * {@link #authenticationEntryPoint(AuthenticationEntryPoint)} (default
 * {@link BasicAuthenticationEntryPoint})</li>
 * </ul>
 *
 * <h2>Shared Objects Used</h2>
 *
 * The following shared objects are used:
 *
 * <ul>
 * <li>{@link AuthenticationManager}</li>
 * <li>{@link RememberMeServices}</li>
 * </ul>
 *
 * @author Rob Winch
 * @since 3.2
 */
public final class HttpBasicConfigurer<B extends HttpSecurityBuilder<B>>
		extends AbstractHttpConfigurer<HttpBasicConfigurer<B>, B> {
	// 用于匹配 ajax 请求的请求匹配器
	private static final RequestHeaderRequestMatcher X_REQUESTED_WITH = new RequestHeaderRequestMatcher(
			"X-Requested-With", "XMLHttpRequest");

	private static final String DEFAULT_REALM = "Realm";

	private AuthenticationEntryPoint authenticationEntryPoint;
	private AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource;
	private BasicAuthenticationEntryPoint basicAuthEntryPoint = new BasicAuthenticationEntryPoint();

	/**
	 * Creates a new instance
	 * 
	 * @throws Exception
	 * @see HttpSecurity#httpBasic()
	 */
	public HttpBasicConfigurer() throws Exception {
		realmName(DEFAULT_REALM);

		LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
		entryPoints.put(X_REQUESTED_WITH, new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));

		DelegatingAuthenticationEntryPoint defaultEntryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
		defaultEntryPoint.setDefaultEntryPoint(this.basicAuthEntryPoint);
		this.authenticationEntryPoint = defaultEntryPoint;
	}

	/**
	 * Allows easily changing the realm, but leaving the remaining defaults in
	 * place. If {@link #authenticationEntryPoint(AuthenticationEntryPoint)} has
	 * been invoked, invoking this method will result in an error.
	 *
	 * @param realmName the HTTP Basic realm to use
	 * @return {@link HttpBasicConfigurer} for additional customization
	 * @throws Exception
	 */
	public HttpBasicConfigurer<B> realmName(String realmName) throws Exception {
		this.basicAuthEntryPoint.setRealmName(realmName);
		this.basicAuthEntryPoint.afterPropertiesSet();
		return this;
	}

	/**
	 * The {@link AuthenticationEntryPoint} to be populated on
	 * {@link BasicAuthenticationFilter} in the event that authentication fails. The
	 * default to use {@link BasicAuthenticationEntryPoint} with the realm "Realm".
	 *
	 * @param authenticationEntryPoint the {@link AuthenticationEntryPoint} to use
	 * @return {@link HttpBasicConfigurer} for additional customization
	 */
	public HttpBasicConfigurer<B> authenticationEntryPoint(AuthenticationEntryPoint authenticationEntryPoint) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		return this;
	}

	/**
	 * Specifies a custom {@link AuthenticationDetailsSource} to use for basic
	 * authentication. The default is {@link WebAuthenticationDetailsSource}.
	 *
	 * @param authenticationDetailsSource the custom
	 *                                    {@link AuthenticationDetailsSource} to use
	 * @return {@link HttpBasicConfigurer} for additional customization
	 */
	public HttpBasicConfigurer<B> authenticationDetailsSource(
			AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource) {
		this.authenticationDetailsSource = authenticationDetailsSource;
		return this;
	}

	// 初始化方法
	@Override
	public void init(B http) throws Exception {
		registerDefaults(http);
	}

	private void registerDefaults(B http) {
		// 获取共享对象 ContentNegotiationStrategy 或者使用缺省的 HeaderContentNegotiationStrategy
		// 用于构建请求匹配器
		ContentNegotiationStrategy contentNegotiationStrategy = http.getSharedObject(ContentNegotiationStrategy.class);
		if (contentNegotiationStrategy == null) {
			contentNegotiationStrategy = new HeaderContentNegotiationStrategy();
		}
		// 构建 REST请求的 请求匹配器
		MediaTypeRequestMatcher restMatcher = new MediaTypeRequestMatcher(contentNegotiationStrategy,
				MediaType.APPLICATION_ATOM_XML, MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON,
				MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_XML, MediaType.MULTIPART_FORM_DATA,
				MediaType.TEXT_XML);
		restMatcher.setIgnoredMediaTypes(Collections.singleton(MediaType.ALL));
		// 构建 针对所有请求的 请求匹配器
		MediaTypeRequestMatcher allMatcher = new MediaTypeRequestMatcher(contentNegotiationStrategy, MediaType.ALL);
		allMatcher.setUseEquals(true);
		// 构建针对非HTML媒体类型的 请求匹配器
		RequestMatcher notHtmlMatcher = new NegatedRequestMatcher(
				new MediaTypeRequestMatcher(contentNegotiationStrategy, MediaType.TEXT_HTML));
		// 构建针对非HTML媒体类型的 但又是 REST 请求的 请求匹配器
		// 注意，这里其实是对上面两个 请求匹配器的 AND 操作 : restMatcher , notHtmlMatcher

		RequestMatcher restNotHtmlMatcher = new AndRequestMatcher(
				Arrays.<RequestMatcher>asList(notHtmlMatcher, restMatcher));
		// 最终要使用的请求匹配器 : X_REQUESTED_WITH OR restNotHtmlMatcher OR allMatcher
		RequestMatcher preferredMatcher = new OrRequestMatcher(
				Arrays.asList(X_REQUESTED_WITH, restNotHtmlMatcher, allMatcher));

		registerDefaultEntryPoint(http, preferredMatcher);
		registerDefaultLogoutSuccessHandler(http, preferredMatcher);
	}
	// 向 ExceptionHandlingConfigurer 登记注册该 HttpBasicConfigurer 的认证入口点
	// authenticationEntryPoint 到相应的请求匹配器 preferredMatcher

	private void registerDefaultEntryPoint(B http, RequestMatcher preferredMatcher) {
		ExceptionHandlingConfigurer<B> exceptionHandling = http.getConfigurer(ExceptionHandlingConfigurer.class);
		if (exceptionHandling == null) {
			return;
		}
		exceptionHandling.defaultAuthenticationEntryPointFor(postProcess(this.authenticationEntryPoint),
				preferredMatcher);
	}
	// 向 LogoutConfigurer 登记注册一个 HttpStatusReturningLogoutSuccessHandler
	// 到相应的请求匹配器 preferredMatcher，退出成功时总是返回状态码 204

	private void registerDefaultLogoutSuccessHandler(B http, RequestMatcher preferredMatcher) {
		LogoutConfigurer<B> logout = http.getConfigurer(LogoutConfigurer.class);
		if (logout == null) {
			return;
		}
		LogoutConfigurer<B> handler = logout.defaultLogoutSuccessHandlerFor(
				postProcess(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)), preferredMatcher);
	}

	@Override
	public void configure(B http) throws Exception {
		// 基于共享对象 authenticationManager ， rememberMeServices 和
		// 属性 authenticationEntryPoint 构造 BasicAuthenticationFilter
		// basicAuthenticationFilter, 这是最终要增加到 HttpSecurity 安全构建器 http 上的过滤器

		AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
		BasicAuthenticationFilter basicAuthenticationFilter = new BasicAuthenticationFilter(authenticationManager,
				this.authenticationEntryPoint);
		if (this.authenticationDetailsSource != null) {
			basicAuthenticationFilter.setAuthenticationDetailsSource(this.authenticationDetailsSource);
		}
		RememberMeServices rememberMeServices = http.getSharedObject(RememberMeServices.class);
		if (rememberMeServices != null) {
			basicAuthenticationFilter.setRememberMeServices(rememberMeServices);
		}
		basicAuthenticationFilter = postProcess(basicAuthenticationFilter);
		http.addFilter(basicAuthenticationFilter);
	}
}
