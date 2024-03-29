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

package org.springframework.web.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

/**
 * Central dispatcher for HTTP request handlers/controllers, e.g. for web UI
 * controllers or HTTP-based remote service exporters. Dispatches to registered
 * handlers for processing a web request, providing convenient mapping and
 * exception handling facilities.
 *
 * <p>
 * This servlet is very flexible: It can be used with just about any workflow,
 * with the installation of the appropriate adapter classes. It offers the
 * following functionality that distinguishes it from other request-driven web
 * MVC frameworks:
 *
 * <ul>
 * <li>It is based around a JavaBeans configuration mechanism.
 *
 * <li>It can use any {@link HandlerMapping} implementation - pre-built or
 * provided as part of an application - to control the routing of requests to
 * handler objects. Default is
 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping} and
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * HandlerMapping objects can be defined as beans in the servlet's application
 * context, implementing the HandlerMapping interface, overriding the default
 * HandlerMapping if present. HandlerMappings can be given any bean name (they
 * are tested by type).
 *
 * <li>It can use any {@link HandlerAdapter}; this allows for using any handler
 * interface. Default adapters are
 * {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter},
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter},
 * for Spring's {@link org.springframework.web.HttpRequestHandler} and
 * {@link org.springframework.web.servlet.mvc.Controller} interfaces,
 * respectively. A default
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * will be registered as well. HandlerAdapter objects can be added as beans in
 * the application context, overriding the default HandlerAdapters. Like
 * HandlerMappings, HandlerAdapters can be given any bean name (they are tested
 * by type).
 *
 * <li>The dispatcher's exception resolution strategy can be specified via a
 * {@link HandlerExceptionResolver}, for example mapping certain exceptions to
 * error pages. Default are
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver},
 * {@link org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver},
 * and
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}.
 * These HandlerExceptionResolvers can be overridden through the application
 * context. HandlerExceptionResolver can be given any bean name (they are tested
 * by type).
 *
 * <li>Its view resolution strategy can be specified via a {@link ViewResolver}
 * implementation, resolving symbolic view names into View objects. Default is
 * {@link org.springframework.web.servlet.view.InternalResourceViewResolver}.
 * ViewResolver objects can be added as beans in the application context,
 * overriding the default ViewResolver. ViewResolvers can be given any bean name
 * (they are tested by type).
 *
 * <li>If a {@link View} or view name is not supplied by the user, then the
 * configured {@link RequestToViewNameTranslator} will translate the current
 * request into a view name. The corresponding bean name is
 * "viewNameTranslator"; the default is
 * {@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}.
 *
 * <li>The dispatcher's strategy for resolving multipart requests is determined
 * by a {@link org.springframework.web.multipart.MultipartResolver}
 * implementation. Implementations for Apache Commons FileUpload and Servlet 3
 * are included; the typical choice is
 * {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}.
 * The MultipartResolver bean name is "multipartResolver"; default is none.
 *
 * <li>Its locale resolution strategy is determined by a {@link LocaleResolver}.
 * Out-of-the-box implementations work via HTTP accept header, cookie, or
 * session. The LocaleResolver bean name is "localeResolver"; default is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
 *
 * <li>Its theme resolution strategy is determined by a {@link ThemeResolver}.
 * Implementations for a fixed theme and for cookie and session storage are
 * included. The ThemeResolver bean name is "themeResolver"; default is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver}.
 * </ul>
 *
 * <p>
 * <b>NOTE: The {@code @RequestMapping} annotation will only be processed if a
 * corresponding {@code HandlerMapping} (for type-level annotations) and/or
 * {@code HandlerAdapter} (for method-level annotations) is present in the
 * dispatcher.</b> This is the case by default. However, if you are defining
 * custom {@code HandlerMappings} or {@code HandlerAdapters}, then you need to
 * make sure that a corresponding custom {@code RequestMappingHandlerMapping}
 * and/or {@code RequestMappingHandlerAdapter} is defined as well - provided
 * that you intend to use {@code @RequestMapping}.
 *
 * <p>
 * <b>A web application can define any number of DispatcherServlets.</b> Each
 * servlet will operate in its own namespace, loading its own application
 * context with mappings, handlers, etc. Only the root application context as
 * loaded by {@link org.springframework.web.context.ContextLoaderListener}, if
 * any, will be shared.
 *
 * <p>
 * As of Spring 3.1, {@code DispatcherServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful
 * in Servlet 3.0+ environments, which support programmatic registration of
 * servlet instances. See the {@link #DispatcherServlet(WebApplicationContext)}
 * javadoc for details.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @see org.springframework.web.HttpRequestHandler
 * @see org.springframework.web.servlet.mvc.Controller
 * @see org.springframework.web.context.ContextLoaderListener
 * 
 * Spring工作流程描述
	1.用户向服务器发送请求，请求被Spring 前端控制Servelt DispatcherServlet捕获；
	2.DispatcherServlet对请求URL进行解析，得到请求资源标识符（URI）。然后根据该URI，调用HandlerMapping获得该Handler配置的所有相关的对象（包括Handler对象以及Handler对象对应的拦截器），最后以HandlerExecutionChain对象的形式返回；
	3.DispatcherServlet 根据获得的Handler，选择一个合适的HandlerAdapter。（附注：如果成功获得HandlerAdapter后，此时将开始执行拦截器的preHandler(…)方法）
	4.提取Request中的模型数据，填充Handler入参，开始执行Handler（Controller)。 在填充Handler的入参过程中，根据你的配置，Spring将帮你做一些额外的工作：
	5.HttpMessageConveter： 将请求消息（如Json、xml等数据）转换成一个对象，将对象转换为指定的响应信息
	6.数据转换：对请求消息进行数据转换。如String转换成Integer、Double等
		数据根式化：对请求消息进行数据格式化。 如将字符串转换成格式化数字或格式化日期等
		数据验证： 验证数据的有效性（长度、格式等），验证结果存储到BindingResult或Error中
	7.Handler执行完成后，向DispatcherServlet 返回一个ModelAndView对象；
	8.根据返回的ModelAndView，选择一个适合的ViewResolver（必须是已经注册到Spring容器中的ViewResolver)返回给DispatcherServlet ；
	ViewResolver 结合Model和View，来渲染视图
	将渲染结果返回给客户端。
	 * 
	 * 
	*/
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {

	/**
	 * Well-known name for the MultipartResolver object in the bean factory for this
	 * namespace.
	 */
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

	/**
	 * Well-known name for the LocaleResolver object in the bean factory for this
	 * namespace.
	 */
	public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

	/**
	 * Well-known name for the ThemeResolver object in the bean factory for this
	 * namespace.
	 */
	public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

	/**
	 * Well-known name for the HandlerMapping object in the bean factory for this
	 * detectAllHandlerMappings 参数用来
	 * 判断是否启用所有的HandlerMapping。可以通过这个参数来控制是使用指定的HandlerMapping，还是检索所有的HandlerMapping。
	 * namespace. Only used when "detectAllHandlerMappings" is turned off.
	 * 
	 * @see #setDetectAllHandlerMappings
	 */
	public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

	/**
	 * Well-known name for the HandlerAdapter object in the bean factory for this
	 * namespace. Only used when "detectAllHandlerAdapters" is turned off.
	 * 
	 * @see #setDetectAllHandlerAdapters
	 */
	public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

	/**
	 * Well-known name for the HandlerExceptionResolver object in the bean factory
	 * for this namespace. Only used when "detectAllHandlerExceptionResolvers" is
	 * turned off.
	 * 
	 * @see #setDetectAllHandlerExceptionResolvers
	 */
	public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

	/**
	 * Well-known name for the RequestToViewNameTranslator object in the bean
	 * factory for this namespace.
	 */
	public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

	/**
	 * Well-known name for the ViewResolver object in the bean factory for this
	 * namespace. Only used when "detectAllViewResolvers" is turned off.
	 * 
	 * @see #setDetectAllViewResolvers
	 */
	public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

	/**
	 * Well-known name for the FlashMapManager object in the bean factory for this
	 * namespace.
	 */
	public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

	/**
	 * Request attribute to hold the current web application context. Otherwise only
	 * the global web app context is obtainable by tags etc.
	 * 
	 * @see org.springframework.web.servlet.support.RequestContextUtils#findWebApplicationContext
	 */
	public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

	/**
	 * Request attribute to hold the current LocaleResolver, retrievable by views.
	 * 
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocaleResolver
	 */
	public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeResolver, retrievable by views.
	 * 
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeResolver
	 */
	public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeSource, retrievable by views.
	 * 
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeSource
	 */
	public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

	/**
	 * Name of request attribute that holds a read-only {@code Map<String,?>} with
	 * "input" flash attributes saved by a previous request, if any.
	 * 
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getInputFlashMap(HttpServletRequest)
	 */
	public static final String INPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the "output" {@link FlashMap} with
	 * attributes to save for a subsequent request.
	 * 
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getOutputFlashMap(HttpServletRequest)
	 */
	public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the {@link FlashMapManager}.
	 * 
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getFlashMapManager(HttpServletRequest)
	 */
	public static final String FLASH_MAP_MANAGER_ATTRIBUTE = DispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

	/**
	 * Name of request attribute that exposes an Exception resolved with an
	 * {@link HandlerExceptionResolver} but where no view was rendered (e.g. setting
	 * the status code).
	 */
	public static final String EXCEPTION_ATTRIBUTE = DispatcherServlet.class.getName() + ".EXCEPTION";

	/** Log category to use when no mapped handler is found for a request. */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Name of the class path resource (relative to the DispatcherServlet class)
	 * that defines DispatcherServlet's default strategy names.
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

	/**
	 * Common prefix that DispatcherServlet's default strategy attributes start
	 * with.
	 */
	private static final String DEFAULT_STRATEGIES_PREFIX = "org.springframework.web.servlet";

	/** Additional logger to use when no mapped handler is found for a request. */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	private static final Properties defaultStrategies;

	static {
		// Load default strategy implementations from properties file.
		// This is currently strictly internal and not meant to be customized
		// by application developers.
		try {
			// 初始化资源配置文件
			ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
			defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
		}
	}

	/** Detect all HandlerMappings or just expect "handlerMapping" bean? */
	private boolean detectAllHandlerMappings = true;

	/** Detect all HandlerAdapters or just expect "handlerAdapter" bean? */
	private boolean detectAllHandlerAdapters = true;

	/**
	 * Detect all HandlerExceptionResolvers or just expect
	 * "handlerExceptionResolver" bean?
	 */
	private boolean detectAllHandlerExceptionResolvers = true;

	/** Detect all ViewResolvers or just expect "viewResolver" bean? */
	private boolean detectAllViewResolvers = true;

	/**
	 * Throw a NoHandlerFoundException if no Handler was found to process this
	 * request?
	 **/
	private boolean throwExceptionIfNoHandlerFound = false;

	/** Perform cleanup of request attributes after include request? */
	private boolean cleanupAfterInclude = true;

	/** MultipartResolver used by this servlet */
	@Nullable
	private MultipartResolver multipartResolver;

	/** LocaleResolver used by this servlet */
	@Nullable
	private LocaleResolver localeResolver;

	/** ThemeResolver used by this servlet */
	@Nullable
	private ThemeResolver themeResolver;

	/** List of HandlerMappings used by this servlet */
	@Nullable
	private List<HandlerMapping> handlerMappings;

	/** List of HandlerAdapters used by this servlet */
	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	/** List of HandlerExceptionResolvers used by this servlet */
	@Nullable
	private List<HandlerExceptionResolver> handlerExceptionResolvers;

	/** RequestToViewNameTranslator used by this servlet */
	@Nullable
	private RequestToViewNameTranslator viewNameTranslator;

	/** FlashMapManager used by this servlet */
	@Nullable
	private FlashMapManager flashMapManager;

	/** List of ViewResolvers used by this servlet */
	@Nullable
	private List<ViewResolver> viewResolvers;

	/**
	 * Create a new {@code DispatcherServlet} that will create its own internal web
	 * application context based on defaults and values provided through servlet
	 * init-params. Typically used in Servlet 2.5 or earlier environments, where the
	 * only option for servlet registration is through {@code web.xml} which
	 * requires the use of a no-arg constructor.
	 * <p>
	 * Calling {@link #setContextConfigLocation} (init-param
	 * 'contextConfigLocation') will dictate which XML files will be loaded by the
	 * {@linkplain #DEFAULT_CONTEXT_CLASS default XmlWebApplicationContext}
	 * <p>
	 * Calling {@link #setContextClass} (init-param 'contextClass') overrides the
	 * default {@code XmlWebApplicationContext} and allows for specifying an
	 * alternative class, such as {@code AnnotationConfigWebApplicationContext}.
	 * <p>
	 * Calling {@link #setContextInitializerClasses} (init-param
	 * 'contextInitializerClasses') indicates which
	 * {@code ApplicationContextInitializer} classes should be used to further
	 * configure the internal application context prior to refresh().
	 * 
	 * @see #DispatcherServlet(WebApplicationContext)
	 */
	public DispatcherServlet() {
		super();
		setDispatchOptionsRequest(true);
	}

	/**
	 * Create a new {@code DispatcherServlet} with the given web application
	 * context. This constructor is useful in Servlet 3.0+ environments where
	 * instance-based registration of servlets is possible through the
	 * {@link ServletContext#addServlet} API.
	 * <p>
	 * Using this constructor indicates that the following properties / init-params
	 * will be ignored:
	 * <ul>
	 * <li>{@link #setContextClass(Class)} / 'contextClass'</li>
	 * <li>{@link #setContextConfigLocation(String)} / 'contextConfigLocation'</li>
	 * <li>{@link #setContextAttribute(String)} / 'contextAttribute'</li>
	 * <li>{@link #setNamespace(String)} / 'namespace'</li>
	 * </ul>
	 * <p>
	 * The given web application context may or may not yet be
	 * {@linkplain ConfigurableApplicationContext#refresh() refreshed}. If it has
	 * <strong>not</strong> already been refreshed (the recommended approach), then
	 * the following will occur:
	 * <ul>
	 * <li>If the given context does not already have a
	 * {@linkplain ConfigurableApplicationContext#setParent parent}, the root
	 * application context will be set as the parent.</li>
	 * <li>If the given context has not already been assigned an
	 * {@linkplain ConfigurableApplicationContext#setId id}, one will be assigned to
	 * it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be
	 * delegated to the application context</li>
	 * <li>{@link #postProcessWebApplicationContext} will be called</li>
	 * <li>Any {@code ApplicationContextInitializer}s specified through the
	 * "contextInitializerClasses" init-param or through the
	 * {@link #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called
	 * if the context implements {@link ConfigurableApplicationContext}</li>
	 * </ul>
	 * If the context has already been refreshed, none of the above will occur,
	 * under the assumption that the user has performed these actions (or not) per
	 * their specific needs.
	 * <p>
	 * See {@link org.springframework.web.WebApplicationInitializer} for usage
	 * examples.
	 * 
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public DispatcherServlet(WebApplicationContext webApplicationContext) {
		super(webApplicationContext);
		setDispatchOptionsRequest(true);
	}

	/**
	 * Set whether to detect all HandlerMapping beans in this servlet's context.
	 * Otherwise, just a single bean with name "handlerMapping" will be expected.
	 * <p>
	 * Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerMapping, despite multiple HandlerMapping beans being defined in the
	 * context.
	 */
	public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
		this.detectAllHandlerMappings = detectAllHandlerMappings;
	}

	/**
	 * Set whether to detect all HandlerAdapter beans in this servlet's context.
	 * Otherwise, just a single bean with name "handlerAdapter" will be expected.
	 * <p>
	 * Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerAdapter, despite multiple HandlerAdapter beans being defined in the
	 * context.
	 */
	public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
		this.detectAllHandlerAdapters = detectAllHandlerAdapters;
	}

	/**
	 * Set whether to detect all HandlerExceptionResolver beans in this servlet's
	 * context. Otherwise, just a single bean with name "handlerExceptionResolver"
	 * will be expected.
	 * <p>
	 * Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerExceptionResolver, despite multiple HandlerExceptionResolver beans
	 * being defined in the context.
	 */
	public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
		this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
	}

	/**
	 * Set whether to detect all ViewResolver beans in this servlet's context.
	 * Otherwise, just a single bean with name "viewResolver" will be expected.
	 * <p>
	 * Default is "true". Turn this off if you want this servlet to use a single
	 * ViewResolver, despite multiple ViewResolver beans being defined in the
	 * context.
	 */
	public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
		this.detectAllViewResolvers = detectAllViewResolvers;
	}

	/**
	 * Set whether to throw a NoHandlerFoundException when no Handler was found for
	 * this request. This exception can then be caught with a
	 * HandlerExceptionResolver or an {@code @ExceptionHandler} controller method.
	 * <p>
	 * Note that if
	 * {@link org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler}
	 * is used, then requests will always be forwarded to the default servlet and a
	 * NoHandlerFoundException would never be thrown in that case.
	 * <p>
	 * Default is "false", meaning the DispatcherServlet sends a NOT_FOUND error
	 * through the Servlet response.
	 * 
	 * @since 4.0
	 */
	public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
		this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
	}

	/**
	 * Set whether to perform cleanup of request attributes after an include
	 * request, that is, whether to reset the original state of all request
	 * attributes after the DispatcherServlet has processed within an include
	 * request. Otherwise, just the DispatcherServlet's own request attributes will
	 * be reset, but not model attributes for JSPs or special attributes set by
	 * views (for example, JSTL's).
	 * <p>
	 * Default is "true", which is strongly recommended. Views should not rely on
	 * request attributes having been set by (dynamic) includes. This allows JSP
	 * views rendered by an included controller to use any model attributes, even
	 * with the same names as in the main JSP, without causing side effects. Only
	 * turn this off for special needs, for example to deliberately allow main JSPs
	 * to access attributes from JSP views rendered by an included controller.
	 */
	public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
		this.cleanupAfterInclude = cleanupAfterInclude;
	}

	/**
	 * This implementation calls {@link #initStrategies}.
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		initStrategies(context);
	}

	/**
	 * Initialize the strategy objects that this servlet uses.
	 * <p>
	 * May be overridden in subclasses in order to initialize further strategy
	 * objects.
	 */
	protected void initStrategies(ApplicationContext context) {
//		initMultipartResolver(context);
//		initLocaleResolver(context);
//		initThemeResolver(context);
//		initHandlerMappings(context);
//		initHandlerAdapters(context);
//		initHandlerExceptionResolvers(context);
//		initRequestToViewNameTranslator(context);
//		initViewResolvers(context);
//		initFlashMapManager(context);
		////文件上传解析，如果请求类型是multipart将通过MultipartResolver进行文件上传解析
		initMultipartResolver(context);
		// 初始化国际化解析器
		initLocaleResolver(context);
		// 初始化主题解析器
		initThemeResolver(context);
		// 初始化 HandlerMappering
		initHandlerMappings(context);
		// 初始化 HandlerAdapter
		initHandlerAdapters(context);
		// 初始化 Handler异常解析器
		initHandlerExceptionResolvers(context);
		// 初始化RequestToViewNameTranslator
		initRequestToViewNameTranslator(context);
		// 初始化 视图解析器
		initViewResolvers(context);
		// 初始化 FlashMapManager
		initFlashMapManager(context);
	}

	/**
	 * Initialize the MultipartResolver used by this class.
	 * <p>
	 * If no bean is defined with the given name in the BeanFactory for this
	 * namespace, no multipart handling is provided.
	 */
	private void initMultipartResolver(ApplicationContext context) {
		try {
			//bean id被写死，在配置的时候需要注意,文件上传时需要注入bean名称为multipartResolver的类
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using MultipartResolver [" + this.multipartResolver + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			this.multipartResolver = null;
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate MultipartResolver with name '" + MULTIPART_RESOLVER_BEAN_NAME
						+ "': no multipart request handling provided");
			}
		}
	}

	/**
	 * 般情况下， localeResolver 有三种注入实例
	 * 
	 * AcceptHeaderLocaleResolver ： 基于URL 参数的配置 。他会根据请求的URL后缀来判断国际化场景。比如 ：
	 * “http://xxx?local=zh_CN”。local参数也可以是en_US。 CookieLocaleResolver ：
	 * 基于Cookie的国际化配置。他是通过浏览器的 Cookies 设置取得Local对象。 SessionLocaleResolver ：基于
	 * Session 的配置，他通过验证用户会话中预置的属性来解析区域。常用的是根据用户本次会话过程中语言来决定语言种类。如果该会话属性不存在，则会根据
	 * http的 accept-language 请求头确认国际化场景。 Initialize the LocaleResolver used by this
	 * class.
	 * <p>
	 * If no bean is defined with the given name in the BeanFactory for this
	 * namespace, we default to AcceptHeaderLocaleResolver.
	 */
	private void initLocaleResolver(ApplicationContext context) {
		try {
			//在配置多语言本地化时会注入bean名称为localeResolver，默认实现的类有FixedLocaleResolver ，SessionLocaleResolver ，CookieLocaleResolver， AcceptHeaderLocaleResolver 
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using LocaleResolver [" + this.localeResolver + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate LocaleResolver with name '" + LOCALE_RESOLVER_BEAN_NAME
						+ "': using default [" + this.localeResolver + "]");
			}
		}
	}

	/**
	 * FixedThemeResolver ：用于选择一个固定的主题 SessionThemeResolver ：用于主题保存在用户的http session中
	 * CookieThemeResolver ：实现用户所选的主题，以cookie的形式存放在客户端的机器上 Initialize the
	 * ThemeResolver used by this class.
	 * <p>
	 * If no bean is defined with the given name in the BeanFactory for this
	 * namespace, we default to a FixedThemeResolver.
	 */
	private void initThemeResolver(ApplicationContext context) {
		try {
			//需要在配置文件中注入bean名称为themeResolver的，FixedThemeResolver, SessionThemeResolver和CookieThemeResolver

			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using ThemeResolver [" + this.themeResolver + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ThemeResolver with name '" + THEME_RESOLVER_BEAN_NAME
						+ "': using default [" + this.themeResolver + "]");
			}
		}
	}

	/**
	 * 在基于Spring mvc 的web应用程序中，我们可以为DispatcherServlet 提供多个 HandlerMapping 供其使用。
	 * DispatcherServlet 在选用 HandlerMapping 的过程中，将会根据我们所指定的一系列Handler 的优先级进行排序，
	 * 然后优先使用优先级在前的HandlerMapping。
	 * 如果当前HandlerMapping能够返回可用的Handler，DispatcherServlet 则是使用当前返回的Handler
	 * 进行Web请求的处理，而不再询问其他HandlerMapping， 否则DispatcherServlet将按照各个HandlerMapping
	 * 的优先级进行询问，知道获取到一个可用的Handler 为止。 Initialize the HandlerMappings used by this
	 * class.
	 * <p>
	 * If no HandlerMapping beans are defined in the BeanFactory for this namespace,
	 * we default to BeanNameUrlHandlerMapping.
	 * 
	 * 关于几种HandlerMapping，我们这里来简单看看。具体后续开设衍生篇来看 BeanNameUrlHandlerMapping ：以beanName
	 * 作为key值 RequestMappingHandlerMapping ：完成@Controller和@RequestMapping
	 * 的解析，并将解析保存。 请求发送时与请求路径进行匹配对应找到合适的Handler。 RequestMappingHandlerMapping 实现了
	 * InitializingBean 接口， 会在afterPropertiesSet 方法中调用时机:
	 * 解析@Controller和@RequestMapping注解是在 afterPropertiesSet方法中进行的。 匹配调用则是在
	 * DispatcherServlet
	 * doDispatch方法中的getHandler中调用了HandlerMapper中的getHandler中的getHandlerInternal方法。
	 * SimpleUrlHandlerMapping :基本逻辑是通过注入SimpleurlHandlerMapping 的mapping属性， mapping
	 * key为url, value为handler(beanName)。这里需要注意Controller必须要实现Controller接口。
	 * 
	 * 
	 */
	private void initHandlerMappings(ApplicationContext context) {
		this.handlerMappings = null;
		// 如果启用所有的HandlerMapping。可以通过这个参数来控制是使用指定的HandlerMapping，还是检索所有的
		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor
			// contexts.
			// 寻找所有的HandlerMapping类型的类
			Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
					HandlerMapping.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				// 按照优先级进行排序
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		} else {
			// 如果使用指定的参数，从容器中获取beanName 为 handlerMapping 的HandlerMapping
			try {
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		// 如果handlerMappings 为null。则使用默认策略指定的HandlerMapping
		if (this.handlerMappings == null) {
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerMappings found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * <p>
	 * If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 */
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;
		// 如果启用所有的HandlerAdapter。可以通过这个参数来控制是使用指定的HandlerMapping，还是检索所有的
		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor
			// contexts.
			// 寻找所有的适配器并排序
			Map<String, HandlerAdapter> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
					HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		} else {
			try {
				// 没有启用则从Spring 容器中获取 beanName = handlerAdapter 并且类型是 HandlerAdapter 类型的bean。并
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}

		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		// 如果还没有获取到适配器，则使用默认策略的适配器。从 DispatcherServlet.properties 中获取
		// org.springframework.web.servlet.HandlerAdapter 为key值的value加载到容器中。
		if (this.handlerAdapters == null) {
//			这里我们简单介绍一下三个 HandlerAdapter
//
//			HttpRequestHandlerAdapter : Http请求处理器适配器。
//			HTTP请求处理适配器仅仅支持 HTTP 请求处理器的适配。他简单的将HTTP请求对象和响应对象传递给HTTP请求处理器的实现，他并不需要返回值。主要应用在基于 HTTP的远程调用实现上。
//			SimpleControllerHandlerAdapter ： 简单控制器处理器适配器
//			这个实现类将HTTP请求适配到了一个控制器的实现进行处理。这里的控制器的实现是一个简单的控制器接口的 实现。简单控制器处理器适配器被设计成一个框架类的实现，不需要被改写，客户化的业务逻辑通常在控制器接口的实现类中实现的。
//			RequestMappingHandlerAdapter ： 请求映射处理器适配器
//			这个实现类需要通过注解方法映射和注解方法处理器协同工作。它通过解析声明在注解控制器的请求映射信息来解析相应的处理器方法来处理当前的http请求，在处理的过程中，他通过反射来发现探测处理器方法的参数，调用处理器方法，并映射返回值到模型和控制器对象。最后返回模型和控制器对象给作为主控制器的派遣器Servlet。
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerAdapters found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**	// 寻找逻辑（detectAllHandlerExceptionResolvers默认值是true表示回去容器里寻找）：
	// 1、若detect = true（默认是true），去容器里找出所有`HandlerExceptionResolver`类型的Bean们，找到后排序
	// 2、若detect = false（可手动更改），那就拿名称为`handlerExceptionResolver`这单独的一个Bean（context.getBean()）
	// 3、如果一个都木有找到，那就走默认策略getDefaultStrategies()，详见下面截图~~~
开启@EnableWebMvc后，使用的异常处理器是HandlerExceptionResolverComposite。截图如下：
	 * Initialize the HandlerExceptionResolver used by this class.
	 * <p>
	 * If no bean is defined with the given name in the BeanFactory for this
	 * namespace, we default to no exception resolver.
	 */
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		this.handlerExceptionResolvers = null;

		if (this.detectAllHandlerExceptionResolvers) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including
			// ancestor contexts.
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		} else {
			try {
				HandlerExceptionResolver her = context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME,
						HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
			}
		}

		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		if (this.handlerExceptionResolvers == null) {
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerExceptionResolvers found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * 当Controller处理器方法没有返回一个View对象或者逻辑视图名称，并且在该方法中没有直接放Response
	 * 的输出流中写数据的时候，Spring就会采用约定好的方式提供一个逻辑视图名称。这个逻辑视图名称是通过 Spring 定义的
	 * org.springframework.web.servlet.RequestToViewNameTranslator 接口的 getViewName
	 * 方法实现的。Spring默认提供了一个实现
	 * org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator
	 * 可以看一下其支持的属性 Initialize the RequestToViewNameTranslator used by this servlet
	 * instance.
	 * <p>
	 * If no implementation is configured then we default to
	 * DefaultRequestToViewNameTranslator.
	 */
	private void initRequestToViewNameTranslator(ApplicationContext context) {
		try {
			this.viewNameTranslator = context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME,
					RequestToViewNameTranslator.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using RequestToViewNameTranslator [" + this.viewNameTranslator + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate RequestToViewNameTranslator with name '"
						+ REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME + "': using default [" + this.viewNameTranslator
						+ "]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * <p>
	 * If no ViewResolver beans are defined in the BeanFactory for this namespace,
	 * we default to InternalResourceViewResolver.
	 */
	private void initViewResolvers(ApplicationContext context) {
		this.viewResolvers = null;

		if (this.detectAllViewResolvers) {
			// Find all ViewResolvers in the ApplicationContext, including ancestor
			// contexts.
			Map<String, ViewResolver> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
					ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				// We keep ViewResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		} else {
			try {
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			} catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later.
			}
		}

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		if (this.viewResolvers == null) {
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No ViewResolvers found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * <p>
	 * If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 */
	private void initFlashMapManager(ApplicationContext context) {
		try {
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME, FlashMapManager.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using FlashMapManager [" + this.flashMapManager + "]");
			}
		} catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate FlashMapManager with name '" + FLASH_MAP_MANAGER_BEAN_NAME
						+ "': using default [" + this.flashMapManager + "]");
			}
		}
	}

	/**
	 * Return this servlet's ThemeSource, if any; else return {@code null}.
	 * <p>
	 * Default is to return the WebApplicationContext as ThemeSource, provided that
	 * it implements the ThemeSource interface.
	 * 
	 * @return the ThemeSource, if any
	 * @see #getWebApplicationContext()
	 */
	@Nullable
	public final ThemeSource getThemeSource() {
		return (getWebApplicationContext() instanceof ThemeSource ? (ThemeSource) getWebApplicationContext() : null);
	}

	/**
	 * Obtain this servlet's MultipartResolver, if any.
	 * 
	 * @return the MultipartResolver used by this servlet, or {@code null} if none
	 *         (indicating that no multipart support is available)
	 */
	@Nullable
	public final MultipartResolver getMultipartResolver() {
		return this.multipartResolver;
	}

	/**
	 * Return the configured {@link HandlerMapping} beans that were detected by type
	 * in the {@link WebApplicationContext} or initialized based on the default set
	 * of strategies from {@literal DispatcherServlet.properties}.
	 * <p>
	 * <strong>Note:</strong> This method may return {@code null} if invoked prior
	 * to {@link #onRefresh(ApplicationContext)}.
	 * 
	 * @return an immutable list with the configured mappings, or {@code null} if
	 *         not initialized yet
	 * @since 5.0
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return (this.handlerMappings != null ? Collections.unmodifiableList(this.handlerMappings) : null);
	}

	/**
	 * Return the default strategy object for the given strategy interface.
	 * <p>
	 * The default implementation delegates to {@link #getDefaultStrategies},
	 * expecting a single object in the list.
	 * 
	 * @param context           the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the corresponding strategy object
	 * @see #getDefaultStrategies
	 */
	protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
		List<T> strategies = getDefaultStrategies(context, strategyInterface);
		if (strategies.size() != 1) {
			throw new BeanInitializationException(
					"DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
		}
		return strategies.get(0);
	}

	/**
	 * Create a List of default strategy objects for the given strategy interface.
	 * <p>
	 * The default implementation uses the "DispatcherServlet.properties" file (in
	 * the same package as the DispatcherServlet class) to determine the class
	 * names. It instantiates the strategy objects through the context's
	 * BeanFactory.
	 * 
	 * @param context           the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the List of corresponding strategy objects
	 */
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		// 获取Name
		String key = strategyInterface.getName();
		// 从配置文件中获取value
		String value = defaultStrategies.getProperty(key);
		// 获取到value 之后就是对value的处理，添加返回。
		if (value != null) {
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
			List<T> strategies = new ArrayList<>(classNames.length);
			for (String className : classNames) {
				try {
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
					//这里是通过Beanfactory工厂创建对象
					Object strategy = createDefaultStrategy(context, clazz);
					strategies.add((T) strategy);
				} catch (ClassNotFoundException ex) {
					throw new BeanInitializationException("Could not find DispatcherServlet's default strategy class ["
							+ className + "] for interface [" + key + "]", ex);
				} catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" + className
									+ "] for interface [" + key + "]",
							err);
				}
			}
			return strategies;
		} else {
			return new LinkedList<>();
		}
	}

	/**
	 * Create a default strategy.
	 * <p>
	 * The default implementation uses
	 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}.
	 * 
	 * @param context the current WebApplicationContext
	 * @param clazz   the strategy implementation class to instantiate
	 * @return the fully configured strategy instance
	 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean
	 */
	protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
		return context.getAutowireCapableBeanFactory().createBean(clazz);
	}

	/**
	 * Exposes the DispatcherServlet-specific request attributes and delegates to
	 * {@link #doDispatch} for the actual dispatching.
	 */
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (logger.isDebugEnabled()) {
			String resumed = WebAsyncUtils.getAsyncManager(request).hasConcurrentResult() ? " resumed" : "";
			logger.debug("DispatcherServlet with name '" + getServletName() + "'" + resumed + " processing "
					+ request.getMethod() + " request for [" + getRequestUri(request) + "]");
		}

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		Map<String, Object> attributesSnapshot = null;
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			Enumeration<?> attrNames = request.getAttributeNames();
			while (attrNames.hasMoreElements()) {
				String attrName = (String) attrNames.nextElement();
				if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
					attributesSnapshot.put(attrName, request.getAttribute(attrName));
				}
			}
		}

		// Make framework objects available to handlers and view objects.
		// 设置一些Spring 上下文
		/* 设置web应用上下文**/
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
		/* 国际化本地**/
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
		/* 样式**/
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
		//设置样式资源
		request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

		if (this.flashMapManager != null) {
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE, Collections.unmodifiableMap(inputFlashMap));
			}
			//Flash attributes 在对请求的重定向生效之前被临时存储（通常是在session)中，并且在重定向之后被立即移除
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
			//FlashMap 被用来管理 flash attributes 而 FlashMapManager 则被用来存储，获取和管理 FlashMap 实体
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE, this.flashMapManager);
		}

		try {
			doDispatch(request, response);
		} finally {
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
				// Restore the original attribute snapshot, in case of an include.
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
		}
	}

	 
	 /**
	 *将Handler进行分发，handler会被handlerMapping有序的获得
	 *通过查询servlet安装的HandlerAdapters来获得HandlerAdapters来查找第一个支持handler的类
	 *所有的HTTP的方法都会被这个方法掌控。取决于HandlerAdapters 或者handlers 他们自己去决定哪些方法是可用
	 *@param request current HTTP request
	 *@param response current HTTP response
 
	 * Process the actual dispatching to the handler.
	 * <p>
	 * The handler will be obtained by applying the servlet's HandlerMappings in
	 * order. The HandlerAdapter will be obtained by querying the servlet's
	 * installed HandlerAdapters to find the first that supports the handler class.
	 * <p>
	 * All HTTP methods are handled by this method. It's up to HandlerAdapters or
	 * handlers themselves to decide which methods are acceptable.
	 * 
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 */
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
				// 1. 如果是 MultipartContent 类型的request 则转换request 为 MultipartHttpServletRequest
				//判断是否有文件上传
				processedRequest = checkMultipart(request);
				multipartRequestParsed = (processedRequest != request);

				// Determine handler for the current request.
				// 获得HandlerExecutionChain，其包含HandlerIntercrptor和HandlerMethod
				mappedHandler = getHandler(processedRequest);
				if (mappedHandler == null) {
					noHandlerFound(processedRequest, response);
					return;
				}

				// Determine handler adapter for the current request.
				//获得HandlerAdapter
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				// Process last-modified header, if supported by the handler.
				//获得HTTP请求方法
				String method = request.getMethod();
				boolean isGet = "GET".equals(method);
				if (isGet || "HEAD".equals(method)) {
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (logger.isDebugEnabled()) {
						logger.debug("Last-Modified value for [" + getRequestUri(request) + "] is: " + lastModified);
					}
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}
				// 6.1 拦截器方法的调用
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				// 真正调用 handler 并 返回视图
				//返回ModelAndView
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}
				//视图名称转换应用于需要添加前缀的情况
				//当 控制层的返回结果是 null 或者 void 时，则表明没有找到对应视图，Spring 会根据request 信息来进行解析，查找默认的视图
				applyDefaultViewName(processedRequest, mv);
				// 6.2 拦截器后置方法的调用
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			} catch (Exception ex) {
				dispatchException = ex;
				// 记录下来异常，在 9 中统一处理
			} catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}
			// 9. 处理最后的结果
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		} catch (Exception ex) {
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		} catch (Throwable err) {
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		} finally {
			//判断是否是异步请求
			if (asyncManager.isConcurrentHandlingStarted()) {
				// Instead of postHandle and afterCompletion
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			} else {
				// Clean up any resources used by a multipart request.
				if (multipartRequestParsed) {
					//删除上传资源
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

	/**
	 * Do we need view name translation?
	 */
	private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
		if (mv != null && !mv.hasView()) {
			String defaultViewName = getDefaultViewName(request);
			if (defaultViewName != null) {
				mv.setViewName(defaultViewName);
			}
		}
	}

	/**
	 * Handle the result of handler selection and handler invocation, which is
	 * either a ModelAndView or an Exception to be resolved to a ModelAndView.
	 */
	private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, @Nullable ModelAndView mv, @Nullable Exception exception)
			throws Exception {

		boolean errorView = false;
		// 判断上面的过程是否出现了异常。如果本次请求出现了异常，并不能因此终止程序。所以需要解析出来异常，返回对应的视图结果，告知用户出现了异常。
		if (exception != null) {
			if (exception instanceof ModelAndViewDefiningException) {
				logger.debug("ModelAndViewDefiningException encountered", exception);
				mv = ((ModelAndViewDefiningException) exception).getModelAndView();
			} else {
				// 若是普通异常，就交给方法processHandlerException()去统一处理
				// 从而得到一个异常视图ModelAndView，并且标注errorView = true（若不为null的话）
				Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
				mv = processHandlerException(request, response, handler, exception);
				errorView = (mv != null);
			}
		}

		// Did the handler return a view to render?
		// 如果在Handler实例的处理过程中返回了 view，则需要做页面处理
		if (mv != null && !mv.wasCleared()) {
			// 渲染此错误视图（若不为null）
			render(mv, request, response);
			if (errorView) {
				WebUtils.clearErrorRequestAttributes(request);
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("Null ModelAndView returned to DispatcherServlet with name '" + getServletName()
						+ "': assuming HandlerAdapter completed request handling");
			}
		}

		if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
			// Concurrent handling started during a forward
			return;
		}

		if (mappedHandler != null) {
			// 触发 拦截器完成事件
			mappedHandler.triggerAfterCompletion(request, response, null);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's primary
	 * locale as current locale.
	 * <p>
	 * The default implementation uses the dispatcher's LocaleResolver to obtain the
	 * current locale, which might change during a request.
	 * 
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext
	 */
	@Override
	protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		} else {
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

	/**
	 * Convert the request into a multipart request, and make multipart resolver
	 * available.
	 * <p>
	 * If no multipart resolver is set, simply use the existing request.
	 * 
	 * @param request current HTTP request
	 * @return the processed request (multipart wrapper if necessary)
	 * @see MultipartResolver#resolveMultipart
	 */
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
		 //默认是空的值，没有数据
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
				logger.debug("Request is already a MultipartHttpServletRequest - if not in a forward, "
						+ "this typically results from an additional MultipartFilter in web.xml");
			} else if (hasMultipartException(request)) {
				logger.debug("Multipart resolution failed for current request before - "
						+ "skipping re-resolution for undisturbed error rendering");
			} else {
				try {
					return this.multipartResolver.resolveMultipart(request);
				} catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
					} else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		return request;
	}

	/**
	 * Check "javax.servlet.error.exception" attribute for a multipart exception.
	 */
	private boolean hasMultipartException(HttpServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
		while (error != null) {
			if (error instanceof MultipartException) {
				return true;
			}
			error = error.getCause();
		}
		return false;
	}

	/**
	 * Clean up any resources used by the given multipart request (if any).
	 * 
	 * @param request current HTTP request
	 * @see MultipartResolver#cleanupMultipart
	 */
	protected void cleanupMultipart(HttpServletRequest request) {
		if (this.multipartResolver != null) {
			MultipartHttpServletRequest multipartRequest = WebUtils.getNativeRequest(request,
					MultipartHttpServletRequest.class);
			if (multipartRequest != null) {
				this.multipartResolver.cleanupMultipart(multipartRequest);
			}
		}
	}

	/**
	 * Return the HandlerExecutionChain for this request.
	 * <p>
	 * Tries all handler mappings in order.
	 * 
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain, or {@code null} if no handler could be
	 *         found
	 */
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		// 这里的 this.handlerMappings 在没有手动调整的情况下是加载的默认配置文件中的数据
		if (this.handlerMappings != null) {
			// 遍历每一个 handleMapping，解析 request，直到碰到一个解析成功的，将解析后的 Handler拦截链路返回。
			for (HandlerMapping hm : this.handlerMappings) {
				if (logger.isTraceEnabled()) {
					logger.trace("Testing handler map [" + hm + "] in DispatcherServlet with name '" + getServletName()
							+ "'");
				}
				HandlerExecutionChain handler = hm.getHandler(request);
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}

	/**
	 * No handler found -> set appropriate HTTP response status. 正常情况下，每一个请求都应该对应一个
	 * Handler，因为每个请求都应该在后台有对应的处理逻辑。而逻辑的实现就是在Handler
	 * 中。正常情况下，如果没有URL匹配的Handler，我们可以通过设置默认的Handler
	 * 来解决这一问题，不过如果没有设置默认的Handler。则只能通过Response 向用户返回错误信息。
	 * 
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @throws Exception if preparing the response failed
	 */
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {
			pageNotFoundLogger.warn("No mapping found for HTTP request with URI [" + getRequestUri(request)
					+ "] in DispatcherServlet with name '" + getServletName() + "'");
		}
		// 判断DispatcherServlet 属性设置，是否需要抛出异常
		if (this.throwExceptionIfNoHandlerFound) {
			throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
					new ServletServerHttpRequest(request).getHeaders());
		} else {
			// 否则直接抛出错误 404
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Return the HandlerAdapter for this handler object.
	 * 
	 * 这一步的目的是根据 Handler 寻找对应的 HandlerAdapter。这里使用了适配器模式，遍历所有的 Adapter。根据
	 * HandlerAdapter#supports 方法来判断是否支持当前Handler
	 * 的解析，如果支持，则返回。我们这里返回的是RequestMappingHandlerAdapter。
	 * 
	 * @param handler the handler object to find an adapter for
	 * @throws ServletException if no HandlerAdapter can be found for the handler.
	 *                          This is a fatal error.
	 */
	protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		if (this.handlerAdapters != null) {
			for (HandlerAdapter ha : this.handlerAdapters) {
				if (logger.isTraceEnabled()) {
					logger.trace("Testing handler adapter [" + ha + "]");
				}
				if (ha.supports(handler)) {
					return ha;
				}
			}
		}
		throw new ServletException("No adapter for handler [" + handler
				+ "]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

	/**
	 * Determine an error ModelAndView via the registered HandlerExceptionResolvers.
	 * 
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @param handler  the executed handler, or {@code null} if none chosen at the
	 *                 time of the exception (for example, if multipart resolution
	 *                 failed)
	 * @param ex       the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to
	 * @throws Exception if no error ModelAndView found
	 */
	@Nullable
	protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
			@Nullable Object handler, Exception ex) throws Exception {

		// Check registered HandlerExceptionResolvers...
		ModelAndView exMv = null;
		// 核心处理办法就在此处，exMv 只有有一个视图返回了，就立马停止（短路效果）
		if (this.handlerExceptionResolvers != null) {
			for (HandlerExceptionResolver handlerExceptionResolver : this.handlerExceptionResolvers) {
				exMv = handlerExceptionResolver.resolveException(request, response, handler, ex);
				if (exMv != null) {
					break;
				}
			}
		}
		if (exMv != null) {
			if (exMv.isEmpty()) {
				request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
				return null;
			}
			// We might still need view name translation for a plain error model...
			if (!exMv.hasView()) {
				String defaultViewName = getDefaultViewName(request);
				if (defaultViewName != null) {
					exMv.setViewName(defaultViewName);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Handler execution resulted in exception - forwarding to resolved error view: " + exMv,
						ex);
			}
			WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
			return exMv;
		}

		throw ex;
	}

	/**
	 * Render the given ModelAndView.
	 * <p>
	 * This is the last stage in handling a request. It may involve resolving the
	 * view by name.
	 * 
	 * @param mv       the ModelAndView to render
	 * @param request  current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @throws ServletException if view is missing or cannot be resolved
	 * @throws Exception        if there's a problem rendering the view
	 */
	protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Determine locale for request and apply it to the response.
		Locale locale = (this.localeResolver != null ? this.localeResolver.resolveLocale(request)
				: request.getLocale());
		response.setLocale(locale);

		View view;
		String viewName = mv.getViewName();
		// 如果viewname不为null，则需要通过viewName 解析出来对应的 View
		if (viewName != null) {
			// We need to resolve the view name.
			view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
			if (view == null) {
				throw new ServletException("Could not resolve view with name '" + mv.getViewName()
						+ "' in servlet with name '" + getServletName() + "'");
			}
		} else {
			// No need to lookup: the ModelAndView object contains the actual View object.
			view = mv.getView();
			if (view == null) {
				throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a "
						+ "View object in servlet with name '" + getServletName() + "'");
			}
		}

		// Delegate to the View object for rendering.
		if (logger.isDebugEnabled()) {
			logger.debug("Rendering view [" + view + "] in DispatcherServlet with name '" + getServletName() + "'");
		}
		try {
			if (mv.getStatus() != null) {
				response.setStatus(mv.getStatus().value());
			}
			view.render(mv.getModelInternal(), request, response);
		} catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug(
						"Error rendering view [" + view + "] in DispatcherServlet with name '" + getServletName() + "'",
						ex);
			}
			throw ex;
		}
	}

	/**
	 * Translate the supplied request into a default view name.
	 * 
	 * @param request current HTTP servlet request
	 * @return the view name (or {@code null} if no default found)
	 * @throws Exception if view name translation failed
	 */
	@Nullable
	protected String getDefaultViewName(HttpServletRequest request) throws Exception {
		return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
	}

	/**
	 * Resolve the given view name into a View object (to be rendered).
	 * <p>
	 * The default implementations asks all ViewResolvers of this dispatcher. Can be
	 * overridden for custom resolution strategies, potentially based on specific
	 * model attributes or request parameters.
	 * 
	 * @param viewName the name of the view to resolve
	 * @param model    the model to be passed to the view
	 * @param locale   the current locale
	 * @param request  current HTTP servlet request
	 * @return the View object, or {@code null} if none found
	 * @throws Exception if the view cannot be resolved (typically in case of
	 *                   problems creating an actual View object)
	 * @see ViewResolver#resolveViewName
	 */
	@Nullable
	protected View resolveViewName(String viewName, @Nullable Map<String, Object> model, Locale locale,
			HttpServletRequest request) throws Exception {
		// 遍历视图解析器，直到有解析器能解析出来视图
		if (this.viewResolvers != null) {
			for (ViewResolver viewResolver : this.viewResolvers) {
				View view = viewResolver.resolveViewName(viewName, locale);
				if (view != null) {
					return view;
				}
			}
		}
		return null;
	}

	private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, ex);
		}
		throw ex;
	}

	/**
	 * Restore the request attributes after an include.
	 * 
	 * @param request            current HTTP request
	 * @param attributesSnapshot the snapshot of the request attributes before the
	 *                           include
	 */
	@SuppressWarnings("unchecked")
	private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?, ?> attributesSnapshot) {
		// Need to copy into separate Collection here, to avoid side effects
		// on the Enumeration when removing attributes.
		Set<String> attrsToCheck = new HashSet<>();
		Enumeration<?> attrNames = request.getAttributeNames();
		while (attrNames.hasMoreElements()) {
			String attrName = (String) attrNames.nextElement();
			if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
				attrsToCheck.add(attrName);
			}
		}

		// Add attributes that may have been removed
		attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

		// Iterate over the attributes to check, restoring the original value
		// or removing the attribute, respectively, if appropriate.
		for (String attrName : attrsToCheck) {
			Object attrValue = attributesSnapshot.get(attrName);
			if (attrValue == null) {
				request.removeAttribute(attrName);
			} else if (attrValue != request.getAttribute(attrName)) {
				request.setAttribute(attrName, attrValue);
			}
		}
	}

	private static String getRequestUri(HttpServletRequest request) {
		String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
		if (uri == null) {
			uri = request.getRequestURI();
		}
		return uri;
	}

}
