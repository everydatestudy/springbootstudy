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

package org.springframework.web.reactive.result.method.annotation;

import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * TODO 注意这个是reactive的，
 * Resolver for method arguments annotated with @{@link RequestParam} from URI
 * query string parameters.
 *
 * <p>
 * This resolver can also be created in default resolution mode in which simple
 * types (int, long, etc.) not annotated with @{@link RequestParam} are also
 * treated as request parameters with the parameter name derived from the
 * argument name.
 *
 * <p>
 * If the method parameter type is {@link Map}, the name specified in the
 * annotation is used to resolve the request parameter String value. The value
 * is then converted to a {@link Map} via type conversion assuming a suitable
 * {@link Converter} has been registered. Or if a request parameter name is not
 * specified the {@link RequestParamMapMethodArgumentResolver} is used instead
 * to provide access to all request parameters in the form of a map.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 * @see RequestParamMapMethodArgumentResolver
 */
public class RequestParamMethodArgumentResolver extends AbstractNamedValueSyncArgumentResolver {
	// 这个参数老重要了：
	// true：表示参数类型是基本类型
	// 参考BeanUtils#isSimpleProperty(什么Enum、Number、Date、URL、包装类型、以上类型的数组类型等等)
	// 如果是基本类型，即使你不写@RequestParam注解，它也是会走进来处理的~~~(这个@PathVariable可不会哟~)
	// fasle：除上以外的。 要想它处理就必须标注注解才行哦，比如List等~
	// 默认值是false

	private final boolean useDefaultResolution;

	/**此构造只有`MvcUriComponentsBuilder`调用了  传入的false
	 * // 传入了ConfigurableBeanFactory ，所以它支持处理占位符${...} 并且支持SpEL了
	// 此构造都在RequestMappingHandlerAdapter里调用，最后都会传入true来Catch-all Case  这种设计挺有意思的
	 * Class constructor with a default resolution mode flag.
	 * 
	 * @param factory              a bean factory used for resolving ${...}
	 *                             placeholder and #{...} SpEL expressions in
	 *                             default values, or {@code null} if default values
	 *                             are not expected to contain expressions
	 * @param registry             for checking reactive type wrappers
	 * @param useDefaultResolution in default resolution mode a method argument that
	 *                             is a simple type, as defined in
	 *                             {@link BeanUtils#isSimpleProperty}, is treated as
	 *                             a request parameter even if it isn't annotated,
	 *                             the request parameter name is derived from the
	 *                             method parameter name.
	 */
	public RequestParamMethodArgumentResolver(@Nullable ConfigurableBeanFactory factory,
			ReactiveAdapterRegistry registry, boolean useDefaultResolution) {

		super(factory, registry);
		this.useDefaultResolution = useDefaultResolution;
	}
	// 此处理器能处理如下Case：
		// 1、所有标注有@RequestParam注解的类型（非Map）/ 注解指定了value值的Map类型（自己提供转换器哦）
		// ======下面都表示没有标注@RequestParam注解了的=======
		// 1、不能标注有@RequestPart注解，否则直接不处理了
		// 2、是上传的request：isMultipartArgument() = true（MultipartFile类型或者对应的集合/数组类型  或者javax.servlet.http.Part对应结合/数组类型）
		// 3、useDefaultResolution=true情况下，"基本类型"也会处理

	@Override
	public boolean supportsParameter(MethodParameter param) {
		if (checkAnnotatedParamNoReactiveWrapper(param, RequestParam.class, this::singleParam)) {
			return true;
		} else if (this.useDefaultResolution) {
			return checkParameterTypeNoReactiveWrapper(param, BeanUtils::isSimpleProperty)
					|| BeanUtils.isSimpleProperty(param.nestedIfOptional().getNestedParameterType());
		}
		return false;
	}

	private boolean singleParam(RequestParam requestParam, Class<?> type) {
		return !Map.class.isAssignableFrom(type) || StringUtils.hasText(requestParam.name());
	}
	// 从这也可以看出：即使木有@RequestParam注解，也是可以创建出一个NamedValueInfo来的
	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		RequestParam ann = parameter.getParameterAnnotation(RequestParam.class);
		return (ann != null ? new RequestParamNamedValueInfo(ann) : new RequestParamNamedValueInfo());
	}

	@Override
	protected Object resolveNamedValue(String name, MethodParameter parameter, ServerWebExchange exchange) {
		List<String> paramValues = exchange.getRequest().getQueryParams().get(name);
		Object result = null;
		//小知识点：getParameter()其实本质是getParameterNames()[0]的效果
		// 强调一遍：?ids=1,2,3 结果是["1,2,3"]（兼容方式，不建议使用。注意：只能是逗号分隔）
		// ?ids=1&ids=2&ids=3  结果是[1,2,3]（标准的传值方式，建议使用）
		// 但是Spring MVC这两种都能用List接收  请务必注意他们的区别~~~
		if (paramValues != null) {
			result = (paramValues.size() == 1 ? paramValues.get(0) : paramValues);
		}
		return result;
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter, ServerWebExchange exchange) {
		String type = parameter.getNestedParameterType().getSimpleName();
		String reason = "Required " + type + " parameter '" + name + "' is not present";
		throw new ServerWebInputException(reason, parameter);
	}

	private static class RequestParamNamedValueInfo extends NamedValueInfo {
		// 请注意这个默认值：如果你不写@RequestParam，那么就会用这个默认值
		// 注意：required = false的哟（若写了注解，required默认可是true，请务必注意区分）
		// 因为不写注解的情况下，若是简单类型参数都是交给此处理器处理的。所以这个机制需要明白
		// 复杂类型（非简单类型）默认是ModelAttributeMethodProcessor处理的

		RequestParamNamedValueInfo() {
			super("", false, ValueConstants.DEFAULT_NONE);
		}

		RequestParamNamedValueInfo(RequestParam annotation) {
			super(annotation.name(), annotation.required(), annotation.defaultValue());
		}
	}

}
