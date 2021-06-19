/**
 * Copyright 2012-2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;
import feign.Request.HttpMethod;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defines what annotations and values are valid on interfaces.
 */
public interface Contract {

	/**
	 * Called to parse the methods in the class that are linked to HTTP requests.
	 *
	 * @param targetType {@link feign.Target#type() type} of the Feign interface.
	 */
	// TODO: break this and correct spelling at some point
	// 此方法来解析类中链接到HTTP请求的方法：提取有效信息到元信息存储
	// MethodMetadata：方法各种元信息，包括但不限于
	// 返回值类型returnType
	// 请求参数、请求参数的index、名称
	// url、查询参数、请求body体等等等等
	List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType);

	abstract class BaseContract implements Contract {
//	  从此处可以清楚的看到，Feign虽然基于接口实现，但它对接口是有要求的：
//
//	  不能是泛型接口
//	  接口最多只能有一个父接口（当然可以没有父接口）
//	  然后他会处理所有的接口方法，包含父接口的。但不包含接口里的默认方法、私有方法、静态方法等，也排除掉Object里的方法。
//	  而对方法的到元数据的解析，落在了parseAndValidateMetadata()这个protected方法上
		@Override
		public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
			// 这些检查挺有意思的
			// 1、类上不能存在任何一个泛型变量
			checkState(targetType.getTypeParameters().length == 0, "Parameterized types unsupported: %s",
					targetType.getSimpleName());
			// 2、接口最多最多只能有一个父接口
			checkState(targetType.getInterfaces().length <= 1, "Only single inheritance supported: %s",
					targetType.getSimpleName());
			if (targetType.getInterfaces().length == 1) {
				checkState(targetType.getInterfaces()[0].getInterfaces().length == 0,
						"Only single-level inheritance supported: %s", targetType.getSimpleName());
			}
			// 对该类所有的方法进行解析：包装成一个MethodMetadata
			// getMethods表示本类 + 父类的public方法
			// 因为是接口，所有肯定都是public的（当然Java8支持private、default、static等）
			Map<String, MethodMetadata> result = new LinkedHashMap<String, MethodMetadata>();
			for (Method method : targetType.getMethods()) {
				if (method.getDeclaringClass() == Object.class || (method.getModifiers() & Modifier.STATIC) != 0
						|| Util.isDefault(method)) {
					continue;
				}
				// 排除掉Object的方法、static方法、default方法等
				// parseAndValidateMetadata是本类的一个protected方法
				MethodMetadata metadata = parseAndValidateMetadata(targetType, method);
				checkState(!result.containsKey(metadata.configKey()), "Overrides unsupported: %s",
						metadata.configKey());
				result.put(metadata.configKey(), metadata);
			}
			return new ArrayList<>(result.values());
		}

		/**
		 * @deprecated use {@link #parseAndValidateMetadata(Class, Method)} instead.
		 */
		@Deprecated
		public MethodMetadata parseAndValidatateMetadata(Method method) {
			return parseAndValidateMetadata(method.getDeclaringClass(), method);
		}

		/**
		 * Called indirectly by {@link #parseAndValidatateMetadata(Class)}.
		 */
		protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
			MethodMetadata data = new MethodMetadata();
			// 方法返回类型是支持泛型的
			data.returnType(Types.resolve(targetType, targetType, method.getGenericReturnType()));
			// 这里使用了Feign的一个工具方法，来生成configKey，不用过于了解细节，简单的说就是尽量唯一
			data.configKey(Feign.configKey(targetType, method));
			 // 这一步很重要：处理接口上的注解。并且处理了父接口哦
			  // 这就是为何你父接口上的注解，子接口里也生效的原因哦~~~
			  // processAnnotationOnClass()是个abstract方法，交给子类去实现（毕竟注解是可以扩展的嘛）
			if (targetType.getInterfaces().length == 1) {
				processAnnotationOnClass(data, targetType.getInterfaces()[0]);
			}
			processAnnotationOnClass(data, targetType);
			// 处理标注在方法上的所有注解
			  // 若子接口override了父接口的方法，注解请以子接口的为主，忽略父接口方法
			for (Annotation methodAnnotation : method.getAnnotations()) {
				processAnnotationOnMethod(data, methodAnnotation, method);
			}
			// 简单的说：处理完方法上的注解后，必须已经知道到底是GET or POST 或者其它了
			checkState(data.template().method() != null,
					"Method %s not annotated with HTTP method type (ex. GET, POST)", method.getName());
			// 方法参数，支持泛型类型的。如List<String>这种...
			Class<?>[] parameterTypes = method.getParameterTypes();
			Type[] genericParameterTypes = method.getGenericParameterTypes();
			 // 注解是个二维数组...
			Annotation[][] parameterAnnotations = method.getParameterAnnotations();
			int count = parameterAnnotations.length;
		      // 一个注解一个注解的处理
			for (int i = 0; i < count; i++) {
				boolean isHttpAnnotation = false;
				if (parameterAnnotations[i] != null) {
					isHttpAnnotation = processAnnotationsOnParameter(data, parameterAnnotations[i], i);
				}
				// 方法参数若存在URI类型的参数，那url就以它为准，并不使用全局的了
				if (parameterTypes[i] == URI.class) {
					data.urlIndex(i);
				} else if (!isHttpAnnotation) {
					checkState(data.formParams().isEmpty(), "Body parameters cannot be used with form parameters.");
					checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
					data.bodyIndex(i);
					data.bodyType(Types.resolve(targetType, targetType, genericParameterTypes[i]));
				}
			}
			// 校验body：
			// 1、body参数不能用作form表单的parameters
			// 2、Body parameters不能太多
			if (data.headerMapIndex() != null) {
				checkMapString("HeaderMap", parameterTypes[data.headerMapIndex()],
						genericParameterTypes[data.headerMapIndex()]);
			}

			if (data.queryMapIndex() != null) {
				if (Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()])) {
					checkMapKeys("QueryMap", genericParameterTypes[data.queryMapIndex()]);
				}
			}

			return data;
		}

		private static void checkMapString(String name, Class<?> type, Type genericType) {
			checkState(Map.class.isAssignableFrom(type), "%s parameter must be a Map: %s", name, type);
			checkMapKeys(name, genericType);
		}

		private static void checkMapKeys(String name, Type genericType) {
			Class<?> keyClass = null;

			// assume our type parameterized
			if (ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
				Type[] parameterTypes = ((ParameterizedType) genericType).getActualTypeArguments();
				keyClass = (Class<?>) parameterTypes[0];
			} else if (genericType instanceof Class<?>) {
				// raw class, type parameters cannot be inferred directly, but we can scan any
				// extended
				// interfaces looking for any explict types
				Type[] interfaces = ((Class) genericType).getGenericInterfaces();
				if (interfaces != null) {
					for (Type extended : interfaces) {
						if (ParameterizedType.class.isAssignableFrom(extended.getClass())) {
							// use the first extended interface we find.
							Type[] parameterTypes = ((ParameterizedType) extended).getActualTypeArguments();
							keyClass = (Class<?>) parameterTypes[0];
							break;
						}
					}
				}
			}

			if (keyClass != null) {
				checkState(String.class.equals(keyClass), "%s key must be a String: %s", name,
						keyClass.getSimpleName());
			}
		}

		/**按照从上至下流程解析每一个方法上的注解信息，抽象方法留给子类具体实现：

		processAnnotationOnClass：
		processAnnotationOnMethod：
		processAnnotationsOnParameter：
		这三个抽象方法非常的公用，决定了识别哪些注解、解析哪些注解，因此特别适合第三方扩展。
		 显然，Feign内置的默认实现实现了这些接口，就连Spring MVC的扩展SpringMvcContract也是通过继承它来实现支持@RequestMapping等注解的。
		 * Called by parseAndValidateMetadata twice, first on the declaring class, then
		 * on the target type (unless they are the same).
		 *
		 * @param data metadata collected so far relating to the current java method.
		 * @param clz  the class to process
		 */
		// 支持注解：@Headers
		protected abstract void processAnnotationOnClass(MethodMetadata data, Class<?> clz);

		/**
		 * @param data       metadata collected so far relating to the current java
		 *                   method.
		 * @param annotation annotations present on the current method annotation.
		 * @param method     method currently being processed.
		 */
		// 支出注解：@RequestLine、@Body、@Headers
		protected abstract void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method);

		/**
		 * @param data        metadata collected so far relating to the current java
		 *                    method.
		 * @param annotations annotations present on the current parameter annotation.
		 * @param paramIndex  if you find a name in {@code annotations}, call
		 *                    {@link #nameParam(MethodMetadata, String, int)} with this
		 *                    as the last parameter.
		 * @return true if you called {@link #nameParam(MethodMetadata, String, int)}
		 *         after finding an http-relevant annotation.
		 */
		// 支持注解：@Param、@QueryMap、@HeaderMap等
		protected abstract boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations,
				int paramIndex);

		/**
		 * links a parameter name to its index in the method signature.
		 */
		protected void nameParam(MethodMetadata data, String name, int i) {
			Collection<String> names = data.indexToName().containsKey(i) ? data.indexToName().get(i)
					: new ArrayList<String>();
			names.add(name);
			data.indexToName().put(i, names);
		}
	}

	class Default extends BaseContract {

		static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$");

		@Override
		protected void processAnnotationOnClass(MethodMetadata data, Class<?> targetType) {
			if (targetType.isAnnotationPresent(Headers.class)) {
				String[] headersOnType = targetType.getAnnotation(Headers.class).value();
				checkState(headersOnType.length > 0, "Headers annotation was empty on type %s.", targetType.getName());
				Map<String, Collection<String>> headers = toMap(headersOnType);
				headers.putAll(data.template().headers());
				data.template().headers(null); // to clear
				data.template().headers(headers);
			}
		}

		@Override
		protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation, Method method) {
			Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
			if (annotationType == RequestLine.class) {
				String requestLine = RequestLine.class.cast(methodAnnotation).value();
				checkState(emptyToNull(requestLine) != null, "RequestLine annotation was empty on method %s.",
						method.getName());

				Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
				if (!requestLineMatcher.find()) {
					throw new IllegalStateException(String.format(
							"RequestLine annotation didn't start with an HTTP verb on method %s", method.getName()));
				} else {
					data.template().method(HttpMethod.valueOf(requestLineMatcher.group(1)));
					data.template().uri(requestLineMatcher.group(2));
				}
				data.template().decodeSlash(RequestLine.class.cast(methodAnnotation).decodeSlash());
				data.template().collectionFormat(RequestLine.class.cast(methodAnnotation).collectionFormat());

			} else if (annotationType == Body.class) {
				String body = Body.class.cast(methodAnnotation).value();
				checkState(emptyToNull(body) != null, "Body annotation was empty on method %s.", method.getName());
				if (body.indexOf('{') == -1) {
					data.template().body(body);
				} else {
					data.template().bodyTemplate(body);
				}
			} else if (annotationType == Headers.class) {
				String[] headersOnMethod = Headers.class.cast(methodAnnotation).value();
				checkState(headersOnMethod.length > 0, "Headers annotation was empty on method %s.", method.getName());
				data.template().headers(toMap(headersOnMethod));
			}
		}

		@Override
		protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
			boolean isHttpAnnotation = false;
			for (Annotation annotation : annotations) {
				Class<? extends Annotation> annotationType = annotation.annotationType();
				if (annotationType == Param.class) {
					Param paramAnnotation = (Param) annotation;
					String name = paramAnnotation.value();
					checkState(emptyToNull(name) != null, "Param annotation was empty on param %s.", paramIndex);
					nameParam(data, name, paramIndex);
					Class<? extends Param.Expander> expander = paramAnnotation.expander();
					if (expander != Param.ToStringExpander.class) {
						data.indexToExpanderClass().put(paramIndex, expander);
					}
					data.indexToEncoded().put(paramIndex, paramAnnotation.encoded());
					isHttpAnnotation = true;
					if (!data.template().hasRequestVariable(name)) {
						data.formParams().add(name);
					}
				} else if (annotationType == QueryMap.class) {
					checkState(data.queryMapIndex() == null, "QueryMap annotation was present on multiple parameters.");
					data.queryMapIndex(paramIndex);
					data.queryMapEncoded(QueryMap.class.cast(annotation).encoded());
					isHttpAnnotation = true;
				} else if (annotationType == HeaderMap.class) {
					checkState(data.headerMapIndex() == null,
							"HeaderMap annotation was present on multiple parameters.");
					data.headerMapIndex(paramIndex);
					isHttpAnnotation = true;
				}
			}
			return isHttpAnnotation;
		}

		private static Map<String, Collection<String>> toMap(String[] input) {
			Map<String, Collection<String>> result = new LinkedHashMap<String, Collection<String>>(input.length);
			for (String header : input) {
				int colon = header.indexOf(':');
				String name = header.substring(0, colon);
				if (!result.containsKey(name)) {
					result.put(name, new ArrayList<String>(1));
				}
				result.get(name).add(header.substring(colon + 1).trim());
			}
			return result;
		}
	}
}
