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

package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ExceptionDepthComparator;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;
//对于本类的功能，可总结如下：
//
//找到指定Class类（可能是Controller本身，也可能是@ControllerAdvice）里面所有标注有@ExceptionHandler的方法们
//同一个Class内，不能出现同一个（注意理解同一个的含义）异常类型被多个Method处理的情况，否则抛出异常：Ambiguous @ExceptionHandler method mapped for ...
//1. 相同异常类型处在不同的Class内的方法上是可以的，比如常见的一个在Controller内，一个在@ControllerAdvice内~
//提供缓存：
//1. mappedMethods：每种异常对应的处理方法（直接映射代码上书写的异常-方法映射）
//2. exceptionLookupCache：经过按照深度逻辑精确匹配上的Method方法
//既能处理本身的异常，也能够处理getCause()导致的异常
//ExceptionDepthComparator的匹配逻辑是按照深度匹配。比如发生的是NullPointerException，但是声明的异常有Throwable和Exception，这是它会根据异常的最近继承关系找到继承深度最浅的那个异常，即Exception。
//————————————————
//版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/f641385712/article/details/102294670
/**
 * Discovers {@linkplain ExceptionHandler @ExceptionHandler} methods in a given class,
 * including all of its superclasses, and helps to resolve a given {@link Exception}
 * to the exception types supported by a given {@link Method}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ExceptionHandlerMethodResolver {

	/**
	 * A filter for selecting {@code @ExceptionHandler} methods.
	 */
	public static final MethodFilter EXCEPTION_HANDLER_METHODS = method ->
			(AnnotationUtils.findAnnotation(method, ExceptionHandler.class) != null);

			// 两个缓存：key：异常类型   value：目标方法Method
	private final Map<Class<? extends Throwable>, Method> mappedMethods = new HashMap<>(16);

	private final Map<Class<? extends Throwable>, Method> exceptionLookupCache = new ConcurrentReferenceHashMap<>(16);


	/**唯一构造函数
	// detectExceptionMappings：传入method，找到这个Method可以处理的所有的异常类型们（注意此方法的逻辑）
	// addExceptionMapping：把异常类型和Method缓存进mappedMethods里
	 * A constructor that finds {@link ExceptionHandler} methods in the given type.
	 * @param handlerType the type to introspect
	 */
	public ExceptionHandlerMethodResolver(Class<?> handlerType) {
		for (Method method : MethodIntrospector.selectMethods(handlerType, EXCEPTION_HANDLER_METHODS)) {
			for (Class<? extends Throwable> exceptionType : detectExceptionMappings(method)) {
				addExceptionMapping(exceptionType, method);
			}
		}
	}


	/**
	 * Extract exception mappings from the {@code @ExceptionHandler} annotation first,
	 * and then as a fallback from the method signature itself.
	 */
	// 找到此Method能够处理的所有的异常类型
		// 1、detectAnnotationExceptionMappings：本方法或者父类的方法上标注有ExceptionHandler注解，然后读取出其value值就是它能处理的异常们
		// 2、若value值木有指定，那所有的方法入参们的异常类型，就是此方法能够处理的所有异常们
		// 3、若最终还是空，那就抛出异常：No exception types mapped to " + method
	@SuppressWarnings("unchecked")
	private List<Class<? extends Throwable>> detectExceptionMappings(Method method) {
		List<Class<? extends Throwable>> result = new ArrayList<>();
		detectAnnotationExceptionMappings(method, result);
		if (result.isEmpty()) {
			for (Class<?> paramType : method.getParameterTypes()) {
				if (Throwable.class.isAssignableFrom(paramType)) {
					result.add((Class<? extends Throwable>) paramType);
				}
			}
		}
		if (result.isEmpty()) {
			throw new IllegalStateException("No exception types mapped to " + method);
		}
		return result;
	}

	protected void detectAnnotationExceptionMappings(Method method, List<Class<? extends Throwable>> result) {
		ExceptionHandler ann = AnnotationUtils.findAnnotation(method, ExceptionHandler.class);
		Assert.state(ann != null, "No ExceptionHandler annotation");
		result.addAll(Arrays.asList(ann.value()));
	}
	// 对于添加方法一样有一句值得说的：
		// 若不同的Method表示可以处理同一个异常，那是不行的："Ambiguous @ExceptionHandler method mapped for [" 
		// 注意：此处必须是同一个异常（比如Exception和RuntimeException不属于同一个...）
 
	private void addExceptionMapping(Class<? extends Throwable> exceptionType, Method method) {
		Method oldMethod = this.mappedMethods.put(exceptionType, method);
		if (oldMethod != null && !oldMethod.equals(method)) {
			throw new IllegalStateException("Ambiguous @ExceptionHandler method mapped for [" +
					exceptionType + "]: {" + oldMethod + ", " + method + "}");
		}
	}

	/**
	 * Whether the contained type has any exception mappings.
	 */
	public boolean hasExceptionMappings() {
		return !this.mappedMethods.isEmpty();
	}

	/**
	 * Find a {@link Method} to handle the given exception.
	 * Use {@link ExceptionDepthComparator} if more than one match is found.
	 * @param exception the exception
	 * @return a Method to handle the exception, or {@code null} if none found
	 */
	@Nullable
	public Method resolveMethod(Exception exception) {
		return resolveMethodByThrowable(exception);
	}

	/**
	 * Find a {@link Method} to handle the given Throwable.
	 * Use {@link ExceptionDepthComparator} if more than one match is found.
	 * @param exception the exception
	 * @return a Method to handle the exception, or {@code null} if none found
	 * @since 5.0
	 */
	@Nullable
	public Method resolveMethodByThrowable(Throwable exception) {
		Method method = resolveMethodByExceptionType(exception.getClass());
		if (method == null) {
			Throwable cause = exception.getCause();
			if (cause != null) {
				method = resolveMethodByExceptionType(cause.getClass());
			}
		}
		return method;
	}
	//1、先去exceptionLookupCache找，若匹配上了直接返回
		// 2、再去mappedMethods这个缓存里找。很显然可能匹配上多个，那就用ExceptionDepthComparator排序匹配到一个最为合适的
		// 3、匹配上后放进缓存`exceptionLookupCache`，所以下次进来就不需要再次匹配了，这就是缓存的效果
		// ExceptionDepthComparator的基本理论上：精确匹配优先（按照深度比较）
 
	/**
	 * Find a {@link Method} to handle the given exception type. This can be
	 * useful if an {@link Exception} instance is not available (e.g. for tools).
	 * @param exceptionType the exception type
	 * @return a Method to handle the exception, or {@code null} if none found
	 */
	@Nullable
	public Method resolveMethodByExceptionType(Class<? extends Throwable> exceptionType) {
		Method method = this.exceptionLookupCache.get(exceptionType);
		if (method == null) {
			method = getMappedMethod(exceptionType);
			this.exceptionLookupCache.put(exceptionType, method);
		}
		return method;
	}

	/**
	 * Return the {@link Method} mapped to the given exception type, or {@code null} if none.
	 */
	@Nullable
	private Method getMappedMethod(Class<? extends Throwable> exceptionType) {
		List<Class<? extends Throwable>> matches = new ArrayList<>();
		for (Class<? extends Throwable> mappedException : this.mappedMethods.keySet()) {
			if (mappedException.isAssignableFrom(exceptionType)) {
				matches.add(mappedException);
			}
		}
		if (!matches.isEmpty()) {
			matches.sort(new ExceptionDepthComparator(exceptionType));
			return this.mappedMethods.get(matches.get(0));
		}
		else {
			return null;
		}
	}

}
