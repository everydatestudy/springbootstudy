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

package org.springframework.web.servlet.mvc.method.annotation;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;

/**
 * This return value handler is intended to be ordered after all others as it
 * attempts to handle _any_ return value type (i.e. returns {@code true} for
 * all return types).
 *
 * <p>The return value is handled either with a {@link ModelAndViewResolver}
 * or otherwise by regarding it as a model attribute if it is a non-simple
 * type. If neither of these succeeds (essentially simple type other than
 * String), {@link UnsupportedOperationException} is raised.
 *
 * <p><strong>Note:</strong> This class is primarily needed to support
 * {@link ModelAndViewResolver}, which unfortunately cannot be properly
 * adapted to the {@link HandlerMethodReturnValueHandler} contract since the
 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method
 * cannot be implemented. Hence {@code ModelAndViewResolver}s are limited
 * to always being invoked at the end after all other return value
 * handlers have been given a chance. It is recommended to re-implement
 * a {@code ModelAndViewResolver} as {@code HandlerMethodReturnValueHandler},
 * which also provides better access to the return type and method information.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ModelAndViewResolverMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	@Nullable
	private final List<ModelAndViewResolver> mavResolvers;
	// 持有modelAttributeProcessor 的引用，所以是对它的一个加强~~~~
	private final ModelAttributeMethodProcessor modelAttributeProcessor = new ModelAttributeMethodProcessor(true);


	/**
	 * Create a new instance.
	 */
	public ModelAndViewResolverMethodReturnValueHandler(@Nullable List<ModelAndViewResolver> mavResolvers) {
		this.mavResolvers = mavResolvers;
	}


	/**
	 * Always returns {@code true}. See class-level note.
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return true;
	}

	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
		// 若我们配置了处理器，那就一个一个的处理吧~~~~~
				// 当然，最终真正处理的可能只有一个，这里也是责任链的形式~~~~一般会用if判断
		if (this.mavResolvers != null) {
			for (ModelAndViewResolver mavResolver : this.mavResolvers) {
				Class<?> handlerType = returnType.getContainingClass();
				Method method = returnType.getMethod();
				Assert.state(method != null, "No handler method");
				ExtendedModelMap model = (ExtendedModelMap) mavContainer.getModel();
				// 处理ModelAndView，若返回的不是ModelAndViewResolver.UNRESOLVED
				// 那就说明它处理了，那就return掉~~~~ 逻辑还是很简单的~~~
				ModelAndView mav = mavResolver.resolveModelAndView(method, handlerType, returnValue, model, webRequest);
				// 这一步相当于如果我们自定义了model，会把它的属性合并进来~~~
				// 大多数情况下，我们外部直接操作ExtendedModelMap model这个对象即可
				// 当然你也可以不指定view，自己写成同@ResponseBody一样的效果也是阔仪的
				if (mav != ModelAndViewResolver.UNRESOLVED) {
					mavContainer.addAllAttributes(mav.getModel());
					mavContainer.setViewName(mav.getViewName());
					if (!mav.isReference()) {
						mavContainer.setView(mav.getView());
					}
					return;
				}
			}
		}

		// No suitable ModelAndViewResolver...
		if (this.modelAttributeProcessor.supportsReturnType(returnType)) {
			this.modelAttributeProcessor.handleReturnValue(returnValue, returnType, mavContainer, webRequest);
		}
		else {
			throw new UnsupportedOperationException("Unexpected return type: " +
					returnType.getParameterType().getName() + " in method: " + returnType.getMethod());
		}
	}

}
