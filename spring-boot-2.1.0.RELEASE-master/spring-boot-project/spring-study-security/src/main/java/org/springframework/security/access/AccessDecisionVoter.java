/*
 * Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
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

package org.springframework.security.access;

import java.util.Collection;

import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

/**ccessDecisionVoter主要的职责就是对它所对应的访问规则作出判断，当前的访问规则是否可以得到授权。
 *  AccessDecisionVoter接口的主要方法其实与之前的 AuthenticationProvider非常的相似。
 * 名称	                            	支持Secure Object类型	           支持ConfigAttribute类型
  WebExpressionVoter					FilterInvocation	           WebExpressionConfigAttribute
  Jsr250Voter	        				MethodInvocation	           Jsr250SecurityConfig
  PreInvocationAuthorizationAdviceVoter	Object						   PreInvocationAttribute
  AuthenticatedVoter					Object						    可返回属于特定集合的字符串的ConfigAttribute
  RoleVoter								Object						    可返回带有特定前缀(缺省ROLE_)的字符串的ConfigAttribute
  AbstractAclVoter						MethodInvocation	                 未知，留待具体实现子类确定

 *  
 * Indicates a class is responsible for voting on authorization decisions.
 * <p>
 * The coordination of voting (ie polling {@code AccessDecisionVoter}s, tallying their
 * responses, and making the final authorization decision) is performed by an
 * {@link org.springframework.security.access.AccessDecisionManager}.
 *
 * @author Ben Alex
 */
public interface AccessDecisionVoter<S> {
	// ~ Static fields/initializers
	// =====================================================================================
	//表示投票通过
	int ACCESS_GRANTED = 1;
	//表示弃权
	int ACCESS_ABSTAIN = 0;
	//表示拒绝
	int ACCESS_DENIED = -1;

	// ~ Methods
	// ========================================================================================================

	/**
	 * Indicates whether this {@code AccessDecisionVoter} is able to vote on the passed
	 * {@code ConfigAttribute}.
	 * <p>
	 * This allows the {@code AbstractSecurityInterceptor} to check every configuration
	 * attribute can be consumed by the configured {@code AccessDecisionManager} and/or
	 * {@code RunAsManager} and/or {@code AfterInvocationManager}.
	 *
	 * @param attribute a configuration attribute that has been configured against the
	 * {@code AbstractSecurityInterceptor}
	 *
	 * @return true if this {@code AccessDecisionVoter} can support the passed
	 * configuration attribute
	 */
	boolean supports(ConfigAttribute attribute);

	/**
	 * Indicates whether the {@code AccessDecisionVoter} implementation is able to provide
	 * access control votes for the indicated secured object type.
	 *
	 * @param clazz the class that is being queried
	 *
	 * @return true if the implementation can process the indicated class
	 */
	boolean supports(Class<?> clazz);

	/**具体的投票方法，根据用户所具有的权限以及当前
	 * 请求需要的权限进行投票。vote方法有三个参数：
	 * 第一个参数：authentication中可以提取出来当前用户所具备的权限
	 * 第二个参数：object表示受保护的安全对象，如果受保护的是url地址
	 * 则object就是一个filterInvocation对象；
	 * 如果是受保护的为一个方法，则object就是一个methodInvocation对象。
	 * 第三个参数attribute表示访问受保护对象的所需要的权限。
	 * vote方法的返回值就是前面所定义的三个常量之一
	 * 
	 * Indicates whether or not access is granted.
	 * <p>
	 * The decision must be affirmative ({@code ACCESS_GRANTED}), negative (
	 * {@code ACCESS_DENIED}) or the {@code AccessDecisionVoter} can abstain (
	 * {@code ACCESS_ABSTAIN}) from voting. Under no circumstances should implementing
	 * classes return any other value. If a weighting of results is desired, this should
	 * be handled in a custom
	 * {@link org.springframework.security.access.AccessDecisionManager} instead.
	 * <p>
	 * Unless an {@code AccessDecisionVoter} is specifically intended to vote on an access
	 * control decision due to a passed method invocation or configuration attribute
	 * parameter, it must return {@code ACCESS_ABSTAIN}. This prevents the coordinating
	 * {@code AccessDecisionManager} from counting votes from those
	 * {@code AccessDecisionVoter}s without a legitimate interest in the access control
	 * decision.
	 * <p>
	 * Whilst the secured object (such as a {@code MethodInvocation}) is passed as a
	 * parameter to maximise flexibility in making access control decisions, implementing
	 * classes should not modify it or cause the represented invocation to take place (for
	 * example, by calling {@code MethodInvocation.proceed()}).
	 *
	 * @param authentication the caller making the invocation
	 * @param object the secured object being invoked
	 * @param attributes the configuration attributes associated with the secured object
	 *
	 * @return either {@link #ACCESS_GRANTED}, {@link #ACCESS_ABSTAIN} or
	 * {@link #ACCESS_DENIED}
	 */
	int vote(Authentication authentication, S object,
			Collection<ConfigAttribute> attributes);
}
