/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.web.method.support;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 策略接口：用于在给定请求的上下文中将方法参数解析为参数值。简单的理解为：它负责处理你Handler方法里的所有入参：包括自动封装、自动赋值、校验等等。有了它才能会让Spring MVC处理入参显得那么高级、那么自动化。
 * Spring MVC内置了非常非常多的实现，当然若还不能满足你的需求，你依旧可以自定义和自己注册，后面我会给出自定义的示例。

    有个形象的公式：HandlerMethodArgumentResolver = HandlerMethod + Argument(参数) + Resolver(解析器)。
    解释为：它是HandlerMethod方法的解析器，将HttpServletRequest(header + body 中的内容)解析为HandlerMethod方法的参数（method parameters）
 
     因为子类众多，所以我分类进行说明。我把它分为四类进行描述：

	基于Name
	数据类型是Map的
	固定参数类型
	基于ContentType的消息转换器
     从URI（路径变量）、HttpServletRequest、HttpSession、Header、Cookie…等中根据名称key来获取值
 
 * Strategy interface for resolving method parameters into argument values in
 * the context of a given request.
 *
 * @author Arjen Poutsma
 * @since 3.1
 * @see HandlerMethodReturnValueHandler
 */
public interface HandlerMethodArgumentResolver {

	/**
	 * // 判断 HandlerMethodArgumentResolver 是否支持 MethodParameter
	// (PS: 一般都是通过 参数上面的注解|参数的类型)
	 * Whether the given {@linkplain MethodParameter method parameter} is
	 * supported by this resolver.
	 * @param parameter the method parameter to check
	 * @return {@code true} if this resolver supports the supplied parameter;
	 * {@code false} otherwise
	 */
	boolean supportsParameter(MethodParameter parameter);

	/**
	 * // 从NativeWebRequest中获取数据，ModelAndViewContainer用来提供访问Model
	// MethodParameter parameter：请求参数
	// WebDataBinderFactory用于创建一个WebDataBinder用于数据绑定、校验
 
	 * Resolves a method parameter into an argument value from a given request.
	 * A {@link ModelAndViewContainer} provides access to the model for the
	 * request. A {@link WebDataBinderFactory} provides a way to create
	 * a {@link WebDataBinder} instance when needed for data binding and
	 * type conversion purposes.
	 * @param parameter the method parameter to resolve. This parameter must
	 * have previously been passed to {@link #supportsParameter} which must
	 * have returned {@code true}.
	 * @param mavContainer the ModelAndViewContainer for the current request
	 * @param webRequest the current request
	 * @param binderFactory a factory for creating {@link WebDataBinder} instances
	 * @return the resolved argument value, or {@code null} if not resolvable
	 * @throws Exception in case of errors with the preparation of argument values
	 */
	@Nullable
	Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception;

}
