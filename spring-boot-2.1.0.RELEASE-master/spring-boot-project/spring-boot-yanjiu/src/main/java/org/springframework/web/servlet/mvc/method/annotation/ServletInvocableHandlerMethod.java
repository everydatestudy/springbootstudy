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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.View;
import org.springframework.web.util.NestedServletException;

/**
 * Extends {@link InvocableHandlerMethod} with the ability to handle return
 * values through a registered {@link HandlerMethodReturnValueHandler} and
 * also supports setting the response status based on a method-level
 * {@code @ResponseStatus} annotation.
 *
 * <p>A {@code null} return value (including void) may be interpreted as the
 * end of request processing in combination with a {@code @ResponseStatus}
 * annotation, a not-modified check condition
 * (see {@link ServletWebRequest#checkNotModified(long)}), or
 * a method argument that provides access to the response stream.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {

	private static final Method CALLABLE_METHOD = ClassUtils.getMethod(Callable.class, "call");
	// 处理方法返回值
	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;


	/**
	 * Creates an instance from the given handler and method.
	 */
	public ServletInvocableHandlerMethod(Object handler, Method method) {
		super(handler, method);
	}

	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public ServletInvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}


	/**设置处理返回值的HandlerMethodReturnValueHandler
	 * Register {@link HandlerMethodReturnValueHandler} instances to use to
	 * handle return values.
	 */
	public void setHandlerMethodReturnValueHandlers(HandlerMethodReturnValueHandlerComposite returnValueHandlers) {
		this.returnValueHandlers = returnValueHandlers;
	}


	/** 它不是复写，但是是对invokeForRequest方法的进一步增强  因为调用目标方法还是靠invokeForRequest
	// 本处是把方法的返回值拿来进一步处理~~~比如状态码之类的
	 * Invoke the method and handle the return value through one of the
	 * configured {@link HandlerMethodReturnValueHandler}s.
	 * @param webRequest the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs "given" arguments matched by type (not resolved)
	 */
	public void invokeAndHandle(ServletWebRequest webRequest, ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {
		 // 执行请求
		Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);
		// 设置HttpServletResponse返回状态码 这里面还是有点意思的  因为@ResponseStatus#code()在父类已经解析了  但是子类才用
		setResponseStatus(webRequest);
		// 重点是这一句话：mavContainer.setRequestHandled(true); 表示该请求已经被处理过了
		if (returnValue == null) {
			// Request的NotModified为true 有@ResponseStatus注解标注 RequestHandled=true 三个条件有一个成立,则设置请求处理完成并返回
			if (isRequestNotModified(webRequest) || getResponseStatus() != null || mavContainer.isRequestHandled()) {
				mavContainer.setRequestHandled(true);
				return;
			}
		}
		// 返回值不为null,@ResponseStatus存在reason 同样设置请求处理完成并返回
		else if (StringUtils.hasText(getResponseStatusReason())) {
			mavContainer.setRequestHandled(true);
			return;
		}
		// 前边都不成立,则设置RequestHandled=false即请求未完成
		// 继续交给HandlerMethodReturnValueHandlerComposite处理
		// 可见@ResponseStatus的优先级还是蛮高的~~~~~
		mavContainer.setRequestHandled(false);
		Assert.state(this.returnValueHandlers != null, "No return value handlers");
		try {
			// 关于对方法返回值的处理，参见：https://blog.csdn.net/f641385712/article/details/90370542
			this.returnValueHandlers.handleReturnValue(
					returnValue, getReturnValueType(returnValue), mavContainer, webRequest);
		}
		catch (Exception ex) {
			if (logger.isTraceEnabled()) {
				logger.trace(getReturnValueHandlingErrorMessage("Error handling return value", returnValue), ex);
			}
			throw ex;
		}
	}

	/** 设置返回的状态码到HttpServletResponse 里面去
	 * Set the response status according to the {@link ResponseStatus} annotation.
	 */
	private void setResponseStatus(ServletWebRequest webRequest) throws IOException {
		HttpStatus status = getResponseStatus();
		if (status == null) {
			return;
		}

		HttpServletResponse response = webRequest.getResponse();
		if (response != null) {
			String reason = getResponseStatusReason();
			if (StringUtils.hasText(reason)) {
				response.sendError(status.value(), reason);
			}
			else {
				response.setStatus(status.value());
			}
		}
		// 设置到request的属性，把响应码给过去。为了在redirect中使用
		
		// To be picked up by RedirectView
		webRequest.getRequest().setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, status);
	}

	/**
	 * Does the given request qualify as "not modified"?
	 * @see ServletWebRequest#checkNotModified(long)
	 * @see ServletWebRequest#checkNotModified(String)
	 */
	private boolean isRequestNotModified(ServletWebRequest webRequest) {
		return webRequest.isNotModified();
	}

	private String getReturnValueHandlingErrorMessage(String message, @Nullable Object returnValue) {
		StringBuilder sb = new StringBuilder(message);
		if (returnValue != null) {
			sb.append(" [type=").append(returnValue.getClass().getName()).append("]");
		}
		sb.append(" [value=").append(returnValue).append("]");
		return getDetailedErrorMessage(sb.toString());
	}

	/**
	 * Create a nested ServletInvocableHandlerMethod subclass that returns the
	 * the given value (or raises an Exception if the value is one) rather than
	 * actually invoking the controller method. This is useful when processing
	 * async return values (e.g. Callable, DeferredResult, ListenableFuture).
	 */
	ServletInvocableHandlerMethod wrapConcurrentResult(Object result) {
		return new ConcurrentResultHandlerMethod(result, new ConcurrentResultMethodParameter(result));
	}


	/**
	 * A nested subclass of {@code ServletInvocableHandlerMethod} that uses a
	 * simple {@link Callable} instead of the original controller as the handler in
	 * order to return the fixed (concurrent) result value given to it. Effectively
	 * "resumes" processing with the asynchronously produced return value.
	 */
	private class ConcurrentResultHandlerMethod extends ServletInvocableHandlerMethod {

		private final MethodParameter returnType;

		public ConcurrentResultHandlerMethod(final Object result, ConcurrentResultMethodParameter returnType) {
			super((Callable<Object>) () -> {
				if (result instanceof Exception) {
					throw (Exception) result;
				}
				else if (result instanceof Throwable) {
					throw new NestedServletException("Async processing failed", (Throwable) result);
				}
				return result;
			}, CALLABLE_METHOD);

			if (ServletInvocableHandlerMethod.this.returnValueHandlers != null) {
				setHandlerMethodReturnValueHandlers(ServletInvocableHandlerMethod.this.returnValueHandlers);
			}
			this.returnType = returnType;
		}

		/**
		 * Bridge to actual controller type-level annotations.
		 */
		@Override
		public Class<?> getBeanType() {
			return ServletInvocableHandlerMethod.this.getBeanType();
		}

		/**
		 * Bridge to actual return value or generic type within the declared
		 * async return type, e.g. Foo instead of {@code DeferredResult<Foo>}.
		 */
		@Override
		public MethodParameter getReturnValueType(@Nullable Object returnValue) {
			return this.returnType;
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.getMethodAnnotation(annotationType);
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.hasMethodAnnotation(annotationType);
		}
	}


	/**
	 * MethodParameter subclass based on the actual return value type or if
	 * that's null falling back on the generic type within the declared async
	 * return type, e.g. Foo instead of {@code DeferredResult<Foo>}.
	 */
	private class ConcurrentResultMethodParameter extends HandlerMethodParameter {

		@Nullable
		private final Object returnValue;

		private final ResolvableType returnType;

		public ConcurrentResultMethodParameter(Object returnValue) {
			super(-1);
			this.returnValue = returnValue;
			this.returnType = (returnValue instanceof ReactiveTypeHandler.CollectedValuesList ?
					((ReactiveTypeHandler.CollectedValuesList) returnValue).getReturnType() :
					ResolvableType.forType(super.getGenericParameterType()).getGeneric());
		}

		public ConcurrentResultMethodParameter(ConcurrentResultMethodParameter original) {
			super(original);
			this.returnValue = original.returnValue;
			this.returnType = original.returnType;
		}

		@Override
		public Class<?> getParameterType() {
			if (this.returnValue != null) {
				return this.returnValue.getClass();
			}
			if (!ResolvableType.NONE.equals(this.returnType)) {
				return this.returnType.resolve(Object.class);
			}
			return super.getParameterType();
		}

		@Override
		public Type getGenericParameterType() {
			return this.returnType.getType();
		}

		@Override
		public <T extends Annotation> boolean hasMethodAnnotation(Class<T> annotationType) {
			// Ensure @ResponseBody-style handling for values collected from a reactive type
			// even if actual return type is ResponseEntity<Flux<T>>
			return (super.hasMethodAnnotation(annotationType) ||
					(annotationType == ResponseBody.class &&
							this.returnValue instanceof ReactiveTypeHandler.CollectedValuesList));
		}

		@Override
		public ConcurrentResultMethodParameter clone() {
			return new ConcurrentResultMethodParameter(this);
		}
	}

}
