//在Spring Security中，AuthenticationProvider通常的用法是交由ProviderManager统一管理和应用。ProviderManager是一个AuthenticationManager实现，它会被广泛应用于认证认证令牌对象Authentication,但实际上具体的认证工作是委托给了ProviderManager所管理的一组AuthenticationProvider上。
//————————————————
//版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/andy_zhang2007/article/details/90201594
package org.springframework.security.authentication;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * 判断用户认证 在Spring
 * Security中，AuthenticationProvider通常的用法是交由ProviderManager统一管理和应用。
 * ProviderManager是一个AuthenticationManager实现，它会被广泛应用于认证认证令牌对象Authentication,
 * 但实际上具体的认证工作是委托给了ProviderManager所管理的一组AuthenticationProvider上。
 * 
 * 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/90201594 Indicates
 * a class can process a specific
 * {@link org.springframework.security.core.Authentication} implementation.
 *
 * @author Ben Alex
 */
public interface AuthenticationProvider {
	/**
	 * Performs authentication with the same contract as
	 * org.springframework.security.authentication.AuthenticationManager#authenticate(Authentication)
	 * 
	 *
	 * 对提供的认证令牌对象 authentication 执行认证逻辑。如果认证成功，返回信息填充完整的 的认证令牌对象 authentication。
	 * 无法完成认证的话返回 null。此时调用者，通常是 ProviderManager 使用其他的认证提供者继续尝试认证该认证令牌对象。
	 * 
	 * @param authentication the authentication request object.
	 *
	 * @return a fully authenticated object including credentials. May return null
	 *         if the AuthenticationProvider is unable to support authentication of
	 *         the passed Authentication object. In such a case, the next
	 *         AuthenticationProvider that supports the presented Authentication
	 *         class will be tried.
	 *
	 * @throws AuthenticationException if authentication fails.
	 */
	Authentication authenticate(Authentication authentication) throws AuthenticationException;

	/**
	 * Returns true if this AuthenticationProvider supports the indicated
	 * Authentication object.
	 * 
	 * 返回 true 表示当前 AuthenticationProvider 能够支持对该 Authentication 对象的 认证。 不过返回 true
	 * 并不表示当前 AuthenticationProvider 能成功认证该 Authentication 对象。
	 * 
	 * 该方法通常被 ProviderManager 用作判断当前 AuthenticationProvider 是否可以用于对某个 Authentication
	 * 执行认证的逻辑
	 * 
	 * Returning true does not guarantee an AuthenticationProvider will be able to
	 * authenticate the presented instance of the Authentication class. It simply
	 * indicates it can support closer evaluation of it. An AuthenticationProvider
	 * can still return null from the #authenticate(Authentication) method to
	 * indicate another AuthenticationProvider should be tried.
	 * 
	 * 
	 * Selection of an AuthenticationProvider capable of performing authentication
	 * is conducted at runtime the ProviderManager.
	 * 
	 *
	 * @param authentication
	 *
	 * @return true if the implementation can more closely evaluate the
	 *         Authentication class presented
	 */
	boolean supports(Class<?> authentication);
}
