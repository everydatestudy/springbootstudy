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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerMethodExceptionResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**该子类实现就是用于处理标注有@ExceptionHandler注解的HandlerMethod方法的，是@ExceptionHandler功能的实现部分。

请注意命名上和ExceptionHandlerMethodResolver做区分~
 * An {@link AbstractHandlerMethodExceptionResolver} that resolves exceptions
 * through {@code @ExceptionHandler} methods.
 *
 * <p>
 * Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and
 * {@link #setCustomReturnValueHandlers}. Or alternatively to re-configure all
 * argument and return value types use {@link #setArgumentResolvers} and
 * {@link #setReturnValueHandlers(List)}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
//对它的功能，总结如下：
//
//@ExceptionHandler的处理和执行是由本类完成的，同一个Class上的所有@ExceptionHandler方法对应着同一个ExceptionHandlerExceptionResolver，不同Class上的对应着不同的~
//标注有@ExceptionHandler的方法入参上可写：具体异常类型、ServletRequest/ServletResponse/RedirectAttributes/ModelMethod等等
//1. 注意：入参写具体异常类型时只能够写一个类型。（若有多种异常，请写公共父类，你再用instanceof来辨别，而不能直接写多个）
//返回值可写：ModelAndView/Model/View/HttpEntity/ModelAttribute/RequestResponseBody/@ResponseStatus等等
//@ExceptionHandler只能标注在方法上。既能标注在Controller本类内的方法上（只对本类生效），也可配合@ControllerAdvice一起使用（对全局生效）
//对步骤4的两种情况，执行时的匹配顺序如下：优先匹配本类（本Controller），再匹配全局的。
//有必要再强调一句：@ExceptionHandler方式并不是只能返回JSON串，步骤4也说了，它返回一个ModelAndView也是ok的
//————————————————
//版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/f641385712/article/details/102294670
public class ExceptionHandlerExceptionResolver extends AbstractHandlerMethodExceptionResolver
		implements ApplicationContextAware, InitializingBean {
	// 这个熟悉：用于处理方法入参的（比如支持入参里可写HttpServletRequest等等）
	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;
	// 用于处理方法返回值（ModelAndView、@ResponseBody、@ResponseStatus等）
	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
	// 消息处理器和内容协商管理器
	private List<HttpMessageConverter<?>> messageConverters;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	private final List<Object> responseBodyAdvice = new ArrayList<>();

	@Nullable
	private ApplicationContext applicationContext;
	// 缓存：异常类型对应的处理器
	// 它缓存着Controller本类，对应的异常处理器（多个@ExceptionHandler）~~~~
	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache = new ConcurrentHashMap<>(64);
	// 它缓存ControllerAdviceBean对应的异常处理器（@ExceptionHandler）
	private final Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> exceptionHandlerAdviceCache = new LinkedHashMap<>();
	// 唯一构造函数：注册上默认的消息转换器
	public ExceptionHandlerExceptionResolver() {
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false); // see SPR-7316

		this.messageConverters = new ArrayList<>();
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(stringHttpMessageConverter);
		this.messageConverters.add(new SourceHttpMessageConverter<>());
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}

	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument resolution
	 * use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding the
	 * resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		} else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		return this.argumentResolvers;
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are ordered
	 * after built-in ones. To override the built-in support for return value
	 * handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus overriding
	 * handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		} else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not initialized
	 * yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public HandlerMethodReturnValueHandlerComposite getReturnValueHandlers() {
		return this.returnValueHandlers;
	}

	/**
	 * Set the message body converters to use.
	 * <p>
	 * These converters are used to convert from and to HTTP requests and responses.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media
	 * types. If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Return the configured {@link ContentNegotiationManager}.
	 */
	public ContentNegotiationManager getContentNegotiationManager() {
		return this.contentNegotiationManager;
	}

	/**
	 * Add one or more components to be invoked after the execution of a controller
	 * method annotated with {@code @ResponseBody} or returning
	 * {@code ResponseEntity} but before the body is written to the response with
	 * the selected {@code HttpMessageConverter}.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		this.responseBodyAdvice.clear();
		if (responseBodyAdvice != null) {
			this.responseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	@Override
	public void setApplicationContext(@Nullable ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Nullable
	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBodyAdvice beans
		// 初始化异常handler通知缓存 -》
		// 这一步骤同RequestMappingHandlerAdapter#initControllerAdviceCache
		// 目的是找到项目中所有的`ResponseBodyAdvice`，然后缓存起来。
		// 并且把它里面所有的标注有@ExceptionHandler的方法都解析保存起来
		// exceptionHandlerAdviceCache：每个advice切面对应哪个ExceptionHandlerMethodResolver（含多个@ExceptionHandler处理方法）
				
		//并且，并且若此Advice还实现了接口：ResponseBodyAdvice。那就还可干预到异常处理器的返回值处理上（基于body）
		//可见：若你想干预到异常处理器的返回值body上，可通过ResponseBodyAdvice来实现哟~~~~~~~~~ 
		// 可见ResponseBodyAdvice连异常处理方法也是生效的，但是`RequestBodyAdvice`可就木有啦。
		initExceptionHandlerAdviceCache();
		// 注册默认的参数处理器。支持到了@SessionAttribute、@RequestAttribute
				// ServletRequest/ServletResponse/RedirectAttributes/ModelMethod等等（当然你还可以自定义）
		if (this.argumentResolvers == null) {
			// 获取默认的参数解析器-》
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}// 支持到了：ModelAndView/Model/View/HttpEntity/ModelAttribute/RequestResponseBody
		// ViewName/Map等等这些返回值 当然还可以自定义
		if (this.returnValueHandlers == null) {
			// 获取默认的ReturnValueHandlers -》
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initExceptionHandlerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Looking for exception mappings: " + getApplicationContext());
		}
		// 查找@ControllerAdvice注解的controller -》
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
		AnnotationAwareOrderComparator.sort(adviceBeans);

		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
			ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(beanType);
			if (resolver.hasExceptionMappings()) {
				this.exceptionHandlerAdviceCache.put(adviceBean, resolver);
				if (logger.isInfoEnabled()) {
					logger.info("Detected @ExceptionHandler methods in " + adviceBean);
				}
			}
			if (ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				this.responseBodyAdvice.add(adviceBean);
				if (logger.isInfoEnabled()) {
					logger.info("Detected ResponseBodyAdvice implementation in " + adviceBean);
				}
			}
		}
	}

	/**
	 * Return an unmodifiable Map with the
	 * {@link ControllerAdvice @ControllerAdvice} beans discovered in the
	 * ApplicationContext. The returned map will be empty if the method is invoked
	 * before the bean has been initialized via {@link #afterPropertiesSet()}.
	 */
	public Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> getExceptionHandlerAdviceCache() {
		return Collections.unmodifiableMap(this.exceptionHandlerAdviceCache);
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers and
	 * custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	protected List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		// Annotation-based argument resolution
		// 从session中解析参数解析器@SessionAttribute
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		////    从request中解析参数解析器@RequestAttribute
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		//基于servletRequets的参数解析器
		resolvers.add(new ServletRequestMethodArgumentResolver());
		//基于servletResponse的参数解析器
		resolvers.add(new ServletResponseMethodArgumentResolver());
		//重定向绑定参数解析器
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		//从model中解析参数解析器
		resolvers.add(new ModelMethodProcessor());

		// Custom arguments
		//Custom arguments 自定义参数解析器
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and custom
	 * handlers provided via {@link #setReturnValueHandlers}.
	 */
	protected List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

		// Single-purpose return value types
		//基于modelAndView返回值解析器
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		// 基于model的返回值解析器
		handlers.add(new ModelMethodProcessor());
		// 基于view的返回值解析器
		handlers.add(new ViewMethodReturnValueHandler());
		// 基于HttpEntity的返回值解析器
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.contentNegotiationManager,this.responseBodyAdvice));
		//基于@ModelAttribute属性方法的返回值解析器
		// Annotation-based return value types
		handlers.add(new ModelAttributeMethodProcessor(false));
		// 基于@RequestBody和@ResponseBody注解方法的参数解析器
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.contentNegotiationManager,
				this.responseBodyAdvice));

		// Multi-purpose return value types
		//基于视图名字的返回值解析器
		handlers.add(new ViewNameMethodReturnValueHandler());
		//基于map的返回值解析器
		handlers.add(new MapMethodProcessor());

		// Custom return value types
		// // Custom return value types 自定义返回值解析器
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		// 基于@ModelAttribute注解方法的解析器
		handlers.add(new ModelAttributeMethodProcessor(true));

		return handlers;
	}

	/** 处理HandlerMethod类型的异常。它的步骤是找到标注有@ExceptionHandler匹配的方法
	// 然后执行此方法来处理所抛出的异常
	 * Find an {@code @ExceptionHandler} method and invoke it to handle the raised
	 * exception.
	 */
	@Override
	@Nullable
	protected ModelAndView doResolveHandlerMethodException(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerMethod handlerMethod, Exception exception) {
		// 这个方法是精华，是关键。它最终返回的是一个ServletInvocableHandlerMethod可执行的方法处理器
		// 也就是说标注有@ExceptionHandler的方法最终会成为它

		// 1、本类能够找到处理方法，就在本类里找，找到就返回一个ServletInvocableHandlerMethod
		// 2、本类木有，就去ControllerAdviceBean切面里找，匹配上了也是欧克的
		//   显然此处会判断：advice.isApplicableToBeanType(handlerType) 看此advice是否匹配
		// 若两者都木有找到，那就返回null。这里的核心其实是ExceptionHandlerMethodResolver这个类

		ServletInvocableHandlerMethod exceptionHandlerMethod = getExceptionHandlerMethod(handlerMethod, exception);
		if (exceptionHandlerMethod == null) {
			return null;
		}
		// 给该执行器设置一些值，方便它的指定（封装参数和处理返回值）
		if (this.argumentResolvers != null) {
			exceptionHandlerMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		if (this.returnValueHandlers != null) {
			exceptionHandlerMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
		}

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		ModelAndViewContainer mavContainer = new ModelAndViewContainer();

		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Invoking @ExceptionHandler method: " + exceptionHandlerMethod);
			}
			Throwable cause = exception.getCause();
			if (cause != null) {
				// Expose cause as provided argument as well
				exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception, cause, handlerMethod);
			} else {
				// Otherwise, just the given exception as-is
				exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception, handlerMethod);
			}
		} catch (Throwable invocationEx) {
			// Any other than the original exception is unintended here,
			// probably an accident (e.g. failed assertion or the like).
			if (invocationEx != exception && logger.isWarnEnabled()) {
				logger.warn("Failed to invoke @ExceptionHandler method: " + exceptionHandlerMethod, invocationEx);
			}
			// Continue with default processing of the original exception...
			return null;
		}

		if (mavContainer.isRequestHandled()) {
			return new ModelAndView();
		} else {
			ModelMap model = mavContainer.getModel();
			HttpStatus status = mavContainer.getStatus();
			ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, status);
			mav.setViewName(mavContainer.getViewName());
			if (!mavContainer.isViewReference()) {
				mav.setView((View) mavContainer.getView());
			}
			if (model instanceof RedirectAttributes) {
				Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
			return mav;
		}
	}

	/**
	 * Find an {@code @ExceptionHandler} method for the given exception. The default
	 * implementation searches methods in the class hierarchy of the controller
	 * first and if not found, it continues searching for additional
	 * {@code @ExceptionHandler} methods assuming some
	 * {@linkplain ControllerAdvice @ControllerAdvice} Spring-managed beans were
	 * detected.
	 * 
	 * @param handlerMethod the method where the exception was raised (may be
	 *                      {@code null})
	 * @param exception     the raised exception
	 * @return a method to handle the exception, or {@code null} if none
	 */
	@Nullable
	protected ServletInvocableHandlerMethod getExceptionHandlerMethod(@Nullable HandlerMethod handlerMethod,
			Exception exception) {

		Class<?> handlerType = null;

		if (handlerMethod != null) {
			// Local exception handler methods on the controller class itself.
			// To be invoked through the proxy, even in case of an interface-based proxy.
			handlerType = handlerMethod.getBeanType();
			ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.get(handlerType);
			if (resolver == null) {
				resolver = new ExceptionHandlerMethodResolver(handlerType);
				this.exceptionHandlerCache.put(handlerType, resolver);
			}
			Method method = resolver.resolveMethod(exception);
			if (method != null) {
				return new ServletInvocableHandlerMethod(handlerMethod.getBean(), method);
			}
			// For advice applicability check below (involving base packages, assignable
			// types
			// and annotation presence), use target class instead of interface-based proxy.
			if (Proxy.isProxyClass(handlerType)) {
				handlerType = AopUtils.getTargetClass(handlerMethod.getBean());
			}
		}

		for (Map.Entry<ControllerAdviceBean, ExceptionHandlerMethodResolver> entry : this.exceptionHandlerAdviceCache
				.entrySet()) {
			ControllerAdviceBean advice = entry.getKey();
			if (advice.isApplicableToBeanType(handlerType)) {
				ExceptionHandlerMethodResolver resolver = entry.getValue();
				Method method = resolver.resolveMethod(exception);
				if (method != null) {
					return new ServletInvocableHandlerMethod(advice.resolveBean(), method);
				}
			}
		}

		return null;
	}

}
