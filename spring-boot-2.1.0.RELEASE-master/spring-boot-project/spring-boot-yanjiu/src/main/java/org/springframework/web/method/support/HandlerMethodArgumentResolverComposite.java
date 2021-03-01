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

package org.springframework.web.method.support;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 具体的参数解析器的选择和使用参数解析器去解析参数的实现都在getMethodArgumentValues中，
 * 首先springMVC在启动时会将所有的参数解析器放到HandlerMethodArgumentResolverComposite，
 * HandlerMethodArgumentResolverComposite是所有参数的一个集合，
 * 接下来就是从HandlerMethodArgumentResolverComposite参数解析器集合中选择一个支持对parameter解析的参数解析器，接下来就使用支持参数解析的解析器进行参数解析。
 * 
 
 * method parameters by delegating to a list of registered
 * {@link HandlerMethodArgumentResolver}s. Previously resolved method parameters
 * are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class HandlerMethodArgumentResolverComposite implements HandlerMethodArgumentResolver {

	protected final Log logger = LogFactory.getLog(getClass());
	//保存springMVC提供的所有的参数解析器
	private final List<HandlerMethodArgumentResolver> argumentResolvers = new LinkedList<>();

	//用于缓存已经查找过的参数解析器
	private final Map<MethodParameter, HandlerMethodArgumentResolver> argumentResolverCache = new ConcurrentHashMap<>(
			256);

	/**
	 * Add the given {@link HandlerMethodArgumentResolver}.
	 */
	public HandlerMethodArgumentResolverComposite addResolver(HandlerMethodArgumentResolver resolver) {
		this.argumentResolvers.add(resolver);
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodArgumentResolver}s.
	 * 
	 * @since 4.3
	 */
	public HandlerMethodArgumentResolverComposite addResolvers(@Nullable HandlerMethodArgumentResolver... resolvers) {
		if (resolvers != null) {
			for (HandlerMethodArgumentResolver resolver : resolvers) {
				this.argumentResolvers.add(resolver);
			}
		}
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodArgumentResolver}s.
	 */
	public HandlerMethodArgumentResolverComposite addResolvers(
			@Nullable List<? extends HandlerMethodArgumentResolver> resolvers) {

		if (resolvers != null) {
			for (HandlerMethodArgumentResolver resolver : resolvers) {
				this.argumentResolvers.add(resolver);
			}
		}
		return this;
	}

	/**
	 * Return a read-only list with the contained resolvers, or an empty list.
	 */
	public List<HandlerMethodArgumentResolver> getResolvers() {
		return Collections.unmodifiableList(this.argumentResolvers);
	}

	/**
	 * Clear the list of configured resolvers.
	 * 
	 * @since 4.3
	 */
	public void clear() {
		this.argumentResolvers.clear();
	}

	/**	//判断参数解析器是否支持参数解析
	 * Whether the given {@linkplain MethodParameter method parameter} is supported
	 * by any registered {@link HandlerMethodArgumentResolver}.
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (getArgumentResolver(parameter) != null);
	}

	/**
	 * Iterate over registered {@link HandlerMethodArgumentResolver}s and invoke the
	 * one that supports it.
	 * 
	 * @throws IllegalStateException if no suitable
	 *                               {@link HandlerMethodArgumentResolver} is found.
	 */
	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		HandlerMethodArgumentResolver resolver = getArgumentResolver(parameter);
		if (resolver == null) {
			throw new IllegalArgumentException(
					"Unknown parameter type [" + parameter.getParameterType().getName() + "]");
		}
		// 通过HttpMessageConverter读取HTTP报文
		return resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
	}

	/**
	 * Find a registered {@link HandlerMethodArgumentResolver} that supports the
	 * given method parameter.
	 */
	@Nullable
	private HandlerMethodArgumentResolver getArgumentResolver(MethodParameter parameter) {
		HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
		if (result == null) {
//			[org.springframework.web.method.annotation.RequestParamMethodArgumentResolver@6bb56f2f, 
//			 org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver@2794c5b,
//			 org.springframework.web.servlet.mvc.method.annotation.PathVariableMethodArgumentResolver@2052c061,
//			 org.springframework.web.servlet.mvc.method.annotation.PathVariableMapMethodArgumentResolver@5218dd4,
//			 org.springframework.web.servlet.mvc.method.annotation.MatrixVariableMethodArgumentResolver@452d4648, 
//			 org.springframework.web.servlet.mvc.method.annotation.MatrixVariableMapMethodArgumentResolver@dfec122,
//			 org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor@3b994934, 
//			 org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor@325f3cbe,
//			 org.springframework.web.servlet.mvc.method.annotation.RequestPartMethodArgumentResolver@1d6f23bb,
//			 org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver@66e306e5,
//			 org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver@174233e4,
//			 org.springframework.web.servlet.mvc.method.annotation.ServletCookieValueMethodArgumentResolver@5c33477e,
//			 org.springframework.web.method.annotation.ExpressionValueMethodArgumentResolver@3825d5b8, 
//			 org.springframework.web.servlet.mvc.method.annotation.SessionAttributeMethodArgumentResolver@468f3877, 
//			 org.springframework.web.servlet.mvc.method.annotation.RequestAttributeMethodArgumentResolver@4054ee51, 
//			 org.springframework.web.servlet.mvc.method.annotation.ServletRequestMethodArgumentResolver@61a4992, 
//			 org.springframework.web.servlet.mvc.method.annotation.ServletResponseMethodArgumentResolver@1004c4fc, 
//			 org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor@2ff70a0b, 
//			 org.springframework.web.servlet.mvc.method.annotation.RedirectAttributesMethodArgumentResolver@48b65fc2, 
//			 org.springframework.web.method.annotation.ModelMethodProcessor@1cb4c99f, 
//			 org.springframework.web.method.annotation.MapMethodProcessor@2aa1ddd1, 
//			 org.springframework.web.method.annotation.ErrorsMethodArgumentResolver@3d113ddc, 
//			 org.springframework.web.method.annotation.SessionStatusMethodArgumentResolver@47232df3, 
//			 org.springframework.web.servlet.mvc.method.annotation.UriComponentsBuilderMethodArgumentResolver@7de1fcb9, 
//			 org.springframework.web.method.annotation.RequestParamMethodArgumentResolver@590af981, 
//			 org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor@6dc1d0a1]
			for (HandlerMethodArgumentResolver methodArgumentResolver : this.argumentResolvers) {
				if (logger.isTraceEnabled()) {
					logger.trace("Testing if argument resolver [" + methodArgumentResolver + "] supports ["
							+ parameter.getGenericParameterType() + "]");
				}
				// supportsParameter方法寻找参数合适的解析器，resolveArgument调用具体解析器的resolveArgument方法执行。
				if (methodArgumentResolver.supportsParameter(parameter)) {
					result = methodArgumentResolver;
					this.argumentResolverCache.put(parameter, result);
					break;
				}
			}
		}
		return result;
	}

}
