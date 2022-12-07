
package org.springframework.security.authentication;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * AuthenticationManager会在Spring
 * Security应用配置阶段被构建，
 * 比如被某个WebSecurityConfigurerAdapter构建，然后在工作阶段被使用。比如一个基于用户名密码认证机制的Spring
 * Web MVC + Spring
 * Security应用，应用/容器启动过程中，
 * AuthenticationManager构建后会被设置到基于用户名密码进行认证的安全过滤器
 * UsernamePasswordAuthenticationFilter上，缺省情况下，当请求为访问地址/login的POST请求时，
 * UsernamePasswordAuthenticationFilter就会认为这是一个用户认证请求，从而获取请求中的用户名/密码信息，
 * 使用AuthenticationManager认证该请求用户的身份
 *
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/90199391
 *  Spring  Security框架提供了AuthenticationManager的缺省实现ProviderManager
 * 
 * Processes an Authentication request.
 *
 * @author Ben Alex
 */
//概念模型 AuthenticationManager 认证管理器 
public interface AuthenticationManager {
	/**
	 * Attempts to authenticate the passed Authentication object, returning a fully
	 * populated Authentication object (including granted authorities) if
	 * successful.
	 * 
	 * An AuthenticationManager must honour the following contract concerning
	 * exceptions:
	 * 
	 * A DisabledException must be thrown if an account is disabled and the
	 * AuthenticationManager can test for this state. A LockedException must be
	 * thrown if an account is locked and the AuthenticationManager can test for
	 * account locking. A BadCredentialsException must be thrown if incorrect
	 * credentials are presented. Whilst the above exceptions are optional, an
	 * AuthenticationManager must always test credentials.
	 * 
	 * Exceptions should be tested for and if applicable thrown in the order
	 * expressed above (i.e. if an account is disabled or locked, the authentication
	 * request is immediately rejected and the credentials testing process is not
	 * performed). This prevents credentials being tested against disabled or locked
	 * accounts.
	 *
	 * @param authentication the authentication request object
	 *
	 * @return a fully authenticated object including credentials
	 *
	 * @throws AuthenticationException if authentication fails
	 */
	Authentication authenticate(Authentication authentication) throws AuthenticationException;
}
