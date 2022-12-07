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
package org.springframework.security.web.access.expression;

import java.util.Collection;

import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;

/**
 * DefaultWebSecurityExpressionHandler对给定的认证token和
 * 请求上下文FilterInvocation创建一个评估上下文EvaluationContext。
 * 然后供SPEL求值使用。比如在WebExpressionVoter中它被这么应用
 * : Voter which handles web authorisation decisions.
 * 
 * @author Luke Taylor
 * @since 3.0
 */
public class WebExpressionVoter implements AccessDecisionVoter<FilterInvocation> {
	// expressionHandler 缺省使用 DefaultWebSecurityExpressionHandler
	private SecurityExpressionHandler<FilterInvocation> expressionHandler = new DefaultWebSecurityExpressionHandler();

	public int vote(Authentication authentication, FilterInvocation fi, Collection<ConfigAttribute> attributes) {
		assert authentication != null;
		assert fi != null;
		assert attributes != null;
		// 获取Web安全表达式配置属性
		WebExpressionConfigAttribute weca = findConfigAttribute(attributes);

		if (weca == null) {
			// 获取Web安全表达式配置属性
			return ACCESS_ABSTAIN;
		}

		EvaluationContext ctx = expressionHandler.createEvaluationContext(authentication, fi);
		ctx = weca.postProcess(ctx, fi);
		// 获取Web安全表达式配置属性weca中的表达式，和上面所创建的表达式求值上下文ctx,
		// 对其进行求值，结果按 boolean 类型处理
		// 如果求值结果为 true, 返回 ACCESS_GRANTED:1, 否则返回 ACCESS_DENIED:-1
		return ExpressionUtils.evaluateAsBoolean(weca.getAuthorizeExpression(), ctx) ? ACCESS_GRANTED : ACCESS_DENIED;
	}

	private WebExpressionConfigAttribute findConfigAttribute(Collection<ConfigAttribute> attributes) {
		for (ConfigAttribute attribute : attributes) {
			if (attribute instanceof WebExpressionConfigAttribute) {
				return (WebExpressionConfigAttribute) attribute;
			}
		}
		return null;
	}

	public boolean supports(ConfigAttribute attribute) {
		return attribute instanceof WebExpressionConfigAttribute;
	}

	public boolean supports(Class<?> clazz) {
		return FilterInvocation.class.isAssignableFrom(clazz);
	}

	public void setExpressionHandler(SecurityExpressionHandler<FilterInvocation> expressionHandler) {
		this.expressionHandler = expressionHandler;
	}
}
