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
 https://blog.csdn.net/beFocused/article/details/112391530 所有类型解析期
 * method parameters by delegating to a list of registered
 * {@link HandlerMethodArgumentResolver}s. Previously resolved method parameters
 * are cached for faster lookups.
 *
 *
 *   类型									     支持的参数条件
 *   RequestParamMethodArgumentResolver   参数符合以下任一条件
										   1. 被@RequestParam注解且参数类型不是Map
										   2. 被@RequestParam注解且参数类型是Map且@RequestParam注解的name属性不为空
										   3. 没有注解@RequestParam且没有注解@RequestPart且参数是MultipartFile或part(与multipart requests相关)
 										   4. 如果解析器作为默认解析器即useDefaultResolution为true且参数类型是简单类型

 *  RequestParamMapMethodArgumentResolver  参数包含注解@RequestParam且类型是Map及其子类且注解@RequestParam属性的name为空
 *  
 *  PathVariableMethodArgumentResolver      该解析支持符合以下条件任一条件的参数
 * 										      1. 参数包含注解@PathVariable且参数类型不是Map及其子类
										      2. 参数包含注解@PathVariable且参数类型是Map及其子类且注解@PathVariable的value属性不为空
 *  PathVariableMapMethodArgumentResolver   参数包含注解@PathVariable且类型是Map及其子类且注解@PathVariable属性的name为空
 * 
 *  MatrixVariableMethodArgumentResolver   该解析支持符合以下条件任一条件的参数
											1. 参数包含注解@MatrixVariable且参数类型不是Map及其子类
											2. 参数包含注解@MatrixVariable且参数类型是Map及其子类且注解@MatrixVariable的value属性不为空
 
 *MatrixVariableMapMethodArgumentResolver  参数包含注解@MatrixVariable且类型是Map及其子类且注解@MatrixVariable属性的name为空

  ServletModelAttributeMethodProcessor   参数符合以下任一条件
										  1. 参数有注解@ModuleAttribute
										  2. 属性annotationNotRequired为true且参数不是简单类型，简单类型包括

 *RequestResponseBodyMethodProcessor      参数包含注解@RequestBody

RequestHeaderMethodArgumentResolver       参数包含注解@RequestHeader且参数类型不是Map及其子类

RequestHeaderMapMethodArgumentResolver    参数包含注解@RequestHeader且类型是Map及其子类
ServletCookieValueMethodArgumentResolver  参数包含注解@CookieValue
ExpressionValueMethodArgumentResolver     参数包含注解@Value
SessionAttributeMethodArgumentResolver    参数包含注解@SessionAttribute

RequestAttributeMethodArgumentResolver 	  参数包含注解@RequestAttribute
ServletRequestMethodArgumentResolver     参数类型是以下任何一种
											1. org.springframework.web.context.request.WebRequest及其子类
											2. javax.servlet.ServletRequest及其子类
											3. org.springframework.web.multipart.MultipartRequest及其子类
											4. javax.servlet.http.HttpSession及其子类
											5. javax.servlet.http.PushBuilder及其子类
											6. java.security.Principal及其子类
											7. java.io.InputStream及其子类
											8. java.io.Reader及其子类
											9. org.springframework.http.HttpMethod
											10. java.util.Locale
											11. java.util.TimeZone
											12. java.time.ZoneId

ServletResponseMethodArgumentResolver    参数类型是javax.servlet.ServletResponse及其子类或java.io.OutputStream及其子类或java.io.Writer及其子类
HttpEntityMethodProcessor                参数类型是org.springframework.http.RequestEntity或者org.springframework.http.HttpEntity


RedirectAttributesMethodArgumentResolver  参数类型是org.springframework.web.servlet.mvc.support.RedirectAttributes及其子类

ModelMethodProcessor                      参数类型是org.springframework.ui.Model及其子类型

MapMethodProcessor						    参数类型是Map及其子类型且参数上没有任何注解


ErrorsMethodArgumentResolver              参数类型是org.springframework.validation.Errors及其子类

SessionStatusMethodArgumentResolver      参数类型是org.springframework.web.bind.support.SessionStatus

UriComponentsBuilderMethodArgumentResolver  参数类型是org.springframework.web.util.UriComponentsBuilder或者org.springframework.web.servlet.support.ServletUriComponentsBuilder




 *
 *
 *
 *
 *
 *
 *
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

	/**	//判断参数解析器是否支持参数解析getArgumentResolver()方法是本文的核心
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

	/** 这块逻辑保证了每个parameter参数最多只会被一个处理器处理
	// 这个从缓存的数据结构中也能够看出来的
	 * Find a registered {@link HandlerMethodArgumentResolver} that supports the
	 * given method parameter.
	 */
	@Nullable
	private HandlerMethodArgumentResolver getArgumentResolver(MethodParameter parameter) {
		HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
		if (result == null) {
//			[org.springframework.web.method.annotation.RequestParamMethodArgumentResolver@6bb56f2f, ---这里是处理带有@RequestParam这个注解的
//			 org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver@2794c5b, ---这里是处理带有@RequestParam类型是map的
//			 org.springframework.web.servlet.mvc.method.annotation.PathVariableMethodArgumentResolver@2052c061,---PathVariableMethodArgumentResolver支持添加了@PathVariable注解的参数解析，并且可以解析有@PathVariable注解并且注解的value值不为空
//			 org.springframework.web.servlet.mvc.method.annotation.PathVariableMapMethodArgumentResolver@5218dd4,--PathVariableMapMethodArgumentResolver是直接将restful风格的请求参数绑定到handler的map参数上，比较简单。
//			 org.springframework.web.servlet.mvc.method.annotation.MatrixVariableMethodArgumentResolver@452d4648, --标注有@MatrixVariable注解的参数的处理器。Matrix：矩阵，这个注解是Spring3.2新提出来的，增强Restful的处理能力（配合@PathVariable使用），比如这类URL的解析就得靠它：/owners/42;q=11/pets/21;s=23;q=22。
//			 org.springframework.web.servlet.mvc.method.annotation.MatrixVariableMapMethodArgumentResolver@dfec122,-- 针对被 @MatrixVariable 注解修饰, 并且类型是 Map的, 且 MatrixVariable.name == null, 从 HttpServletRequest 中获取 URI 模版变量 <-- 并且是去除 ;
//			 org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor@3b994934, --当handler的参数类型为自定义的实体类类型或者添加了@ModelAttribute注解，ServletModelAttributeMethodProcessor会对参数进行解析绑定。
//			 org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor@325f3cbe, --*顾名思义，它负责处理@RequestBody这个注解的参数
//			 org.springframework.web.servlet.mvc.method.annotation.RequestPartMethodArgumentResolver@1d6f23bb,--它用于解析参数被@RequestPart修饰，或者参数类型是MultipartFile | Servlet 3.0提供的javax.servlet.http.Part类型（并且没有被@RequestParam修饰），数据通过 HttpServletRequest获取
//			 org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver@66e306e5,---@RequestHeader注解，可以把Request请求header部分的值绑定到方法的参数上。
//			 org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver@174233e4, ---次性把请求头信息都拿到：数据类型支出写MultiValueMap(LinkedMultiValueMap)/HttpHeaders/Map。实例如下：
//			 org.springframework.web.servlet.mvc.method.annotation.ServletCookieValueMethodArgumentResolver@5c33477e,--指定了从HttpServletRequest去拿cookie值。
//			 org.springframework.web.method.annotation.ExpressionValueMethodArgumentResolver@3825d5b8, 它用于处理标注有@Value注解的参数。对于这个注解我们太熟悉不过了，没想到在web层依旧能发挥作用。
//			 org.springframework.web.servlet.mvc.method.annotation.SessionAttributeMethodArgumentResolver@468f3877, 处理必须标注有@SessionAttribute注解的参数，原理说这一句话就够了。
//			 org.springframework.web.servlet.mvc.method.annotation.RequestAttributeMethodArgumentResolver@4054ee51, 处理必须标注有@RequestAttribute注解的参数，原理说这一句话就够了。
//			 org.springframework.web.servlet.mvc.method.annotation.ServletRequestMethodArgumentResolver@61a4992, 这种方式使用得其实还比较多的。比如平时我们需要用Servlet源生的API：HttpServletRequest, HttpServletResponse肿么办？ 在Spring MVC内就特别特别简单，只需要在入参上声明：就可以直接使用啦~
//			 org.springframework.web.servlet.mvc.method.annotation.ServletResponseMethodArgumentResolver@1004c4fc, --这唯一一个是处理入参时候的。若入参类型是ServletResponse/OutputStream/Writer，并且mavContainer != null，它就设置为true了（因为Spring MVC认为既然你自己引入了response，那你就自己做输出吧，因此使用时此处是需要特别注意的细节地方~）
//			 org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor@2ff70a0b, ---用于处理HttpEntity和RequestEntity类型的入参的。
//			 org.springframework.web.servlet.mvc.method.annotation.RedirectAttributesMethodArgumentResolver@48b65fc2, 和重定向属性RedirectAttributes相关。
//			 org.springframework.web.method.annotation.ModelMethodProcessor@1cb4c99f, 和MapMethodProcessor几乎一模一样。它处理org.springframework.ui.Model类型，处理方式几乎同Map方式一样（因为Model的结构和Map一样也是键值对）
//			 org.springframework.web.method.annotation.MapMethodProcessor@2aa1ddd1, ----它相对来说比较特殊，既处理Map类型的入参，也处理Map类型的返回值（本文只关注返回值处理器部分）
//			 org.springframework.web.method.annotation.ErrorsMethodArgumentResolver@3d113ddc, ---它用于在方法参数可以写Errors类型，来拿到数据校验结果。
//			 org.springframework.web.method.annotation.SessionStatusMethodArgumentResolver@47232df3, 支持SessionStatus。值为：mavContainer.getSessionStatus()
//			 org.springframework.web.servlet.mvc.method.annotation.UriComponentsBuilderMethodArgumentResolver@7de1fcb9, 支持的参数条件
//			 org.springframework.web.method.annotation.RequestParamMethodArgumentResolver@590af981,  ---这里是处理带有@RequestParam这个注解的
//			 org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor@6dc1d0a1]--当handler的参数类型为自定义的实体类类型或者添加了@ModelAttribute注解，ServletModelAttributeMethodProcessor会对参数进行解析绑定。
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
