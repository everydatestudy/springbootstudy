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

package org.springframework.web.method.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Handles method return values by delegating to a list of registered {@link HandlerMethodReturnValueHandler}s.
 * Previously resolved return types are cached for faster lookups.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
//首先发现，它也实现了接口HandlerMethodReturnValueHandler 
//它会缓存以前解析的返回类型以加快查找速度
public class HandlerMethodReturnValueHandlerComposite implements HandlerMethodReturnValueHandler {

	protected final Log logger = LogFactory.getLog(getClass());

	private final List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList<>();


	/**返回的是一个只读视图
	 * Return a read-only list with the registered handlers, or an empty list.
	 */
	public List<HandlerMethodReturnValueHandler> getHandlers() {
		return Collections.unmodifiableList(this.returnValueHandlers);
	}

	/**
	 * Whether the given {@linkplain MethodParameter method return type} is supported by any registered
	 * {@link HandlerMethodReturnValueHandler}.
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return getReturnValueHandler(returnType) != null;
	}

	@Nullable
	private HandlerMethodReturnValueHandler getReturnValueHandler(MethodParameter returnType) {
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			if (handler.supportsReturnType(returnType)) {
				return handler;
			}
		}
		return null;
	}

	/**这里就是处理返回值的核心内容~~~~~
	 * Iterate over registered {@link HandlerMethodReturnValueHandler}s and invoke the one that supports it.
	 * @throws IllegalStateException if no suitable {@link HandlerMethodReturnValueHandler} is found.
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		//这里判断是否存在reponsebody
		// selectHandler选择收个匹配的Handler来处理这个返回值~~~~ 若一个都木有找到  抛出异常吧~~~~
		// 所有很重要的一个方法是它：selectHandler()  它来匹配，以及确定优先级
		HandlerMethodReturnValueHandler handler = selectHandler(returnValue, returnType);
		if (handler == null) {
			throw new IllegalArgumentException("Unknown return value type: " + returnType.getParameterType().getName());
		}
		handler.handleReturnValue(returnValue, returnType, mavContainer, webRequest);
	}
	// 根据返回值，以及返回类型  来找到一个最为合适的HandlerMethodReturnValueHandler
	@Nullable
	private HandlerMethodReturnValueHandler selectHandler(@Nullable Object value, MethodParameter returnType) {
		// 这个和我们上面的就对应上了  第一步去判断这个返回值是不是一个异步的value（AsyncHandlerMethodReturnValueHandler实现类只能我们自己来写~）
		boolean isAsyncValue = isAsyncReturnValue(value, returnType);
//		org.springframework.web.servlet.mvc.method.annotation.ModelAndViewMethodReturnValueHandler@fa87b1f
//		org.springframework.web.method.annotation.ModelMethodProcessor@16f949a
//		org.springframework.web.servlet.mvc.method.annotation.ViewMethodReturnValueHandler@550106f
//		org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitterReturnValueHandler@40529ea1
//		org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBodyReturnValueHandler@cee6537
//		org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor@7158fb74
//		org.springframework.web.servlet.mvc.method.annotation.HttpHeadersReturnValueHandler@740e5e58
//		org.springframework.web.servlet.mvc.method.annotation.CallableMethodReturnValueHandler@207d61ee
//		org.springframework.web.servlet.mvc.method.annotation.DeferredResultMethodReturnValueHandler@5f456f0b
//		org.springframework.web.servlet.mvc.method.annotation.AsyncTaskMethodReturnValueHandler@69f0bccc
//		org.springframework.web.method.annotation.ModelAttributeMethodProcessor@6f0ad334
//		org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor@2433bd2a
//		org.springframework.web.servlet.mvc.method.annotation.ViewNameMethodReturnValueHandler@507ab390
//		org.springframework.web.method.annotation.MapMethodProcessor@3c80738f
//		org.springframework.web.method.annotation.ModelAttributeMethodProcessor@52a8458
		/**这里是所有的默认处理器，是在类{@link RequestMappingHandlerAdapter}*/
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			if (isAsyncValue && !(handler instanceof AsyncHandlerMethodReturnValueHandler)) {
				continue;
			}
			if (handler.supportsReturnType(returnType)) {
				return handler;
			}
		}
		return null;
	}

	private boolean isAsyncReturnValue(@Nullable Object value, MethodParameter returnType) {
		for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
			if (handler instanceof AsyncHandlerMethodReturnValueHandler &&
					((AsyncHandlerMethodReturnValueHandler) handler).isAsyncReturnValue(value, returnType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler}.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandler(HandlerMethodReturnValueHandler handler) {
		this.returnValueHandlers.add(handler);
		return this;
	}

	/**
	 * Add the given {@link HandlerMethodReturnValueHandler}s.
	 */
	public HandlerMethodReturnValueHandlerComposite addHandlers(
			@Nullable List<? extends HandlerMethodReturnValueHandler> handlers) {

		if (handlers != null) {
			this.returnValueHandlers.addAll(handlers);
		}
		return this;
	}

}
