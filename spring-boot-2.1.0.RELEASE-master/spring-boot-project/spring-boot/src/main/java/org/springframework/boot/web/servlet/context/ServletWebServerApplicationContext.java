/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.web.servlet.context;

import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.context.support.ServletContextAwareProcessor;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * https://blog.csdn.net/andy_zhang2007/article/details/78819331
 * 描述了内嵌的tomcat和外部tomcat的 数据处理 A {@link WebApplicationContext} that can be used
 * to bootstrap itself from a contained {@link ServletWebServerFactory} bean.
 * <p>
 * This context will create, initialize and run an {@link WebServer} by
 * searching for a single {@link ServletWebServerFactory} bean within the
 * {@link ApplicationContext} itself. The {@link ServletWebServerFactory} is
 * free to use standard Spring concepts (such as dependency injection, lifecycle
 * callbacks and property placeholder variables).
 * <p>
 * In addition, any {@link Servlet} or {@link Filter} beans defined in the
 * context will be automatically registered with the web server. In the case of
 * a single Servlet bean, the '/' mapping will be used. If multiple Servlet
 * beans are found then the lowercase bean name will be used as a mapping
 * prefix. Any Servlet named 'dispatcherServlet' will always be mapped to '/'.
 * Filter beans will be mapped to all URLs ('/*').
 * <p>
 * For more advanced configuration, the context can instead define beans that
 * implement the {@link ServletContextInitializer} interface (most often
 * {@link ServletRegistrationBean}s and/or {@link FilterRegistrationBean}s). To
 * prevent double registration, the use of {@link ServletContextInitializer}
 * beans will disable automatic Servlet and Filter bean registration.
 * <p>
 * Although this context can be used directly, most developers should consider
 * using the {@link AnnotationConfigServletWebServerApplicationContext} or
 * {@link XmlServletWebServerApplicationContext} variants.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @see AnnotationConfigServletWebServerApplicationContext
 * @see XmlServletWebServerApplicationContext
 * @see ServletWebServerFactory
 */
public class ServletWebServerApplicationContext extends GenericWebApplicationContext
		implements ConfigurableWebServerApplicationContext {

	private static final Log logger = LogFactory.getLog(ServletWebServerApplicationContext.class);

	/**
	 * Constant value for the DispatcherServlet bean name. A Servlet bean with this
	 * name is deemed to be the "main" servlet and is automatically given a mapping
	 * of "/" by default. To change the default behavior you can use a
	 * {@link ServletRegistrationBean} or a different bean name.
	 */
	public static final String DISPATCHER_SERVLET_NAME = "dispatcherServlet";

	private volatile WebServer webServer;

	private ServletConfig servletConfig;

	private String serverNamespace;

	/**
	 * Create a new {@link ServletWebServerApplicationContext}.
	 */
	public ServletWebServerApplicationContext() {
	}

	/**
	 * Create a new {@link ServletWebServerApplicationContext} with the given
	 * {@code DefaultListableBeanFactory}.
	 * 
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this
	 *                    context
	 */
	public ServletWebServerApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
	}

	/**
	 * Register ServletContextAwareProcessor.
	 * 
	 * @see ServletContextAwareProcessor
	 */
	@Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		beanFactory.addBeanPostProcessor(new WebApplicationContextServletContextAwareProcessor(this));
		beanFactory.ignoreDependencyInterface(ServletContextAware.class);
	}

	@Override
	public final void refresh() throws BeansException, IllegalStateException {
		try {
			super.refresh();
		} catch (RuntimeException ex) {
			stopAndReleaseWebServer();
			throw ex;
		}
	}

	@Override
	protected void onRefresh() {
		super.onRefresh();
		try {
			createWebServer(); // 这里创建服务器
		} catch (Throwable ex) {
			throw new ApplicationContextException("Unable to start web server", ex);
		}
	}

	@Override
	protected void finishRefresh() {
		super.finishRefresh();
		WebServer webServer = startWebServer();
		if (webServer != null) {
			// 发布EmbeddedServletContainerInitializedEvent事件
//			v对EmbeddedServletContainerInitializedEvent 感兴趣的Listener 有如下2个:
//
//				org.springframework.boot.context.config.DelegatingApplicationListener,
//				org.springframework.boot.context.embedded.ServerPortInfoApplicationContextInitializer

			publishEvent(new ServletWebServerInitializedEvent(webServer, this));
		}
	}

	@Override
	protected void onClose() {
		super.onClose();
		stopAndReleaseWebServer();
	}

	// TODO
	// 为何springboot外置tomcat时，仅仅在pom.xml中排除了tomcat-starter的配置时，此时就不会创建内嵌的tomcat容器了？
	private void createWebServer() {
		WebServer webServer = this.webServer;
		// 1. 获得ServletContext
		ServletContext servletContext = getServletContext();
	   // 2 内置Servlet容器和ServletContext都还没初始化的时候执行
		if (webServer == null && servletContext == null) {
			// 还记得EmbeddedTomcat 配置类中注册了一个TomcatServletWebServerFactory类型的bean吗？
			// getWebServerFactory方法就是从容器中获取到TomcatServletWebServerFactory类型的bean
			ServletWebServerFactory factory = getWebServerFactory();
			// 调用org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
			// 的getWebServer方法得到Tomcat服务器
			this.webServer = factory.getWebServer(getSelfInitializer());
		}
		// 3. 内置Servlet容器已经初始化但是ServletContext还没初始化,则进行初始化.一般不会到这里
		else if (servletContext != null) {
			try {
				getSelfInitializer().onStartup(servletContext);
			} catch (ServletException ex) {
				throw new ApplicationContextException("Cannot initialize servlet context", ex);
			}
		}
		// 4. 初始化PropertySources
		// TODO:这里的初始化属性源跟ConfigFileApplicationListener监听加载属性配置有何关系？？？//
		// 替换Servlet相关的属性源???
		initPropertySources();
	}

	/**
	 * 
	 * 
	 * 最终调用了AbstractBeanFactory#getBean,该bean触发了bean的实例化,在实例化的过程中, 会触发一系列的扩展点的调用.其中,
	 * 在AbstractAutowireCapableBeanFactory#applyBeanPostProcessorsBeforeInitialization中,
	 * 会调用一系列的BeanPostProcessor.在当前场景有12个,如下:
	 * 
	 * org.springframework.context.support.ApplicationContextAwareProcessor,
	 * org.springframework.boot.context.embedded.WebApplicationContextServletContextAwareProcessor,
	 * org.springframework.context.annotation.ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor,
	 * org.springframework.context.support.PostProcessorRegistrationDelegate$BeanPostProcessorChecker,
	 * org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor,
	 * org.springframework.boot.context.embedded.WebServerFactoryCustomizerBeanPostProcessor,
	 * org.springframework.boot.web.servlet.ErrorPageRegistrarBeanPostProcessor,
	 * org.springframework.context.annotation.CommonAnnotationBeanPostProcessor,
	 * org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor,
	 * org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor,
	 * org.springframework.context.support.ApplicationListenerDetector
	 * 
	 * 其中真正发挥作用的有WebServerFactoryCustomizerBeanPostProcessor,ErrorPageRegistrarBeanPostProcessor.其实现分别如下:
	 * 
	 * ———————————————— 版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0
	 * BY-SA版权协议，转载请附上原文出处链接及本声明。
	 * 原文链接：https://blog.csdn.net/qq_26000415/article/details/78917684
	 * 
	 * Returns the {@link ServletWebServerFactory} that should be used to create the
	 * embedded {@link WebServer}. By default this method searches for a suitable
	 * bean in the context itself.
	 * 
	 * @return a {@link ServletWebServerFactory} (never {@code null})
	 */
	protected ServletWebServerFactory getWebServerFactory() {
		// Use bean names so that we don't consider the hierarchy
		String[] beanNames = getBeanFactory().getBeanNamesForType(ServletWebServerFactory.class);
		if (beanNames.length == 0) {
			throw new ApplicationContextException("Unable to start ServletWebServerApplicationContext due to missing "
					+ "ServletWebServerFactory bean.");
		}
		if (beanNames.length > 1) {
			throw new ApplicationContextException("Unable to start ServletWebServerApplicationContext due to multiple "
					+ "ServletWebServerFactory beans : " + StringUtils.arrayToCommaDelimitedString(beanNames));
		}
		return getBeanFactory().getBean(beanNames[0], ServletWebServerFactory.class);
	}

	/**
	 * Returns the {@link ServletContextInitializer} that will be used to complete
	 * the setup of this {@link WebApplicationContext}.
	 * 
	 * @return the self initializer
	 * @see #prepareWebApplicationContext(ServletContext)
	 */
	private org.springframework.boot.web.servlet.ServletContextInitializer getSelfInitializer() {
		
		return this::selfInitialize;
		// 这里使用了jdk8的新特性等于下面的表达式
		/**
		 * return new ServletContextInitializer() {
		 *
		 * @Override public void onStartup(ServletContext servletContext) throws
		 *           ServletException { selfInitialize(servletContext); }
		 *
		 *           };
		 */
	}
 

	// EmbeddedWebApplicationContext的方法selfInitialize
	// 根据上面的分析，该方法会在内置Tomcat Servlet容器的ServletContext就绪后的相应时机被回调。
	private void selfInitialize(ServletContext servletContext) throws ServletException {
		// 主要是将当前Spring EmbeddedWebApplicationContext实例和ServletContext互相关联:
		// 1.将当前EmbeddedWebApplicationContext实例登记为ServletContext对象的属性
		// ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE;
		// 2.将ServletContext对象设置到当前EmbeddedWebApplicationContext实例的成员变量
		// servletContext。
		prepareWebApplicationContext(servletContext);
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		ExistingWebApplicationScopes existingScopes = new ExistingWebApplicationScopes(beanFactory);
		WebApplicationContextUtils.registerWebApplicationScopes(beanFactory, getServletContext());
		existingScopes.restore();
		WebApplicationContextUtils.registerEnvironmentBeans(beanFactory, getServletContext());
		// 从Bean容器中获取所有是SCI的bean,并调用其onStartup()方法
		// 而通过上面的分析可知，用于注册DispatcherServlet bean的ServletRegistrationBean,
		// 正是这样一个SCI，所以这里会尝试从容器中获取这个bean并执行其onStartup()方法,此过程
		// 也首先触发了DispatchServlet bean的创建。
		// [org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean@3ed0918d,
		// org.springframework.boot.web.servlet.FilterRegistrationBean@f202d6d,
		// org.springframework.boot.web.servlet.FilterRegistrationBean@60e21209,
		// org.springframework.boot.web.servlet.FilterRegistrationBean@630d1b2f,
		// org.springframework.boot.web.servlet.FilterRegistrationBean@746b18fd]
		// DispatcherServletRegistrationBean继承了ServletRegistrationBean的类，
		Collection<ServletContextInitializer> contextInitializer = getServletContextInitializerBeans();
		//输出容器的初始数据
		System.out.println(contextInitializer);
		for (ServletContextInitializer beans : contextInitializer) {
			beans.onStartup(servletContext);
		}
	}

	/**
	 * Returns {@link ServletContextInitializer}s that should be used with the
	 * embedded web server. By default this method will first attempt to find
	 * {@link ServletContextInitializer}, {@link Servlet}, {@link Filter} and
	 * certain {@link EventListener} beans.
	 * 
	 * @return the servlet initializer beans
	 */
	protected Collection<ServletContextInitializer> getServletContextInitializerBeans() {
		return new ServletContextInitializerBeans(getBeanFactory());
	}

	/**
	 * Prepare the {@link WebApplicationContext} with the given fully loaded
	 * {@link ServletContext}. This method is usually called from
	 * {@link ServletContextInitializer#onStartup(ServletContext)} and is similar to
	 * the functionality usually provided by a {@link ContextLoaderListener}.
	 * 
	 * @param servletContext the operational servlet context
	 */
	protected void prepareWebApplicationContext(ServletContext servletContext) {
		Object rootContext = servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (rootContext != null) {
			if (rootContext == this) {
				throw new IllegalStateException(
						"Cannot initialize context because there is already a root application context present - "
								+ "check whether you have multiple ServletContextInitializers!");
			}
			return;
		}
		Log logger = LogFactory.getLog(ContextLoader.class);
		servletContext.log("Initializing Spring embedded WebApplicationContext");
		try {
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this);
			if (logger.isDebugEnabled()) {
				logger.debug("Published root WebApplicationContext as ServletContext attribute with name ["
						+ WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE + "]");
			}
			setServletContext(servletContext);
			if (logger.isInfoEnabled()) {
				long elapsedTime = System.currentTimeMillis() - getStartupDate();
				logger.info("Root WebApplicationContext: initialization completed in " + elapsedTime + " ms");
			}
		} catch (RuntimeException | Error ex) {
			logger.error("Context initialization failed", ex);
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ex);
			throw ex;
		}
	}

	private WebServer startWebServer() {
		WebServer webServer = this.webServer;
		if (webServer != null) {
			// 最终执行org.springframework.boot.web.embedded.tomcat.TomcatWebServer#start,代码如下:
			webServer.start();
		}
		return webServer;
	}

	private void stopAndReleaseWebServer() {
		WebServer webServer = this.webServer;
		if (webServer != null) {
			try {
				webServer.stop();
				this.webServer = null;
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

	@Override
	protected Resource getResourceByPath(String path) {
		if (getServletContext() == null) {
			return new ClassPathContextResource(path, getClassLoader());
		}
		return new ServletContextResource(getServletContext(), path);
	}

	@Override
	public String getServerNamespace() {
		return this.serverNamespace;
	}

	@Override
	public void setServerNamespace(String serverNamespace) {
		this.serverNamespace = serverNamespace;
	}

	@Override
	public void setServletConfig(ServletConfig servletConfig) {
		this.servletConfig = servletConfig;
	}

	@Override
	public ServletConfig getServletConfig() {
		return this.servletConfig;
	}

	/**
	 * Returns the {@link WebServer} that was created by the context or {@code null}
	 * if the server has not yet been created.
	 * 
	 * @return the embedded web server
	 */
	@Override
	public WebServer getWebServer() {
		return this.webServer;
	}

	/**
	 * Utility class to store and restore any user defined scopes. This allow scopes
	 * to be registered in an ApplicationContextInitializer in the same way as they
	 * would in a classic non-embedded web application context.
	 */
	public static class ExistingWebApplicationScopes {

		private static final Set<String> SCOPES;

		static {
			Set<String> scopes = new LinkedHashSet<>();
			scopes.add(WebApplicationContext.SCOPE_REQUEST);
			scopes.add(WebApplicationContext.SCOPE_SESSION);
			SCOPES = Collections.unmodifiableSet(scopes);
		}

		private final ConfigurableListableBeanFactory beanFactory;

		private final Map<String, Scope> scopes = new HashMap<>();

		public ExistingWebApplicationScopes(ConfigurableListableBeanFactory beanFactory) {
			this.beanFactory = beanFactory;
			for (String scopeName : SCOPES) {
				Scope scope = beanFactory.getRegisteredScope(scopeName);
				if (scope != null) {
					this.scopes.put(scopeName, scope);
				}
			}
		}

		public void restore() {
			this.scopes.forEach((key, value) -> {
				if (logger.isInfoEnabled()) {
					logger.info("Restoring user defined scope " + key);
				}
				this.beanFactory.registerScope(key, value);
			});
		}

	}

}
