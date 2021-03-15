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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

/**Spring MVC对@SessionAttributes的处理操作入口，是在ModelFactory.initModel()方法里会对@SessionAttributes的注解进行解析、处理，然后方法完成之后也会对它进行属性同步。
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {

	private static final Log logger = LogFactory.getLog(ModelFactory.class);

	private final List<ModelMethod> modelMethods = new ArrayList<>();

	private final WebDataBinderFactory dataBinderFactory;

	private final SessionAttributesHandler sessionAttributesHandler;


	/**
	 * Create a new instance with the given {@code @ModelAttribute} methods.
	 * @param handlerMethods the {@code @ModelAttribute} methods to invoke
	 * @param binderFactory for preparation of {@link BindingResult} attributes
	 * @param attributeHandler for access to session attributes
	 */
	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {
		// 把InvocableHandlerMethod转为内部类ModelMethod
		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				this.modelMethods.add(new ModelMethod(handlerMethod));
			}
		}
		this.dataBinderFactory = binderFactory;
		this.sessionAttributesHandler = attributeHandler;
	}


	/**
	 * Populate the model in the following order:
	 * <ol>
	 * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
	 * <li>Invoke {@code @ModelAttribute} methods
	 * <li>Find {@code @ModelAttribute} method arguments also listed as
	 * {@code @SessionAttributes} and ensure they're present in the model raising
	 * an exception if necessary.
	 * </ol>
	 * @param request the current request
	 * @param container a container with the model to be initialized
	 * @param handlerMethod the method for which the model is initialized
	 * @throws Exception may arise from {@code @ModelAttribute} methods
	 */
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {
		// 先拿到sessionAttr里所有的属性们（首次进来肯定木有，但同一个session第二次进来就有了）
		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
		// 和当前请求中 已经有的model合并属性信息
				// 注意：sessionAttributes中只有当前model不存在的属性，它才会放进去
		container.mergeAttributes(sessionAttributes);
		//执行@ModelAttribute注解的方法
		// 此方法重要：调用模型属性方法来填充模型  这里ModelAttribute会生效
		// 关于@ModelAttribute的内容  我放到了这里：https://blog.csdn.net/f641385712/article/details/98260361
		// 总之：完成这步之后 Model就有值了~~~~

		invokeModelAttributeMethods(request, container);
		// 最后，最后，最后还做了这么一步操作~~~
		// findSessionAttributeArguments的作用：把@ModelAttribute的入参也列入SessionAttributes（非常重要） 详细见下文
		// 这里一定要掌握：因为使用中的坑坑经常是因为没有理解到这块逻辑

 
		for (String name : findSessionAttributeArguments(handlerMethod)) {
			if (!container.containsAttribute(name)) {
				Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				container.addAttribute(name, value);
			}
		}
	}

	/**调用标注有注解的方法来填充Model
	 * Invoke model attribute methods to populate the model.
	 * Attributes are added only if not already present in the model.
	 */
	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {
		// modelMethods是构造函数进来的  一个个的处理吧
		while (!this.modelMethods.isEmpty()) {
			// getNextModelMethod：通过next其实能看出 执行是有顺序的  拿到一个可执行的InvocableHandlerMethod
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
			//判断方法是否被@ModelAttribute注解
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			Assert.state(ann != null, "No ModelAttribute annotation");
			if (container.containsAttribute(ann.name())) {
				if (!ann.binding()) {// 若binding是false  就禁用掉此name的属性  让不支持绑定了  此方法也处理完成
					container.setBindingDisabled(ann.name());
				}
				continue;
			}
			//执行被@ModelAttribute注解的方法
			Object returnValue = modelMethod.invokeForRequest(request, container);
			//返回值放到mavContainer
			if (!modelMethod.isVoid()){
				// returnValueName的生成规则 上文有解释过  本处略
				String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
				if (!ann.binding()) {
					container.setBindingDisabled(returnValueName);
				}
				//在个判断是个小细节：只有容器内不存在此属性，才会放进去   因此并不会有覆盖的效果哦~~~
				// 所以若出现同名的  请自己控制好顺序吧
				if (!container.containsAttribute(returnValueName)) {
					container.addAttribute(returnValueName, returnValue);
				}
			}
		}
	}
	// 拿到下一个标注有此注解方法~~~
	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		// 每次都会遍历所有的构造进来的modelMethods
		for (ModelMethod modelMethod : this.modelMethods) {
			// dependencies：表示该方法的所有入参中 标注有@ModelAttribute的入参们
			// checkDependencies的作用是：所有的dependencies依赖们必须都是container已经存在的属性，才会进到这里来
			if (modelMethod.checkDependencies(container)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Selected @ModelAttribute method " + modelMethod);
				}
				// 找到一个 就移除一个
				// 这里使用的是List的remove方法，不用担心并发修改异常？？？ 哈哈其实不用担心的  小伙伴能知道为什么吗？？
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}// 若并不是所有的依赖属性Model里都有，那就拿第一个吧~~~~
		ModelMethod modelMethod = this.modelMethods.get(0);
		if (logger.isTraceEnabled()) {
			logger.trace("Selected @ModelAttribute method (not present: " +
					modelMethod.getUnresolvedDependencies(container)+ ") " + modelMethod);
		}
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}

	/**把@ModelAttribute标注的入参也列入SessionAttributes 放进sesson里（非常重要）
	// 这个动作是很多开发者都忽略了的
	 * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
	 */
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		List<String> result = new ArrayList<>();
		// 遍历所有的方法参数
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			// 只有参数里标注了@ModelAttribute的才会进入继续解析~~~
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				String name = getNameForParameter(parameter);
				Class<?> paramType = parameter.getParameterType();
				if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
					result.add(name);
				}
			}
		}
		return result;
	}

	/**
	 * Promote model attributes listed as {@code @SessionAttributes} to the session.
	 * Add {@link BindingResult} attributes where necessary.
	 * @param request the current request
	 * @param container contains the model to update
	 * @throws Exception if creating BindingResult attributes fails
	 */
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		ModelMap defaultModel = container.getDefaultModel();
		if (container.getSessionStatus().isComplete()){
			this.sessionAttributesHandler.cleanupAttributes(request);
		}
		else {
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			updateBindingResult(request, defaultModel);
		}
	}

	/**
	 * Add {@link BindingResult} attributes to the model for attributes that require it.
	 */
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		List<String> keyNames = new ArrayList<>(model.keySet());
		for (String name : keyNames) {
			Object value = model.get(name);
			if (value != null && isBindingCandidate(name, value)) {
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
				if (!model.containsAttribute(bindingResultKey)) {
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}

	/**
	 * Whether the given attribute requires a {@link BindingResult} in the model.
	 */
	private boolean isBindingCandidate(String attributeName, Object value) {
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return false;
		}

		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}

	// 静态方法：决定了parameter的名字  它是public的，因为ModelAttributeMethodProcessor里也有使用
		// 请注意：这里不是MethodParameter.getParameterName()获取到的形参名字，而是有自己的一套规则的

		// @ModelAttribute指定了value值就以它为准，否则就是类名的首字母小写（当然不同类型不一样，下面有给范例）

	/**
	 * Derive the model attribute name for the given method parameter based on
	 * a {@code @ModelAttribute} parameter annotation (if present) or falling
	 * back on parameter type based conventions.
	 * @param parameter a descriptor for the method parameter
	 * @return the derived name
	 * @see Conventions#getVariableNameForParameter(MethodParameter)
	 */
	public static String getNameForParameter(MethodParameter parameter) {
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}

	/** 关于方法这块的处理逻辑，和上差不多，主要是返回类型和实际类型的区分
	// 比如List<String>它对应的名是：stringList。即使你的返回类型是Object~~~
	 * Derive the model attribute name for the given return value based on:
	 * <ol>
	 * <li>the method {@code ModelAttribute} annotation value
	 * <li>the declared return type if it is more specific than {@code Object}
	 * <li>the actual return value type
	 * </ol>
	 * @param returnValue the value returned from a method invocation
	 * @param returnType a descriptor for the return type of the method
	 * @return the derived name (never {@code null} or empty String)
	 */
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		else {
			Method method = returnType.getMethod();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	private static class ModelMethod {

		private final InvocableHandlerMethod handlerMethod;

		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			// 把方法入参中所有标注了@ModelAttribute了的Name都搜集进来
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		public List<String> getUnresolvedDependencies(ModelAndViewContainer mavContainer) {
			List<String> result = new ArrayList<>(this.dependencies.size());
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					result.add(name);
				}
			}
			return result;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
