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

import java.util.LinkedHashMap;

import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 这里的配置主要是为了统一处理Spring
 * Security的异常，其主要处理Security的两大类异常，分别是AuthenticationException与AccessDeniedException。
 * 
 * 作者：一根线条 链接：https://www.jianshu.com/p/4065e067eb88 来源：简书
 * 著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。 Adds exception handling for Spring
 * Security related exceptions to an application. All properties have reasonable
 * defaults, so no additional configuration is required other than applying this
 * {@link org.springframework.security.config.annotation.SecurityConfigurer}.
 *
 * <h2>Security Filters</h2>
 *
 * The following Filters are populated
 *
 * <ul>
 * <li>{@link ExceptionTranslationFilter}</li>
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
 * <li>If no explicit {@link RequestCache}, is provided a {@link RequestCache}
 * shared object is used to replay the request after authentication is
 * successful</li>
 * <li>{@link AuthenticationEntryPoint} - see
 * {@link #authenticationEntryPoint(AuthenticationEntryPoint)}</li>
 * </ul>
 * AuthenticationEntryPoint 说明 验证过滤器 LoginUrlAuthenticationEntryPoint
 * 可以对请求进行重定向，例如将http转换成https请求；将请求重定向到配置的登录界面（可以服务器端或客户端重定向）；
 * UsernamePasswordAuthenticationFilter DigestAuthenticationEntryPoint
 * 摘要式身份验证入口点，添加响应头“WWW-Authenticate”并响应401【未经授权】 DigestAuthenticationFilter
 * BasicAuthenticationEntryPoint 添加响应头“WWW-Authenticate”并响应401【未经授权】
 * BasicAuthenticationFilter
 * 
 * 作者：一根线条 链接：https://www.jianshu.com/p/4065e067eb88 来源：简书
 * 著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
 * 
 * @author Rob Winch
 * @since 3.2
 */
public final class ExceptionHandlingConfigurer<H extends HttpSecurityBuilder<H>>
		extends AbstractHttpConfigurer<ExceptionHandlingConfigurer<H>, H> {

	private AuthenticationEntryPoint authenticationEntryPoint;

	private AccessDeniedHandler accessDeniedHandler;

	private LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> defaultEntryPointMappings = new LinkedHashMap<>();

	private LinkedHashMap<RequestMatcher, AccessDeniedHandler> defaultDeniedHandlerMappings = new LinkedHashMap<>();

	/**
	 * Creates a new instance
	 * 
	 * @see HttpSecurity#exceptionHandling()
	 */
	public ExceptionHandlingConfigurer() {
	}

	/**
	 * Shortcut to specify the {@link AccessDeniedHandler} to be used is a specific
	 * error page
	 *
	 * @param accessDeniedUrl the URL to the access denied page (i.e. /errors/401)
	 * @return the {@link ExceptionHandlingConfigurer} for further customization
	 * @see AccessDeniedHandlerImpl
	 * @see #accessDeniedHandler(org.springframework.security.web.access.AccessDeniedHandler)
	 */
	public ExceptionHandlingConfigurer<H> accessDeniedPage(String accessDeniedUrl) {
		AccessDeniedHandlerImpl accessDeniedHandler = new AccessDeniedHandlerImpl();
		accessDeniedHandler.setErrorPage(accessDeniedUrl);
		return accessDeniedHandler(accessDeniedHandler);
	}

	/**
	 * Specifies the {@link AccessDeniedHandler} to be used
	 *
	 * @param accessDeniedHandler the {@link AccessDeniedHandler} to be used
	 * @return the {@link ExceptionHandlingConfigurer} for further customization
	 */
	public ExceptionHandlingConfigurer<H> accessDeniedHandler(AccessDeniedHandler accessDeniedHandler) {
		this.accessDeniedHandler = accessDeniedHandler;
		return this;
	}

	/**
	 * Sets a default {@link AccessDeniedHandler} to be used which prefers being
	 * invoked for the provided {@link RequestMatcher}. If only a single default
	 * {@link AccessDeniedHandler} is specified, it will be what is used for the
	 * default {@link AccessDeniedHandler}. If multiple default
	 * {@link AccessDeniedHandler} instances are configured, then a
	 * {@link RequestMatcherDelegatingAccessDeniedHandler} will be used.
	 *
	 * @param deniedHandler    the {@link AccessDeniedHandler} to use
	 * @param preferredMatcher the {@link RequestMatcher} for this default
	 *                         {@link AccessDeniedHandler}
	 * @return the {@link ExceptionHandlingConfigurer} for further customizations
	 * @since 5.1
	 */
	public ExceptionHandlingConfigurer<H> defaultAccessDeniedHandlerFor(AccessDeniedHandler deniedHandler,
			RequestMatcher preferredMatcher) {
		this.defaultDeniedHandlerMappings.put(preferredMatcher, deniedHandler);
		return this;
	}

	/**
	 * Sets the {@link AuthenticationEntryPoint} to be used.
	 *
	 * <p>
	 * If no {@link #authenticationEntryPoint(AuthenticationEntryPoint)} is
	 * specified, then
	 * {@link #defaultAuthenticationEntryPointFor(AuthenticationEntryPoint, RequestMatcher)}
	 * will be used. The first {@link AuthenticationEntryPoint} will be used as the
	 * default is no matches were found.
	 * </p>
	 *
	 * <p>
	 * If that is not provided defaults to {@link Http403ForbiddenEntryPoint}.
	 * </p>
	 *
	 * @param authenticationEntryPoint the {@link AuthenticationEntryPoint} to use
	 * @return the {@link ExceptionHandlingConfigurer} for further customizations
	 */
	public ExceptionHandlingConfigurer<H> authenticationEntryPoint(AuthenticationEntryPoint authenticationEntryPoint) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		return this;
	}

	/**
	 * Sets a default {@link AuthenticationEntryPoint} to be used which prefers
	 * being invoked for the provided {@link RequestMatcher}. If only a single
	 * default {@link AuthenticationEntryPoint} is specified, it will be what is
	 * used for the default {@link AuthenticationEntryPoint}. If multiple default
	 * {@link AuthenticationEntryPoint} instances are configured, then a
	 * {@link DelegatingAuthenticationEntryPoint} will be used.
	 *
	 * @param entryPoint       the {@link AuthenticationEntryPoint} to use
	 * @param preferredMatcher the {@link RequestMatcher} for this default
	 *                         {@link AuthenticationEntryPoint}
	 * @return the {@link ExceptionHandlingConfigurer} for further customizations
	 */
	public ExceptionHandlingConfigurer<H> defaultAuthenticationEntryPointFor(AuthenticationEntryPoint entryPoint,
			RequestMatcher preferredMatcher) {
		this.defaultEntryPointMappings.put(preferredMatcher, entryPoint);
		return this;
	}

	/**
	 * Gets any explicitly configured {@link AuthenticationEntryPoint}
	 * 
	 * @return
	 */
	AuthenticationEntryPoint getAuthenticationEntryPoint() {
		return this.authenticationEntryPoint;
	}

	/**
	 * Gets the {@link AccessDeniedHandler} that is configured.
	 *
	 * @return the {@link AccessDeniedHandler}
	 */
	AccessDeniedHandler getAccessDeniedHandler() {
		return this.accessDeniedHandler;
	}

	@Override
	public void configure(H http) throws Exception {
		// 身份验证入口点（驱动应用开始进行身份验证），用于启动身份验证方案（默认：Http403ForbiddenEntryPoint）

		AuthenticationEntryPoint entryPoint = getAuthenticationEntryPoint(http);
		// 处理Filter链中抛出的AccessDeniedException与AuthenticationException类型的异常;
		// 它提供了Java异常和HTTP响应之间的桥梁
		ExceptionTranslationFilter exceptionTranslationFilter = new ExceptionTranslationFilter(entryPoint,
				getRequestCache(http));
		// 默认为AccessDeniedHandlerImpl【响应403】
		AccessDeniedHandler deniedHandler = getAccessDeniedHandler(http);
		exceptionTranslationFilter.setAccessDeniedHandler(deniedHandler);
		exceptionTranslationFilter = postProcess(exceptionTranslationFilter);
		http.addFilter(exceptionTranslationFilter);
	}

	/**
	 * Gets the {@link AccessDeniedHandler} according to the rules specified by
	 * {@link #accessDeniedHandler(AccessDeniedHandler)}
	 * 
	 * @param http the {@link HttpSecurity} used to look up shared
	 *             {@link AccessDeniedHandler}
	 * @return the {@link AccessDeniedHandler} to use
	 */
	AccessDeniedHandler getAccessDeniedHandler(H http) {
		AccessDeniedHandler deniedHandler = this.accessDeniedHandler;
		if (deniedHandler == null) {
			deniedHandler = createDefaultDeniedHandler(http);
		}
		return deniedHandler;
	}

	/**
	 * Gets the {@link AuthenticationEntryPoint} according to the rules specified by
	 * {@link #authenticationEntryPoint(AuthenticationEntryPoint)}
	 * 
	 * @param http the {@link HttpSecurity} used to look up shared
	 *             {@link AuthenticationEntryPoint}
	 * @return the {@link AuthenticationEntryPoint} to use
	 */
	AuthenticationEntryPoint getAuthenticationEntryPoint(H http) {
		AuthenticationEntryPoint entryPoint = this.authenticationEntryPoint;
		if (entryPoint == null) {
			entryPoint = createDefaultEntryPoint(http);
		}
		return entryPoint;
	}
	// 创建缺省使用的 AccessDeniedHandler ： 访问被拒绝时的处理器 :
	// 1. 如果 this.defaultDeniedHandlerMappings 为空，则是用一个新的 AccessDeniedHandlerImpl
	// 对象：访问被拒绝时想浏览器返回状态字 403
	// 2. 如果 this.defaultDeniedHandlerMappings 包含一个元素，这是用该元素;
	// 3. 如果 this.defaultDeniedHandlerMappings 包含多个元素，则构造一个
	// RequestMatcherDelegatingAccessDeniedHandler 对象包装和代理
	// this.defaultDeniedHandlerMappings 中的这组元素，此
	// RequestMatcherDelegatingAccessDeniedHandler
	// 缺省的 AccessDeniedHandler 则是一个新的 AccessDeniedHandlerImpl
	// 对象：访问被拒绝时想浏览器返回状态字 403

	private AccessDeniedHandler createDefaultDeniedHandler(H http) {
		if (this.defaultDeniedHandlerMappings.isEmpty()) {
			return new AccessDeniedHandlerImpl();
		}
		if (this.defaultDeniedHandlerMappings.size() == 1) {
			return this.defaultDeniedHandlerMappings.values().iterator().next();
		}
		return new RequestMatcherDelegatingAccessDeniedHandler(this.defaultDeniedHandlerMappings,
				new AccessDeniedHandlerImpl());
	}
	 // 创建缺省使用的 AuthenticationEntryPoint ：
    // 1. 如果 this.defaultEntryPointMappings 为空，则使用一个 Http403ForbiddenEntryPoint 实例
    // 2. 如果 this.defaultEntryPointMappings 只包含一个元素，直接使用该元素
    // 3. 如果 this.defaultEntryPointMappings 有多个元素，构建一个 DelegatingAuthenticationEntryPoint
    // 代理对象供使用，该代理对象也是一个 AuthenticationEntryPoint，它将任务代理给 
    // this.defaultEntryPointMappings 中的各个 AuthenticationEntryPoint 对象,并将其中第一个设置为
    // 缺省
//————————————————
//版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/andy_zhang2007/article/details/93380379
	private AuthenticationEntryPoint createDefaultEntryPoint(H http) {
		if (this.defaultEntryPointMappings.isEmpty()) {
			return new Http403ForbiddenEntryPoint();
		}
		if (this.defaultEntryPointMappings.size() == 1) {
			return this.defaultEntryPointMappings.values().iterator().next();
		}
		DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(
				this.defaultEntryPointMappings);
		entryPoint.setDefaultEntryPoint(this.defaultEntryPointMappings.values().iterator().next());
		return entryPoint;
	}

	/**
	 * Gets the {@link RequestCache} to use. If one is defined using
	 * {@link #requestCache(org.springframework.security.web.savedrequest.RequestCache)},
	 * then it is used. Otherwise, an attempt to find a {@link RequestCache} shared
	 * object is made. If that fails, an {@link HttpSessionRequestCache} is used
	 *
	 * @param http the {@link HttpSecurity} to attempt to fined the shared object
	 * @return the {@link RequestCache} to use
	 */
	private RequestCache getRequestCache(H http) {
		RequestCache result = http.getSharedObject(RequestCache.class);
		if (result != null) {
			return result;
		}
		return new HttpSessionRequestCache();
	}
}
