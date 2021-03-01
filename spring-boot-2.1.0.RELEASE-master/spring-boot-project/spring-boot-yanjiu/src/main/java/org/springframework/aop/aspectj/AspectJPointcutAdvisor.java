/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.aop.aspectj;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * AspectJPointcutAdvisor that adapts an {@link AbstractAspectJAdvice}
 * to the {@link org.springframework.aop.PointcutAdvisor} interface.
    *显然是和AspectJ相关的，使用得很是广泛。
    注意它和AspectJExpressionPointcutAdvisor的区别。
    有名字也能看出来，AspectJExpressionPointcutAdvisor和表达式语言的切点相关的，
    而AspectJPointcutAdvisor是无关的。它哥俩都位于包org.springframework.aop.aspectj里。
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @since 2.0
 */
//而这个 Advisor中的 Pointcut与Advice都是由
//ReflectiveAspectJAdvisorFactory 来解析生成的
//(与之对应的 Advice 是 AspectJMethodBeforeAdvice,
//AspectJAfterAdvice, AspectJAfterReturningAdvice, AspectJAfterThrowingAdvice, AspectJAroundAdvice,
public class AspectJPointcutAdvisor implements PointcutAdvisor, Ordered {
	// AbstractAspectJAdvice通知：它的子类看下面截图，就非常清楚了
	private final AbstractAspectJAdvice advice;
	// 可以接受任意的Pointcut，可谓非常的通用（当然也包含切点表达式啦）
	private final Pointcut pointcut;

	@Nullable
	private Integer order;


	/**
	 * Create a new AspectJPointcutAdvisor for the given advice
	 * @param advice the AbstractAspectJAdvice to wrap
	 */
	// 只有这一个构造函数，包装一个advice
	public AspectJPointcutAdvisor(AbstractAspectJAdvice advice) {
		Assert.notNull(advice, "Advice must not be null");
		this.advice = advice;
		// 然后pointcut根据advice直接给生成了一个。这是AbstractAspectJAdvice#buildSafePointcut的方法
		this.pointcut = advice.buildSafePointcut();
	}


	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		if (this.order != null) {
			return this.order;
		}
		else {
			return this.advice.getOrder();
		}
	}

	@Override
	public boolean isPerInstance() {
		return true;
	}

	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	/**
	 * Return the name of the aspect (bean) in which the advice was declared.
	 * @since 4.3.15
	 * @see AbstractAspectJAdvice#getAspectName()
	 */
	public String getAspectName() {
		return this.advice.getAspectName();
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AspectJPointcutAdvisor)) {
			return false;
		}
		AspectJPointcutAdvisor otherAdvisor = (AspectJPointcutAdvisor) other;
		return this.advice.equals(otherAdvisor.advice);
	}

	@Override
	public int hashCode() {
		return AspectJPointcutAdvisor.class.hashCode() * 29 + this.advice.hashCode();
	}

}
