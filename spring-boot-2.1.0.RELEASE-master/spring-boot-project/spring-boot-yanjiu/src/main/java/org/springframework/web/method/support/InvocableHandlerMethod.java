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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * Provides a method for invoking the handler method for a given request after resolving its
 * method argument values through registered {@link HandlerMethodArgumentResolver}s.
 *
 * <p>Argument resolution often requires a {@link WebDataBinder} for data binding or for type
 * conversion. Use the {@link #setDataBinderFactory(WebDataBinderFactory)} property to supply
 * a binder factory to pass to argument resolvers.
 *
 * <p>Use {@link #setHandlerMethodArgumentResolvers} to customize the list of argument resolvers.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class InvocableHandlerMethod extends HandlerMethod {
	// 它额外提供的几个属性，可以看到和数据绑定、数据校验就扯上关系了~~~
	// 用于产生数据绑定器、校验器
	@Nullable
	private WebDataBinderFactory dataBinderFactory;

	// HandlerMethodArgumentResolver用于入参的解析
	private HandlerMethodArgumentResolverComposite argumentResolvers = new HandlerMethodArgumentResolverComposite();
	// 用于获取形参名
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public InvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}

	/**
	 * Create an instance from a bean instance and a method.
	 */
	public InvocableHandlerMethod(Object bean, Method method) {
		super(bean, method);
	}

	/**
	 * Construct a new handler method with the given bean instance, method name and parameters.
	 * @param bean the object bean
	 * @param methodName the method name
	 * @param parameterTypes the method parameter types
	 * @throws NoSuchMethodException when the method cannot be found
	 */
	public InvocableHandlerMethod(Object bean, String methodName, Class<?>... parameterTypes)
			throws NoSuchMethodException {

		super(bean, methodName, parameterTypes);
	}


	/**
	 * Set the {@link WebDataBinderFactory} to be passed to argument resolvers allowing them to create
	 * a {@link WebDataBinder} for data binding and type conversion purposes.
	 * @param dataBinderFactory the data binder factory.
	 */
	public void setDataBinderFactory(WebDataBinderFactory dataBinderFactory) {
		this.dataBinderFactory = dataBinderFactory;
	}

	/**
	 * Set {@link HandlerMethodArgumentResolver}s to use to use for resolving method argument values.
	 */
	public void setHandlerMethodArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
		this.argumentResolvers = argumentResolvers;
	}

	/**
	 * Set the ParameterNameDiscoverer for resolving parameter names when needed
	 * (e.g. default request attribute name).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}


	/**
	 * Invoke the method after resolving its argument values in the context of the given request.
	 * <p>Argument values are commonly resolved through {@link HandlerMethodArgumentResolver}s.
	 * The {@code providedArgs} parameter however may supply argument values to be used directly,
	 * i.e. without argument resolution. Examples of provided argument values include a
	 * {@link WebDataBinder}, a {@link SessionStatus}, or a thrown exception instance.
	 * Provided argument values are checked before argument resolvers.
	 * @param request the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs "given" arguments matched by type, not resolved
	 * @return the raw value returned by the invoked method
	 * @throws Exception raised if no suitable argument resolver can be found,
	 * or if the method raised an exception
	 */
	// 省略构造函数 全部使用super的
	// 它自己的三大属性都使用set方法设置进来~~~并且没有提供get方法
	// 也就是说：它自己内部使用就行了~~~

	// 在给定请求的上下文中解析方法的参数值后调用该方法。 也就是说：方法入参里就能够自动使用请求域（包括path里的，requestParam里的、以及常规对象如HttpSession这种）
	// 解释下providedArgs作用：调用者可以传进来，然后直接doInvoke()的时候原封不动的使用它
	//（弥补了请求域没有所有对象的不足，毕竟有些对象是用户自定义的嘛~）

	@Nullable
	public Object invokeForRequest(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {
		// 虽然它是最重要的方法，但是此处不讲，因为核心原来还是`HandlerMethodArgumentResolver`
				// 它只是把解析好的放到对应位置里去~~~
				// 说明：这里传入了ParameterNameDiscoverer，它是能够获取到形参名的。
				// 这就是为何注解里我们不写value值，通过形参名字来匹配也是ok的核心原因~
 
		Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
		if (logger.isTraceEnabled()) {
			logger.trace("Invoking '" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
					"' with arguments " + Arrays.toString(args));
		}
		Object returnValue = doInvoke(args);
		if (logger.isTraceEnabled()) {
			logger.trace("Method [" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
					"] returned [" + returnValue + "]");
		}
		return returnValue;
	}

	/**
	 * Get the method argument values for the current request.
	 */
	private Object[] getMethodArgumentValues(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {
		/**
		 * 获取到所有的参数名
		 */
		MethodParameter[] parameters = getMethodParameters();
		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
			/**
			 * 下面这行代码，看注释的意思是：从提供的参数列表中获取对应的值，但是从前面的调用链中会发现，入参的providedArgs是null
			 */
			args[i] = resolveProvidedArgument(parameter, providedArgs);
			if (args[i] != null) {
				continue;
			}
			/**
			 * 遍历所有的参数解析器，看哪个解析器可以解析，如果参数解析器返回true，就表示可以解析
			 * 如果我们要扩展参数解析器，就需要看下这里的逻辑
			 * 需要学习下这里的argumentResolvers是在哪里赋值的
			 */
			if (this.argumentResolvers.supportsParameter(parameter)) {
				try {
					/**
					 * 这里就是用parameter对应的解析器去解析该参数
					 *   // 参数解析器解析HTTP报文到参数
					 */
					args[i] = this.argumentResolvers.resolveArgument(
							parameter, mavContainer, request, this.dataBinderFactory);
					continue;
				}
				catch (Exception ex) {
					if (logger.isDebugEnabled()) {
						logger.debug(getArgumentResolutionErrorMessage("Failed to resolve", i), ex);
					}
					throw ex;
				}
			}
			if (args[i] == null) {
				throw new IllegalStateException("Could not resolve method parameter at index " +
						parameter.getParameterIndex() + " in " + parameter.getExecutable().toGenericString() +
						": " + getArgumentResolutionErrorMessage("No suitable resolver for", i));
			}
		}
		return args;
	}

	private String getArgumentResolutionErrorMessage(String text, int index) {
		Class<?> paramType = getMethodParameters()[index].getParameterType();
		return text + " argument " + index + " of type '" + paramType.getName() + "'";
	}

	/**
	 * Attempt to resolve a method parameter from the list of provided argument values.
	 */
	@Nullable
	private Object resolveProvidedArgument(MethodParameter parameter, @Nullable Object... providedArgs) {
		if (providedArgs == null) {
			return null;
		}
		for (Object providedArg : providedArgs) {
			if (parameter.getParameterType().isInstance(providedArg)) {
				return providedArg;
			}
		}
		return null;
	}


	/**
	 * Invoke the handler method with the given argument values.
	 */
	protected Object doInvoke(Object... args) throws Exception {
		ReflectionUtils.makeAccessible(getBridgedMethod());
		try {
			return getBridgedMethod().invoke(getBean(), args);
		}
		catch (IllegalArgumentException ex) {
			assertTargetBean(getBridgedMethod(), getBean(), args);
			String text = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
			throw new IllegalStateException(getInvocationErrorMessage(text, args), ex);
		}
		catch (InvocationTargetException ex) {
			// Unwrap for HandlerExceptionResolvers ...
			Throwable targetException = ex.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			else if (targetException instanceof Error) {
				throw (Error) targetException;
			}
			else if (targetException instanceof Exception) {
				throw (Exception) targetException;
			}
			else {
				String text = getInvocationErrorMessage("Failed to invoke handler method", args);
				throw new IllegalStateException(text, targetException);
			}
		}
	}

	/**
	 * Assert that the target bean class is an instance of the class where the given
	 * method is declared. In some cases the actual controller instance at request-
	 * processing time may be a JDK dynamic proxy (lazy initialization, prototype
	 * beans, and others). {@code @Controller}'s that require proxying should prefer
	 * class-based proxy mechanisms.
	 */
	private void assertTargetBean(Method method, Object targetBean, Object[] args) {
		Class<?> methodDeclaringClass = method.getDeclaringClass();
		Class<?> targetBeanClass = targetBean.getClass();
		if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
			String text = "The mapped handler method class '" + methodDeclaringClass.getName() +
					"' is not an instance of the actual controller bean class '" +
					targetBeanClass.getName() + "'. If the controller requires proxying " +
					"(e.g. due to @Transactional), please use class-based proxying.";
			throw new IllegalStateException(getInvocationErrorMessage(text, args));
		}
	}

	private String getInvocationErrorMessage(String text, Object[] resolvedArgs) {
		StringBuilder sb = new StringBuilder(getDetailedErrorMessage(text));
		sb.append("Resolved arguments: \n");
		for (int i = 0; i < resolvedArgs.length; i++) {
			sb.append("[").append(i).append("] ");
			if (resolvedArgs[i] == null) {
				sb.append("[null] \n");
			}
			else {
				sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
				sb.append("[value=").append(resolvedArgs[i]).append("]\n");
			}
		}
		return sb.toString();
	}

	/**
	 * Adds HandlerMethod details such as the bean type and method signature to the message.
	 * @param text error message to append the HandlerMethod details to
	 */
	protected String getDetailedErrorMessage(String text) {
		StringBuilder sb = new StringBuilder(text).append("\n");
		sb.append("HandlerMethod details: \n");
		sb.append("Controller [").append(getBeanType().getName()).append("]\n");
		sb.append("Method [").append(getBridgedMethod().toGenericString()).append("]\n");
		return sb.toString();
	}

}
