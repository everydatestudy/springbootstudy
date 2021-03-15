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

//org.springframework.web.reactive.result.method.annotation
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ErrorsMethodArgumentResolver;
import org.springframework.web.method.annotation.ExpressionValueMethodArgumentResolver;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelFactory;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.annotation.SessionAttributesHandler;
import org.springframework.web.method.annotation.SessionStatusMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;

/**RequestMappingHandlerAdapter是个非常庞大的体系，本处我们只关心它对@ModelAttribute也就是对ModelFactory的创建，列出相关源码如下：
 * Extension of {@link AbstractHandlerMethodAdapter} that supports
 * {@link RequestMapping} annotated {@code HandlerMethod}s.
 *
 * <p>
 * Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and
 * {@link #setCustomReturnValueHandlers}, or alternatively, to re-configure all
 * argument and return value types, use {@link #setArgumentResolvers} and
 * {@link #setReturnValueHandlers}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 * @see HandlerMethodArgumentResolver
 * @see HandlerMethodReturnValueHandler
 */
//RequestMappingHandlerAdapter这部分处理逻辑：每次请求过来它都会创建一个ModelFactory，
//从而收集到全局的（来自@ControllerAdvice）+ 本Controller控制器上的所有的标注有@ModelAttribute注解的方法们。
//@ModelAttribute标注在单独的方法上（木有@RequestMapping注解），它可以在每个控制器方法调用之前，创建出一个ModelFactory从而管理Model数据~
//
//ModelFactory管理着Model，提供了@ModelAttribute以及@SessionAttributes等对它的影响
//
//同时@ModelAttribute可以标注在入参、方法（返回值）上的，标注在不同地方处理的方式是不一样的，那么接下来又一主菜ModelAttributeMethodProcessor就得登场了。
 
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

	/**
	 * MethodFilter that matches {@link InitBinder @InitBinder} methods.
	 */// 该方法不能标注有@RequestMapping注解，只标注了@ModelAttribute才算哦~
	public static final MethodFilter INIT_BINDER_METHODS = method -> (AnnotationUtils.findAnnotation(method,
			InitBinder.class) != null);
	// MethodIntrospector.selectMethods的过滤器。
	// 这里意思是：含有@ModelAttribute，但是但是但是不含有@RequestMapping注解的方法~~~~~
	/**
	 * MethodFilter that matches {@link ModelAttribute @ModelAttribute} methods.
	 */
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method -> (AnnotationUtils.findAnnotation(method,
			RequestMapping.class) == null && AnnotationUtils.findAnnotation(method, ModelAttribute.class) != null);

	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	@Nullable
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;
	// 这里保存在用户自定义的一些处理器，大部分情况下无需自定义~~~
	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	@Nullable
	private List<ModelAndViewResolver> modelAndViewResolvers;
	// 内容协商管理器  默认就是它喽（使用的协商策略是HeaderContentNegotiationStrategy）
	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();
	// 消息转换器。使用@Bean定义的时候，记得set进来，否则默认只会有4个（不支持json）
	// 若@EnableWebMvc后默认是有8个的，一般都够用了
	private List<HttpMessageConverter<?>> messageConverters;
	// 装载RequestBodyAdvice和ResponseBodyAdvice的实现类们~
	private List<Object> requestResponseBodyAdvice = new ArrayList<>();
	// 它在数据绑定初始化的时候会被使用到，调用其initBinder()方法
		// 只不过，现在一般都使用@InitBinder注解来处理了，所以使用较少
		// 说明：它作用域是全局的，对所有的HandlerMethod都生效~~~~~
	@Nullable
	private WebBindingInitializer webBindingInitializer;
	// 默认使用的SimpleAsyncTaskExecutor：每次执行客户提交给它的任务时，它会启动新的线程
		// 并允许开发者控制并发线程的上限（concurrencyLimit），从而起到一定的资源节流作用（默认值是-1，表示不限流）
		// @EnableWebMvc时可通过复写接口的WebMvcConfigurer.getTaskExecutor()自定义提供一个线程池

	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");

	@Nullable
	private Long asyncRequestTimeout;

	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
	// 对应ModelAndViewContainer.setIgnoreDefaultModelOnRedirect()属性
		// redirect时,是否忽略defaultModel 默认值是false：不忽略
	private boolean ignoreDefaultModelOnRedirect = false;

	private int cacheSecondsForSessionAttributeHandlers = 0;
	// 执行目标方法HandlerMethod时是否要在同一个Session内同步执行？？？
		// 也就是同一个会话时，控制器方法全部同步执行（加互斥锁）
		// 使用场景：对同一用户同一Session的所有访问，必须串行化~~~~~~
	private boolean synchronizeOnSession = false;

	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	@Nullable
	private ConfigurableBeanFactory beanFactory;

	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<>(64);

	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap<>();

	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<>(64);
	// 从Advice里面分析出来的标注有@ModelAttribute的方法（它是全局的）
	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap<>();
	// 唯一构造方法：默认注册一些消息转换器。
		// 开启@EnableWebMvc后此默认行为会被setMessageConverters()方法覆盖
	public RequestMappingHandlerAdapter() {
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false); // see SPR-7316

		this.messageConverters = new ArrayList<>(4);
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(stringHttpMessageConverter);
		this.messageConverters.add(new SourceHttpMessageConverter<>());
		// 添加xml和json转换器 -》,这里的转换是对于form表单转换的，只有是multipart/form-data才會走json
		// 這裡在返回的時候設置了返回类型是application/x-www-form-urlencoded，浏览器会把它下载下来
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
		// TODO 这里只是为了测试，增加fastjson
		this.messageConverters.add(new FastJsonHttpMessageConverter());

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
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null);
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		} else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the argument resolvers for {@code @InitBinder} methods, or possibly
	 * {@code null} if not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null);
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
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return (this.returnValueHandlers != null ? this.returnValueHandlers.getHandlers() : null);
	}

	/**
	 * Provide custom {@link ModelAndViewResolver}s.
	 * <p>
	 * <strong>Note:</strong> This method is available for backwards compatibility
	 * only. However, it is recommended to re-write a {@code ModelAndViewResolver}
	 * as {@link HandlerMethodReturnValueHandler}. An adapter between the two
	 * interfaces is not possible since the
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method cannot be
	 * implemented. Hence {@code ModelAndViewResolver}s are limited to always being
	 * invoked at the end after all other return value handlers have been given a
	 * chance.
	 * <p>
	 * A {@code HandlerMethodReturnValueHandler} provides better access to the
	 * return type and controller method information and can be ordered freely
	 * relative to other return value handlers.
	 */
	public void setModelAndViewResolvers(@Nullable List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * Return the configured {@link ModelAndViewResolver}s, or {@code null}.
	 */
	@Nullable
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return this.modelAndViewResolvers;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media
	 * types. If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Provide the converters to use in argument resolvers and return value handlers
	 * that support reading and/or writing to the body of the request and response.
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
	 * Add one or more {@code RequestBodyAdvice} instances to intercept the request
	 * before it is read and converted for {@code @RequestBody} and
	 * {@code HttpEntity} method arguments.
	 */
	public void setRequestBodyAdvice(@Nullable List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * Add one or more {@code ResponseBodyAdvice} instances to intercept the
	 * response before {@code @ResponseBody} or {@code ResponseEntity} return values
	 * are written to the response body.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply to
	 * every DataBinder instance.
	 */
	public void setWebBindingInitializer(@Nullable WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none.
	 */
	@Nullable
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * Set the default {@link AsyncTaskExecutor} to use when a controller method
	 * return a {@link Callable}. Controller methods can override this default on a
	 * per-request basis by returning an {@link WebAsyncTask}.
	 * <p>
	 * By default a {@link SimpleAsyncTaskExecutor} instance is used. It's
	 * recommended to change that default in production as the simple executor does
	 * not re-use threads.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Specify the amount of time, in milliseconds, before concurrent handling
	 * should time out. In Servlet 3, the timeout begins after the main request
	 * processing thread has exited and ends when the request is dispatched again
	 * for further processing of the concurrently produced result.
	 * <p>
	 * If this value is not set, the default timeout of the underlying
	 * implementation is used, e.g. 10 seconds on Tomcat with Servlet 3.
	 * 
	 * @param timeout the timeout value in milliseconds
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * Configure {@code CallableProcessingInterceptor}'s to register on async
	 * requests.
	 * 
	 * @param interceptors the interceptors to register
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[0]);
	}

	/**
	 * Configure {@code DeferredResultProcessingInterceptor}'s to register on async
	 * requests.
	 * 
	 * @param interceptors the interceptors to register
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[0]);
	}

	/**
	 * Configure the registry for reactive library types to be supported as return
	 * values from controller methods.
	 * 
	 * @since 5.0
	 * @deprecated as of 5.0.5, in favor of {@link #setReactiveAdapterRegistry}
	 */
	@Deprecated
	public void setReactiveRegistry(ReactiveAdapterRegistry reactiveRegistry) {
		this.reactiveAdapterRegistry = reactiveRegistry;
	}

	/**
	 * Configure the registry for reactive library types to be supported as return
	 * values from controller methods.
	 * 
	 * @since 5.0.5
	 */
	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
	}

	/**
	 * Return the configured reactive type registry of adapters.
	 * 
	 * @since 5.0
	 */
	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * By default the content of the "default" model is used both during rendering
	 * and redirect scenarios. Alternatively a controller method can declare a
	 * {@link RedirectAttributes} argument and use it to provide attributes for a
	 * redirect.
	 * <p>
	 * Setting this flag to {@code true} guarantees the "default" model is never
	 * used in a redirect scenario even if a RedirectAttributes argument is not
	 * declared. Setting it to {@code false} means the "default" model may be used
	 * in a redirect if the controller method doesn't declare a RedirectAttributes
	 * argument.
	 * <p>
	 * The default setting is {@code false} but new applications should consider
	 * setting it to {@code true}.
	 * 
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Specify the strategy to store session attributes with. The default is
	 * {@link org.springframework.web.bind.support.DefaultSessionAttributeStore},
	 * storing session attributes in the HttpSession with the same attribute name as
	 * in the model.
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * Cache content produced by {@code @SessionAttributes} annotated handlers for
	 * the given number of seconds.
	 * <p>
	 * Possible values are:
	 * <ul>
	 * <li>-1: no generation of cache-related headers</li>
	 * <li>0 (default value): "Cache-Control: no-store" will prevent caching</li>
	 * <li>1 or higher: "Cache-Control: max-age=seconds" will ask to cache content;
	 * not advised when dealing with session attributes</li>
	 * </ul>
	 * <p>
	 * In contrast to the "cacheSeconds" property which will apply to all general
	 * handlers (but not to {@code @SessionAttributes} annotated handlers), this
	 * setting will apply to {@code @SessionAttributes} handlers only.
	 * 
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * Set if controller execution should be synchronized on the session, to
	 * serialize parallel invocations from the same client.
	 * <p>
	 * More specifically, the execution of the {@code handleRequestInternal} method
	 * will get synchronized if this flag is "true". The best available session
	 * mutex will be used for the synchronization; ideally, this will be a mutex
	 * exposed by HttpSessionMutexListener.
	 * <p>
	 * The session mutex is guaranteed to be the same object during the entire
	 * lifetime of the session, available under the key defined by the
	 * {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a safe reference to
	 * synchronize on for locking on the current session.
	 * <p>
	 * In many cases, the HttpSession reference itself is a safe mutex as well,
	 * since it will always be the same object reference for the same active logical
	 * session. However, this is not guaranteed across different servlet containers;
	 * the only 100% safe way is a session mutex.
	 * 
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see org.springframework.web.util.WebUtils#getSessionMutex(javax.servlet.http.HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter names
	 * if needed (e.g. for default attribute names).
	 * <p>
	 * Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions in
	 * method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * Return the owning factory of this bean instance, or {@code null} if none.
	 */
	@Nullable
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBody advice beans
		// 初始化ControllerAdvice缓存-》
		initControllerAdviceCache();

		if (this.argumentResolvers == null) {
			// 获取默认的方法参数解析器 -》
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		if (this.initBinderArgumentResolvers == null) {
			// 获取默认的@InitBinder参数绑定解析器 -》
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		if (this.returnValueHandlers == null) {
			// 解析默认的返回值handler -》
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}
//	部分代码调理清晰，有4个作用总结如下：
//
//	找到容器内（包括父容器）所有的标注有@ControllerAdvice注解的Bean们缓存起来，然后一个个解析此种Bean
//	找到该Advice Bean内所有的标注有@ModelAttribute但没标注@RequestMapping的方法们，缓存到modelAttributeAdviceCache里对全局生效
//	找到该Advice Bean内所有的标注有@InitBinder的方法们，缓存到initBinderAdviceCache里对全局生效
//	找到该Advice Bean内所有实现了接口RequestBodyAdvice/ResponseBodyAdvice们，最终放入缓存requestResponseBodyAdvice的头部，他们会介入请求body和返回body
//	————————————————
//	版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/f641385712/article/details/102726381
	private void initControllerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}
		if (logger.isInfoEnabled()) {
			logger.info("Looking for @ControllerAdvice: " + getApplicationContext());
		}
		// 从beanFactory中获取参数@ControllerAdvice的controller
		// 关键就是在findAnnotatedBeans方法里：传入了容器上下文
		// 拿到容器内所有的标注有@ControllerAdvice的组件们
		// BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context, Object.class)
		// .filter(name -> context.findAnnotationOnBean(name, ControllerAdvice.class) != null)
		// .map(name -> new ControllerAdviceBean(name, context)) // 使用ControllerAdviceBean包装起来，持有name的引用（还木实例化哟）
		// .collect(Collectors.toList());
		
		// 因为@ControllerAdvice注解可以指定包名等属性，具体可参见HandlerTypePredicate的判断逻辑，是否生效
		// 注意：@RestControllerAdvice是@ControllerAdvice和@ResponseBody的结合体，所以此处也会被找出来
		// 最后Ordered排序

		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
		AnnotationAwareOrderComparator.sort(adviceBeans);
		// 临时存储RequestBodyAdvice和ResponseBodyAdvice的实现类
				// 它哥俩是必须配合@ControllerAdvice一起使用的~
		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();
		// 注意：找到这些标注有@ControllerAdvice后并不需要保存下来。
		// 而是一个一个的找它们里面的@InitBinder/@ModelAttribute 以及 RequestBodyAdvice和ResponseBodyAdvice
		// 说明：异常注解不在这里解析，而是在`ExceptionHandlerMethodResolver`里~~~

		for (ControllerAdviceBean adviceBean : adviceBeans) {
			// 从controller中获取有@RequestMapping、@ModelAttribute注解的方法
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
			// 又见到了这个熟悉的方法selectMethods~~~~过滤器请参照成员变量
						// 含有@ModelAttribute，但是但是但是不含有@RequestMapping注解的方法~~~~~  找到之后放在全局变量缓存起来
						// 简单的说就是找到@ControllerAdvice里面所有的@ModelAttribute方法们
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType, MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods);
				if (logger.isInfoEnabled()) {
					logger.info("Detected @ModelAttribute methods in " + adviceBean);
				}
			}
			// TODO 查找controller中有@InitBinder注解的方法
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType, INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(adviceBean, binderMethods);
				if (logger.isInfoEnabled()) {
					logger.info("Detected @InitBinder methods in " + adviceBean);
				}
			}
			if (RequestBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
				if (logger.isInfoEnabled()) {
					logger.info("Detected RequestBodyAdvice bean in " + adviceBean);
				}
			}
			if (ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
				if (logger.isInfoEnabled()) {
					logger.info("Detected ResponseBodyAdvice bean in " + adviceBean);
				}
			}
		}

		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers and
	 * custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		// Annotation-based argument resolution
		// 基于@RequestParam的参数解析器
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		//	    基于@RequestParam map参数解析器
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		//基于@PathVariable的参数解析器
		resolvers.add(new PathVariableMethodArgumentResolver());
		//基于@PathVariable map的参数解析器
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		//基于@MatrixVariable 矩阵参数解析器
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		//基于@MatrixVariable 矩阵map参数解析器
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		//基于从model解析参数的参数解析器
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		//基于@RequestBody、@ResponseBody的参数解析器
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		//基于@RequestParam 参数解析器
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		//基于@RequestHeader 参数解析器
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		//基于@RequestHeader map参数解析器
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		//基于从cookie中解析参数的参数解析器
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		//基于@Value 表达式参数解析器
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		//基于@SessionAttribute 参数解析器
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		//基于@RequestAttribute 参数解析器
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		//基于servletRequest的参数解析器
		resolvers.add(new ServletRequestMethodArgumentResolver());
		//基于servletResponse的参数解析器
		resolvers.add(new ServletResponseMethodArgumentResolver());
		//基于httpEntity参数解析器
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		//基于重定向参数绑定解析器
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		//基于从model中解析参数的参数解析器
		resolvers.add(new ModelMethodProcessor());
		//基于map的参数解析器
		resolvers.add(new MapMethodProcessor());
		//错误方法参数解析器
		resolvers.add(new ErrorsMethodArgumentResolver());
		//基于session状态方法参数解析器
		resolvers.add(new SessionStatusMethodArgumentResolver());
		//基于url参数解析器
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());
		 // Custom arguments 自定义参数解析器
		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		// 基于@RequestParam参数解析器，用默认值
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		//基于servlet model中解析参数的参数解析器，使用默认值
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

	/**
	 * Return the list of argument resolvers to use for {@code @InitBinder} methods
	 * including built-in and custom resolvers.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		// Annotation-based argument resolution
		//基于@RequestParam的参数解析器
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		//基于@RequestParam map的参数解析器
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		// 基于@PathVariable 的参数解析器
		resolvers.add(new PathVariableMethodArgumentResolver());
		//基于@PathVariable map的参数解析器
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		//基于@MatrixVariable 矩阵参数解析器
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		//基于@MatrixVariable map矩阵参数解析器
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		//基于@Value 表达式参数解析器
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		// 基于@SessionAttribute参数解析器
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		//基于@RequestAttribute 参数解析器
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		// 基于servletRequest参数解析器
		resolvers.add(new ServletRequestMethodArgumentResolver());
		//基于servletResponse参数解析器
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		//基于@RequestParam 参数解析器，使用默认值
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * 增加所有的视图解析器 Return the list of return value handlers to use including built-in
	 * and custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

		// Single-purpose return value types
		//方法返回值是ModelAndView的返回值解析器
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		//方法返回值是model的返回值解析器
		handlers.add(new ModelMethodProcessor());
		//方法是返回值是view的返回值解析器
		handlers.add(new ViewMethodReturnValueHandler());
		//方法返回值是responseEntity的返回值解析器
		// 返回值是ResponseBodyEmitter时候，得用reactiveAdapterRegistry看看是Reactive模式还是普通模式
		// taskExecutor：异步时使用的线程池，使用当前类的  contentNegotiationManager：内容协商管理器

		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters(), this.reactiveAdapterRegistry,this.taskExecutor, this.contentNegotiationManager));
		//方法返回值是流的返回值解析器
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		//方法返回值是httpEntity的方法返回值解析器
		// 此处重要的是getMessageConverters()消息转换器，一般情况下Spring MVC默认会有8个，包括`MappingJackson2HttpMessageConverter`
				// 参见：WebMvcConfigurationSupport定的@Bean --> RequestMappingHandlerAdapter部分
				// 若不@EnableWebMvc默认是只有4个消息转换器的哦~（不支持json）
				// 此处的requestResponseBodyAdvice会介入到请求和响应的body里（消息转换期间）

		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.contentNegotiationManager,this.requestResponseBodyAdvice));
		//方法返回值是httpHeaders的方法返回值解析器
		handlers.add(new HttpHeadersReturnValueHandler());
		//方法返回值是callable的方法返回值解析器
		handlers.add(new CallableMethodReturnValueHandler());
		//方法返回值是DeferredResult的方法返回值解析器
		handlers.add(new DeferredResultMethodReturnValueHandler());
		//方法返回值是WebAsyncTask的方法返回值解析器
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));
		 //方法上有@ModelAttribute注解的返回值解析器
		// Annotation-based return value types
		handlers.add(new ModelAttributeMethodProcessor(false));
		//基于@RequestBody、@ResponseBody的返回值解析器
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.contentNegotiationManager,
				this.requestResponseBodyAdvice));

		// Multi-purpose return value types
		//基于视图名字的返回值解析器
		handlers.add(new ViewNameMethodReturnValueHandler());
		//基于map的返回值解析器
		handlers.add(new MapMethodProcessor());

		// Custom return value types
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}
		// 兜底：ModelAndViewResolver是需要你自己实现然后set进来的（一般我们不会自定定义）
				// 所以绝大部分情况兜底使用的是ModelAttributeMethodProcessor表示，即使你的返回值里木有标注@ModelAttribute
				// 但你是非简单类型(比如对象类型)的话，返回值都会放进Model里
 
		// Catch-all
		//基于modelAndView的返回值解析器
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		} else {
			//方法上有@ModelAttribute注解的返回值解析器，使用默认值
			handlers.add(new ModelAttributeMethodProcessor(true));
		}

		return handlers;
	}

	/**
	 * Always return {@code true} since any method argument and return value type
	 * will be processed in some way. A method argument not recognized by any
	 * HandlerMethodArgumentResolver is interpreted as a request parameter if it is
	 * a simple type, or as a model attribute otherwise. A return value not
	 * recognized by any HandlerMethodReturnValueHandler will be interpreted as a
	 * model attribute.
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request, HttpServletResponse response,
			HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;
		checkRequest(request);

		// Execute invokeHandlerMethod in synchronized block if required.
		if (this.synchronizeOnSession) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			} else {
				// No HttpSession available -> no mutex necessary
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		} else {
			// No synchronization on session demanded at all...
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			} else {
				prepareResponse(response);
			}
		}

		return mav;
	}

	/**
	 * This implementation always returns -1. An {@code @RequestMapping} method can
	 * calculate the lastModified value, call
	 * {@link WebRequest#checkNotModified(long)}, and return {@code null} if the
	 * result of that call is {@code true}.
	 */
	@Override
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}

	/**
	 * Return the {@link SessionAttributesHandler} instance for the given handler
	 * type (never {@code null}).
	 */
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		Class<?> handlerType = handlerMethod.getBeanType();
		SessionAttributesHandler sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
		if (sessionAttrHandler == null) {
			synchronized (this.sessionAttributesHandlerCache) {
				sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
				if (sessionAttrHandler == null) {
					sessionAttrHandler = new SessionAttributesHandler(handlerType, sessionAttributeStore);
					this.sessionAttributesHandlerCache.put(handlerType, sessionAttrHandler);
				}
			}
		}
		return sessionAttrHandler;
	}

	/**
	 * Invoke the {@link RequestMapping} handler method preparing a
	 * {@link ModelAndView} if view resolution is required.
	 * 
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	@Nullable
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request, HttpServletResponse response,
			HandlerMethod handlerMethod) throws Exception {

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		try {
			// 创建一个WebDataBinderFactory 
			// Global methods first（放在前面最先执行） 然后再执行本类自己的
			// 最终创建的是一个ServletRequestDataBinderFactory，持有所有@InitBinder的method方法们
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);
			// 每调用一次都会生成一个ModelFactory ~~~
			ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);

			ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
			if (this.argumentResolvers != null) {
				invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
			}
			if (this.returnValueHandlers != null) {
				invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
			}
			invocableMethod.setDataBinderFactory(binderFactory);
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);

			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
			// 初始化Model
			modelFactory.initModel(webRequest, mavContainer, invocableMethod);
			mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);

			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);

			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);
			// 它不管是不是异步请求都先用AsyncWebRequest 包装了一下，但是若是同步请求
			// asyncManager.hasConcurrentResult()肯定是为false的~~~
			if (asyncManager.hasConcurrentResult()) {
				Object result = asyncManager.getConcurrentResult();
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				asyncManager.clearConcurrentResult();
				if (logger.isDebugEnabled()) {
					logger.debug("Found concurrent result value [" + result + "]");
				}
				invocableMethod = invocableMethod.wrapConcurrentResult(result);
			}
			// 此处其实就是调用ServletInvocableHandlerMethod#invokeAndHandle()方法喽
						// 关于它你可以来这里：https://fangshixiang.blog.csdn.net/article/details/98385163
						// 注意哦：任何HandlerMethod执行完后都是把结果放在了mavContainer里（它可能有Model，可能有View，可能啥都木有~~）
						// 因此最后的getModelAndView()又得一看
	 
			invocableMethod.invokeAndHandle(webRequest, mavContainer);
			if (asyncManager.isConcurrentHandlingStarted()) {
				return null;
			}

			return getModelAndView(mavContainer, modelFactory, webRequest);
		} finally {
			webRequest.requestCompleted();
		}
	}

	/**
	 * Create a {@link ServletInvocableHandlerMethod} from the given
	 * {@link HandlerMethod} definition.
	 * 
	 * @param handlerMethod the {@link HandlerMethod} definition
	 * @return the corresponding {@link ServletInvocableHandlerMethod} (or custom
	 *         subclass thereof)
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
		return new ServletInvocableHandlerMethod(handlerMethod);
	}
	// 创建出一个ModelFactory，来管理Model
	// 显然和Model相关的就会有@ModelAttribute @SessionAttributes等注解啦~
	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
		// 从缓存中拿到和此Handler相关的SessionAttributesHandler处理器~~处理SessionAttr
		SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
		Class<?> handlerType = handlerMethod.getBeanType();
		// 找到当前类（Controller）所有的标注的@ModelAttribute注解的方法
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
			this.modelAttributeCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> attrMethods = new ArrayList<>();
		// Global methods first
		this.modelAttributeAdviceCache.forEach((clazz, methodSet) -> {
			if (clazz.isApplicableToBeanType(handlerType)) {
				Object bean = clazz.resolveBean();
				for (Method method : methodSet) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		});
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}

	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean,
			Method method) {
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
		if (this.argumentResolvers != null) {
			attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		attrMethod.setDataBinderFactory(factory);
		return attrMethod;
	}

	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		// handlerType：方法所在的类（控制器方法所在的类，也就是xxxController）
		// 由此可见，此注解的作用范围是类级别的。会用此作为key来缓存
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.initBinderCache.get(handlerType);
		// 缓存没命中，就去selectMethods找到所有标注有@InitBinder的方法们~~~~
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			this.initBinderCache.put(handlerType, methods);
		}
		// 此处注意：Method最终都被包装成了InvocableHandlerMethod，从而具有执行的能力
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();
		// Global methods first
		// 上面找了本类的，现在开始看看全局里有木有@InitBinder
		// Global methods first（先把全局的放进去，再放个性化的~~~~ 所以小细节：有覆盖的效果哟~~~）
		// initBinderAdviceCache它是一个缓存LinkedHashMap(有序哦~~~)，缓存着作用于全局的类。
		// 如@ControllerAdvice，注意和`RequestBodyAdvice`、`ResponseBodyAdvice`区分开来

		// methodSet：说明一个类里面是可以定义N多个标注有@InitBinder的方法~~~~~
 
		this.initBinderAdviceCache.forEach((clazz, methodSet) -> {
			if (clazz.isApplicableToBeanType(handlerType)) {
				// 这个resolveBean() 有点意思：它持有的Bean若是个BeanName的话，会getBean()一下的
				// 大多数情况下都是BeanName，这在@ControllerAdvice的初始化时会讲~~~
				Object bean = clazz.resolveBean();
				for (Method method : methodSet) {
					initBinderMethods.add(createInitBinderMethod(bean, method));
				}
			}
		});
		for (Method method : methods) {
			
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}
		return createDataBinderFactory(initBinderMethods);
	}

	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		if (this.initBinderArgumentResolvers != null) {
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * Template method to create a new InitBinderDataBinderFactory instance.
	 * <p>
	 * The default implementation creates a ServletRequestDataBinderFactory. This
	 * can be overridden for custom ServletRequestDataBinder subclasses.
	 * 
	 * @param binderMethods {@code @InitBinder} methods
	 * @return the InitBinderDataBinderFactory instance to use
	 * @throws Exception in case of invalid state or arguments
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {

		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer());
	}

	@Nullable
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer, ModelFactory modelFactory,
			NativeWebRequest webRequest) throws Exception {
		// 把session里面的内容写入
		//// 将列为@SessionAttributes的模型属性提升到会话
		modelFactory.updateModel(webRequest, mavContainer);
		// Tips：若已经被处理过，那就返回null喽~~（比如若是@ResponseBody这种，这里就是true）
		// 真正的View 可见ModelMap/视图名称、状态HttpStatus最终都交给了Veiw去渲染
		if (mavContainer.isRequestHandled()) {
			return null;
		}// 通过View、Model、Status构造出一个ModelAndView，最终就可以完成渲染了
		ModelMap model = mavContainer.getModel();
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
		// 这个步骤：是Spring MVC对重定向的支持~~~~
				// 重定向之间传值，使用的RedirectAttributes这种Model~~~~
		if (!mavContainer.isViewReference()) {
			mav.setView((View) mavContainer.getView());
		}	// 对重定向RedirectAttributes参数的支持（两个请求之间传递参数，使用的是ATTRIBUTE）
		if (model instanceof RedirectAttributes) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
		}
		return mav;
	}

}
