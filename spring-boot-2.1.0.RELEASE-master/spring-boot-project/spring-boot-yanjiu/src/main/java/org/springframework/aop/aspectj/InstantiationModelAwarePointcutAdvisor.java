/*
 * Copyright 2002-2006 the original author or authors.
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

import org.springframework.aop.PointcutAdvisor;

/**
 * Interface to be implemented by Spring AOP Advisors wrapping AspectJ
 * aspects that may have a lazy initialization strategy. For example,
 * a perThis instantiation model would mean lazy initialization of the advice.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 */
//由SpringAOP顾问包装AspectJ实现的接口 可能具有延迟初始化策略的方面。
//例如，一个PerThis实例化模型意味着对建议的初始化太慢
public interface InstantiationModelAwarePointcutAdvisor extends PointcutAdvisor {

	/**
	 * Return whether this advisor is lazily initializing its underlying advice.
	 */
	// 该Advisor是否需要懒加载
	boolean isLazy();

	/**
	 * Return whether this advisor has already instantiated its advice.
	 */
	// 判断此Advisor它所拥有的Advice是否已经初始化了
	boolean isAdviceInstantiated();

}
