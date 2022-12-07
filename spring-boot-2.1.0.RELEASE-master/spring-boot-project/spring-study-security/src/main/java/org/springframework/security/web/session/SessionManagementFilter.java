/*
 * Copyright 2002-2016 the original author or authors.
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
package org.springframework.security.web.session;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.Assert;
import org.springframework.web.filter.GenericFilterBean;

/**
 * 该过滤器会检测从当前请求处理开始到目前为止的过程中是否发生了用户登录认证行为(比如这是一个用户名/密码表单提交的请求处理过程)，
 * 如果检测到这一情况，执行相应的session认证策略(一个SessionAuthenticationStrategy)，然后继续继续请求的处理。
 * 
 * 针对Servlet
 * 3.1+,缺省所使用的SessionAuthenticationStrategy会是一个ChangeSessionIdAuthenticationStrategy和CsrfAuthenticationStrategy组合。
 * ChangeSessionIdAuthenticationStrategy会为登录的用户创建一个新的session，而CsrfAuthenticationStrategy会创建新的csrf
 * token用于CSRF保护。
 * 
 * 如果当前过滤器链中启用了UsernamePasswordAuthenticationFilter,实际上本过滤器SessionManagementFilter并不会真正被执行到上面所说的逻辑。
 * 因为在UsernamePasswordAuthenticationFilter中，一旦用户登录认证发生它会先执行上述的逻辑。因此到SessionManagementFilter执行的时候，
 * 它会发现安全上下文存储库中已经有相应的安全上下文了，从而不再重复执行上面的逻辑。
 * 
 * 另外需要注意的是，如果相应的session认证策略执行失败的话，整个成功的用户登录认证行为会被该过滤器否定，
 * 相应新建的SecurityContextHolder中的安全上下文会被清除，所设定的AuthenticationFailureHandler逻辑会被执行。
 * ———————————————— 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0
 * BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/84896907 Detects
 * that a user has been authenticated since the start of the request and, if
 * they have, calls the configured {@link SessionAuthenticationStrategy} to
 * perform any session-related activity such as activating session-fixation
 * protection mechanisms or checking for multiple concurrent logins.
 *
 * @author Martin Algesten
 * @author Luke Taylor
 * @since 2.0
 */
public class SessionManagementFilter extends GenericFilterBean {
	// ~ Static fields/initializers
	// =====================================================================================

	static final String FILTER_APPLIED = "__spring_security_session_mgmt_filter_applied";

	// ~ Instance fields
	// ================================================================================================
	// 安全上下文存储库，要和当前请求处理过程中其他filter配合，一般由配置阶段使用统一配置设置进来
	// 缺省使用基于http session的安全上下文存储库:HttpSessionSecurityContextRepository
	private final SecurityContextRepository securityContextRepository;
	// session 认证策略
	// 缺省是一个 CompositeSessionAuthenticationStrategy 对象，应用了组合模式，组合一些其他的
	// session 认证策略实现，比如针对Servlet 3.1+,缺省会是 ChangeSessionIdAuthenticationStrategy跟
	// CsrfAuthenticationStrategy组合

	private SessionAuthenticationStrategy sessionAuthenticationStrategy;
	// 用于识别一个Authentication是哪种类型:anonymous ? remember ?
	// 配置阶段统一指定，缺省使用 AuthenticationTrustResolverImpl
	private AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
	private InvalidSessionStrategy invalidSessionStrategy = null;
	// 认证失败处理器，比如form用户名/密码登录如果失败可能会引导用户重新发起表单认证
	// 缺省使用SimpleUrlAuthenticationFailureHandler
	private AuthenticationFailureHandler failureHandler = new SimpleUrlAuthenticationFailureHandler();

	public SessionManagementFilter(SecurityContextRepository securityContextRepository) {
		this(securityContextRepository, new SessionFixationProtectionStrategy());
	}

	public SessionManagementFilter(SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionStrategy) {
		Assert.notNull(securityContextRepository, "SecurityContextRepository cannot be null");
		Assert.notNull(sessionStrategy, "SessionAuthenticationStrategy cannot be null");
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionStrategy;
	}

	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		if (request.getAttribute(FILTER_APPLIED) != null) {
			// 如果在当前请求过程中该过滤器已经应用过，则不在二次应用，继续filter chain的执行
			chain.doFilter(request, response);
			return;
		}
		// 该过滤器要执行了，在请求上设置该过滤器已经执行过的标记，避免在该请求的同一处理过程中
		// 本过滤器执行二遍
		request.setAttribute(FILTER_APPLIED, Boolean.TRUE);
		// 检测securityContextRepository是否已经保存了针对当前请求的安全上下文对象 ：
		// 1. 未登录用户访问登录保护url的情况 : 否
		// 2. 未登录用户访问登录页面url的情况 : (不会走到这里，已经被登录页面生成Filter拦截)
		// 3. 未登录用户访问公开url的情况 : 否
		// 4. 登录用户访问公开url的情况 : 是
		// 5. 登录用户访问登录保护url的情况 : 是
		// 6. 登录用户访问公开url的情况 : 是

		if (!securityContextRepository.containsContext(request)) {
			// 如果securityContextRepository中没有保存安全上下文对象，
			// 但是SecurityContextHolder中安全上下文对象的authentication属性
			// 不为null或者匿名，则说明从请求处理开始到现在出现了用户登录认证成功的情况

			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

			if (authentication != null && !trustResolver.isAnonymous(authentication)) {
				// The user has been authenticated during the current request, so call the
				// session strategy
				try {
					sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
				} catch (SessionAuthenticationException e) {
					// The session strategy can reject the authentication
					logger.debug("SessionAuthenticationStrategy rejected the authentication object", e);
					SecurityContextHolder.clearContext();
					failureHandler.onAuthenticationFailure(request, response, e);

					return;
				}
				// Eagerly save the security context to make it available for any possible
				// re-entrant
				// requests which may occur before the current request completes.
				// SEC-1396.
				// 如果该请求处理过程中出现了用户成功登录的情况，并且相应的session认证策略已经
				// 执行成功，直接在securityContextRepository保存新建的针对已经登录用户的安全上下文，
				// 这样之后，在当前请求处理结束前，遇到任何可重入的请求，它们就可以利用该信息了。

				securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
			} else {
				// No security context or authentication present. Check for a session
				// timeout
				if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) {
					if (logger.isDebugEnabled()) {
						logger.debug("Requested session ID " + request.getRequestedSessionId() + " is invalid.");
					}

					if (invalidSessionStrategy != null) {
						invalidSessionStrategy.onInvalidSessionDetected(request, response);
						return;
					}
				}
			}
		}

		chain.doFilter(request, response);
	}

	/**
	 * Sets the strategy which will be invoked instead of allowing the filter chain
	 * to prceed, if the user agent requests an invalid session Id. If the property
	 * is not set, no action will be taken.
	 *
	 * @param invalidSessionStrategy the strategy to invoke. Typically a
	 *                               {@link SimpleRedirectInvalidSessionStrategy}.
	 */
	public void setInvalidSessionStrategy(InvalidSessionStrategy invalidSessionStrategy) {
		this.invalidSessionStrategy = invalidSessionStrategy;
	}

	/**
	 * The handler which will be invoked if the
	 * <tt>AuthenticatedSessionStrategy</tt> raises a
	 * <tt>SessionAuthenticationException</tt>, indicating that the user is not
	 * allowed to be authenticated for this session (typically because they already
	 * have too many sessions open).
	 *
	 */
	public void setAuthenticationFailureHandler(AuthenticationFailureHandler failureHandler) {
		Assert.notNull(failureHandler, "failureHandler cannot be null");
		this.failureHandler = failureHandler;
	}

	/**
	 * Sets the {@link AuthenticationTrustResolver} to be used. The default is
	 * {@link AuthenticationTrustResolverImpl}.
	 *
	 * @param trustResolver the {@link AuthenticationTrustResolver} to use. Cannot
	 *                      be null.
	 */
	public void setTrustResolver(AuthenticationTrustResolver trustResolver) {
		Assert.notNull(trustResolver, "trustResolver cannot be null");
		this.trustResolver = trustResolver;
	}
}
