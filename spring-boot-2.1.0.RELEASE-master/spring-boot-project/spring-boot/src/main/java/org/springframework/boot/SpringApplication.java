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

package org.springframework.boot;

import java.lang.reflect.Constructor;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Class that can be used to bootstrap and launch a Spring application from a Java main
 * method. By default class will perform the following steps to bootstrap your
 * application:
 *
 * <ul>
 * <li>Create an appropriate {@link ApplicationContext} instance (depending on your
 * classpath)</li>
 * <li>Register a {@link CommandLinePropertySource} to expose command line arguments as
 * Spring properties</li>
 * <li>Refresh the application context, loading all singleton beans</li>
 * <li>Trigger any {@link CommandLineRunner} beans</li>
 * </ul>
 *
 * In most circumstances the static {@link #run(Class, String[])} method can be called
 * directly from your {@literal main} method to bootstrap your application:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableAutoConfiguration
 * public class MyApplication  {
 *
 *   // ... Bean definitions
 *
 *   public static void main(String[] args) throws Exception {
 *     SpringApplication.run(MyApplication.class, args);
 *   }
 * }
 * </pre>
 *
 * <p>
 * For more advanced configuration a {@link SpringApplication} instance can be created and
 * customized before being run:
 *
 * <pre class="code">
 * public static void main(String[] args) throws Exception {
 *   SpringApplication application = new SpringApplication(MyApplication.class);
 *   // ... customize application settings here
 *   application.run(args)
 * }
 * </pre>
 *
 * {@link SpringApplication}s can read beans from a variety of different sources. It is
 * generally recommended that a single {@code @Configuration} class is used to bootstrap
 * your application, however, you may also set {@link #getSources() sources} from:
 * <ul>
 * <li>The fully qualified class name to be loaded by
 * {@link AnnotatedBeanDefinitionReader}</li>
 * <li>The location of an XML resource to be loaded by {@link XmlBeanDefinitionReader}, or
 * a groovy script to be loaded by {@link GroovyBeanDefinitionReader}</li>
 * <li>The name of a package to be scanned by {@link ClassPathBeanDefinitionScanner}</li>
 * </ul>
 *
 * Configuration properties are also bound to the {@link SpringApplication}. This makes it
 * possible to set {@link SpringApplication} properties dynamically, like additional
 * sources ("spring.main.sources" - a CSV list) the flag to indicate a web environment
 * ("spring.main.web-application-type=none") or the flag to switch off the banner
 * ("spring.main.banner-mode=off").
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Jeremy Rickard
 * @author Craig Burke
 * @author Michael Simons
 * @author Madhura Bhave
 * @author Brian Clozel
 * @author Ethan Rubinson
 * @see #run(Class, String[])
 * @see #run(Class[], String[])
 * @see #SpringApplication(Class...)
 */
public class SpringApplication {

	/**
	 * The class name of application context that will be used by default for non-web
	 * environments.
	 */
	public static final String DEFAULT_CONTEXT_CLASS = "org.springframework.context."
			+ "annotation.AnnotationConfigApplicationContext";

	/**
	 * The class name of application context that will be used by default for web
	 * environments.
	 */
	public static final String DEFAULT_SERVLET_WEB_CONTEXT_CLASS = "org.springframework.boot."
			+ "web.servlet.context.AnnotationConfigServletWebServerApplicationContext";

	/**
	 * The class name of application context that will be used by default for reactive web
	 * environments.
	 */
	public static final String DEFAULT_REACTIVE_WEB_CONTEXT_CLASS = "org.springframework."
			+ "boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext";

	/**
	 * Default banner location.
	 */
	public static final String BANNER_LOCATION_PROPERTY_VALUE = SpringApplicationBannerPrinter.DEFAULT_BANNER_LOCATION;

	/**
	 * Banner location property key.
	 */
	public static final String BANNER_LOCATION_PROPERTY = SpringApplicationBannerPrinter.BANNER_LOCATION_PROPERTY;

	private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";

	private static final Log logger = LogFactory.getLog(SpringApplication.class);
	/**
	 * SpringBoot的启动类即包含main函数的主类,还有可能是springcould的启动类：
	 * BootstrapImportSelectorConfiguration
	 */
	private Set<Class<?>> primarySources;

	private Set<String> sources = new LinkedHashSet<>();
	/**
	 * 包含main函数的主类
	 */
	private Class<?> mainApplicationClass;

	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	private boolean logStartupInfo = true;

	private boolean addCommandLineProperties = true;

	private boolean addConversionService = true;

	private Banner banner;
	/**
	 * 资源加载器
	 */
	private ResourceLoader resourceLoader;

	private BeanNameGenerator beanNameGenerator;

	private ConfigurableEnvironment environment;

	private Class<? extends ConfigurableApplicationContext> applicationContextClass;
	/**
	 * 应用类型
	 */
	private WebApplicationType webApplicationType;

	private boolean headless = true;

	private boolean registerShutdownHook = true;
	/**
	 * 初始化器
	 */
	private List<ApplicationContextInitializer<?>> initializers;
	/**
	 * 监听器
	 */
	private List<ApplicationListener<?>> listeners;

	private Map<String, Object> defaultProperties;

	private Set<String> additionalProfiles = new HashSet<>();

	private boolean allowBeanDefinitionOverriding;

	private boolean isCustomEnvironment = false;

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #SpringApplication(ResourceLoader, Class...)
	 * @see #setSources(Set)
	 */
	public SpringApplication(Class<?>... primarySources) {
		this(null, primarySources);
	}

	/**
	 * Create a new {@link SpringApplication} instance. The application context will load
	 * beans from the specified primary sources (see {@link SpringApplication class-level}
	 * documentation for details. The instance can be customized before calling
	 * {@link #run(String...)}.
	 * @param resourceLoader the resource loader to use
	 * @param primarySources the primary bean sources
	 * @see #run(Class, String[])
	 * @see #setSources(Set)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
		// 【1】给resourceLoader属性赋值，注意传入的resourceLoader参数为null
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "PrimarySources must not be null");
		// 【2】给primarySources属性赋值，传入的primarySources其实就是SpringApplication.run(MainApplication.class, args);中的MainApplication.class
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
		// 【3】给webApplicationType属性赋值，根据classpath中存在哪种类型的类来确定是哪种应用类型
		//如果是改项目，那就是SERVLET
		this.webApplicationType = WebApplicationType.deduceFromClasspath();
		// 【4】给initializers属性赋值，利用SpringBoot自定义的SPI从spring.factories中加载ApplicationContextInitializer接口的实现类并赋值给initializers属性
		//对于当前来说,在spring-boot/META-INF/spring.factories中.org.springframework.context.ApplicationContextInitializer值如下:
		//org.springframework.context.ApplicationContextInitializer=\
		//org.springframework.boot.context.ConfigurationWarningsApplicationContextInitializer,\
		//org.springframework.boot.context.ContextIdApplicationContextInitializer,\
		//org.springframework.boot.context.config.DelegatingApplicationContextInitializer,\
		//org.springframework.boot.context.embedded.ServerPortInfoApplicationContextInitializer
//		逻辑如下:遍历传入的names,也就是之前通过SpringFactoriesLoader加载的类名.通过遍历,依次调用其构造器进行初始化.加入到instances.然后进行返回.
//		对于当前场景来说:
//		ConfigurationWarningsApplicationContextInitializer,DelegatingApplicationContextInitializer,ServerPortInfoApplicationContextInitializer初始化没有做任何事.
//		ContextIdApplicationContextInitializer在初始化时.会获得spring boot的应用名.搜索路径如下:
//		spring.application.name
//		vcap.application.name
//		spring.config.name
//		如果都没有配置的话,返回application.
		setInitializers((Collection) getSpringFactoriesInstances(
				ApplicationContextInitializer.class));
		// 【5】给listeners属性赋值，利用SpringBoot自定义的SPI从spring.factories中加载ApplicationListener接口的实现类并赋值给listeners属性
		//设置SpringApplication#setListeners时,还是同样的套路.
		//调用getSpringFactoriesInstances加载META-INF/spring.factories
		//中配置的org.springframework.context.ApplicationListener.
		//对于当前来说.加载的类如下:
		//org.springframework.context.ApplicationListener=\
		//org.springframework.boot.ClearCachesApplicationListener,\
		//org.springframework.boot.builder.ParentContextCloserApplicationListener,\
		//org.springframework.boot.context.FileEncodingApplicationListener,\
		//org.springframework.boot.context.config.AnsiOutputApplicationListener,\
		//org.springframework.boot.context.config.ConfigFileApplicationListener,\
		//org.springframework.boot.context.config.DelegatingApplicationListener,\
		//org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener,\
		//org.springframework.boot.logging.ClasspathLoggingApplicationListener,\
		//org.springframework.boot.logging.LoggingApplicationListener
		//这些类在构造器中都没有做任何事.		
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		// 【6】给mainApplicationClass属性赋值，即这里要推断哪个类调用了main函数，然后再赋值给mainApplicationClass属性，用于后面启动流程中打印一些日志。
		this.mainApplicationClass = deduceMainApplicationClass();
	}

	private Class<?> deduceMainApplicationClass() {
		try {
			// 获取StackTraceElement对象数组stackTrace，StackTraceElement对象存储了调用栈相关信息（比如类名，方法名等）
			StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
			// 遍历stackTrace数组
			for (StackTraceElement stackTraceElement : stackTrace) {
				// 若stackTraceElement记录的调用方法名等于main
				if ("main".equals(stackTraceElement.getMethodName())) {
					// 那么就返回stackTraceElement记录的类名即包含main函数的类名
					return Class.forName(stackTraceElement.getClassName());
				}
			}
		}
		catch (ClassNotFoundException ex) {
			// Swallow and continue
		}
		return null;
	}

	/**
	 * Run the Spring application, creating and refreshing a new
	 * {@link ApplicationContext}.
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return a running {@link ApplicationContext}
	 */
	public ConfigurableApplicationContext run(String... args) {
		// new 一个StopWatch用于统计run启动过程花了多少时间
		StopWatch stopWatch = new StopWatch();
		// 开始计时
		stopWatch.start();
		ConfigurableApplicationContext context = null;
		// exceptionReporters集合用来存储异常报告器，用来报告SpringBoot启动过程的异常
		Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList<>();
		// 配置headless属性，即“java.awt.headless”属性，默认为ture
		// 其实是想设置该应用程序,即使没有检测到显示器,
		//也允许其启动.对于服务器来说,是不需要显示器的,所以要这样设置.
		configureHeadlessProperty();
		// 【1】从spring.factories配置文件中加载到EventPublishingRunListener对象并赋值给SpringApplicationRunListeners
		// EventPublishingRunListener对象主要用来发射SpringBoot启动过程中内置的一些生命周期事件，标志每个不同启动阶段
		//对应当前场景来说,org.springframework.boot.SpringApplicationRunListener只有一个.如下:
//		org.springframework.boot.SpringApplicationRunListener=\
//				org.springframework.boot.context.event.EventPublishingRunListener
		SpringApplicationRunListeners listeners = getRunListeners(args);
		// 启动SpringApplicationRunListener的监听，表示SpringApplication开始启动。
		// 》》》》》发射【ApplicationStartingEvent】事件
		//由于只有一个,因此会调用EventPublishingRunListener的starting方法
		//详细注释请点击{@link EventPublishingRunListener}
		listeners.starting();
		try {
			// 创建ApplicationArguments对象，封装了args参数,注意解析通过main传入的参数数据
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(
					args);
			// 【2】准备环境变量，包括系统变量，环境变量，命令行参数，默认变量，servlet相关配置变量，随机值，
			// JNDI属性值，以及配置文件（比如application.properties）等，注意这些环境变量是有优先级的
			// 》》》》》发射【ApplicationEnvironmentPreparedEvent】事件
			// TODO 这里准备环境变量是不是加载所有application.properties等环境变量？
			//跟Spring的加载环境变量有啥区别？是不是这里加载了Spring那边就不用再管了？自己在application.properties，
			//外部参数等可以设置环境变量的地方上编辑多个环境变量看这里能否加载进来？？？
			// TODO 自己想想加密的属性是如何扩展的？如何改造？
			//代码做了3件事:
			//获取或者创建ConfigurableEnvironment
			//配置ConfigurableEnvironment
			//通知所有的观察者,发送ApplicationEnvironmentPreparedEvent事件.			
			ConfigurableEnvironment environment = prepareEnvironment(listeners,
					applicationArguments);
			// 配置spring.beaninfo.ignore属性，默认为true，即跳过搜索BeanInfo classes.
			configureIgnoreBeanInfo(environment);
			// 【3】控制台打印SpringBoot的bannner标志
			Banner printedBanner = printBanner(environment);
			// 【4】根据不同类型创建不同类型的spring applicationcontext容器
			// 因为这里是servlet环境，所以创建的是AnnotationConfigServletWebServerApplicationContext容器对象
			//又实例化了propertySources的属性，
			//因此 包含了StandardEnvironment 也就持有了servletConfigInitParams，servletContextInitParams,systemProperties，systemEnvironment 4个PropertySource.
			//会实例化容器和忽略自动配置和增加AnnotationConfigUtils方法的registerAnnotationConfigProcessors
			// 1.获得DefaultListableBeanFactory 
//			由于AnnotationConfigEmbeddedWebApplicationContext本身就是DefaultListableBeanFactory子类,
//			因此这里将AnnotationConfigEmbeddedWebApplicationContext向上转型为DefaultListableBeanFactory后返回.
//			TODO 	this.reader = new AnnotatedBeanDefinitionReader(this);这行代码非常重要的，
			//下面都是在他的类里面实例化的
//			2如果DefaultListableBeanFactory中的DependencyComparator不是AnnotationAwareOrderComparator实例的话,
//			就设置DependencyComparator为AnnotationAwareOrderComparator.这里一般都会进行设置的.
//
//			3.如果DefaultListableBeanFactory中的AutowireCandidateResolver不是ContextAnnotationAutowireCandidateResolver实例的话,
//			就实例化为ContextAnnotationAutowireCandidateResolver.
//			4.如果不包含org.springframework.context.annotation.internalConfigurationAnnotationProcessor 
//			的bean的定义.就注册一个bean class为 ConfigurationClassPostProcessor
//			5.如果不包含org.springframework.context.annotation.internalAutowiredAnnotationProcessor 的 bean，
//			 就注册一个 bean class 为 AutowiredAnnotationBeanPostProcessor
//			6如果不包含org.springframework.context.annotation.internalRequiredAnnotationProcessor的 bean，
//			就注册一个 bean class 为 RequiredAnnotationBeanPostProcessor
//			7.如果当前类路径存在javax.annotation.Resource.
//			并且registry中不包含org.springframework.context.annotation.internalCommonAnnotationProcessor的定义,
//			那么就注册一个 bean class 为 CommonAnnotationBeanPostProcessor 的bean. 一般都会进行注册的.
//			8.如果当前类路径存在javax.persistence.EntityManagerFactory和 
//			org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor 并且registry中不包含org.springframework.context.annotation.internalPersistenceAnnotationProcessor的 定义,
//			那么就注册一个 class 为 org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor 的bean. 一般都会进行注册的.
//			9.如果registry 不包含org.springframework.context.event.internalEventListenerProcessor的 定义.就注册一个bean class 为 EventListenerMethodProcessor 的定义
//			10.如果registry 不包含org.springframework.context.event.internalEventListenerFactory的 定义. 就注册一个 class 为 DefaultEventListenerFactory 的定义
			//实例化了工厂方法，
			context = createApplicationContext();
			// 【5】从spring.factories配置文件中加载异常报告期实例，这里加载的是FailureAnalyzers
			// 注意FailureAnalyzers的构造器要传入ConfigurableApplicationContext，因为要从context中获取beanFactory和environment
			//org.springframework.boot.SpringBootExceptionReporter=\
			//org.springframework.boot.diagnostics.FailureAnalyzers
			//实例化FailureAnalyzers时将之前实例化的AnnotationConfigEmbeddedWebApplicationContext 传递给了构造器
			exceptionReporters = getSpringFactoriesInstances(
					SpringBootExceptionReporter.class,
					new Class[] { ConfigurableApplicationContext.class }, context);  
			// 【6】为刚创建的AnnotationConfigServletWebServerApplicationContext容器对象做一些初始化工作，准备一些容器属性值等
			// 1）为AnnotationConfigServletWebServerApplicationContext的属性AnnotatedBeanDefinitionReader和ClassPathBeanDefinitionScanner设置environgment属性
			// 2）根据情况对ApplicationContext应用一些相关的后置处理，比如设置resourceLoader属性等
			// 3）在容器刷新前调用各个ApplicationContextInitializer的初始化方法，ApplicationContextInitializer是在构建SpringApplication对象时从spring.factories中加载的
			// 4）》》》》》发射【ApplicationContextInitializedEvent】事件，标志context容器被创建且已准备好
			// 5）从context容器中获取beanFactory，并向beanFactory中注册一些单例bean，比如applicationArguments，printedBanner
			// 6）TODO 加载bean到application context，注意这里只是加载了部分bean比如mainApplication这个bean，
			//大部分bean应该是在AbstractApplicationContext.refresh方法中被加载？这里留个疑问先
			// 7）》》》》》发射【ApplicationPreparedEvent】事件，标志Context容器已经准备完成
			prepareContext(context, environment, listeners, applicationArguments,
					printedBanner);

			// 【7】刷新容器，这一步至关重要，以后会在分析Spring源码时详细分析，主要做了以下工作：
			// 1）在context刷新前做一些准备工作，比如初始化一些属性设置，属性合法性校验和保存容器中的一些早期事件等；
			// 2）让子类刷新其内部bean factory,注意SpringBoot和Spring启动的情况执行逻辑不一样
			// 3）对bean factory进行配置，比如配置bean factory的类加载器，后置处理器等
			// 4）完成bean factory的准备工作后，此时执行一些后置处理逻辑，子类通过重写这个方法来在BeanFactory创建并预准备完成以后做进一步的设置
			// 在这一步，所有的bean definitions将会被加载，但此时bean还不会被实例化
			// 5）执行BeanFactoryPostProcessor的方法即调用bean factory的后置处理器：
			// BeanDefinitionRegistryPostProcessor（触发时机：bean定义注册之前）和BeanFactoryPostProcessor（触发时机：bean定义注册之后bean实例化之前）
			// 6）注册bean的后置处理器BeanPostProcessor，注意不同接口类型的BeanPostProcessor；在Bean创建前后的执行时机是不一样的
			// 7）初始化国际化MessageSource相关的组件，比如消息绑定，消息解析等
			// 8）初始化事件广播器，如果bean factory没有包含事件广播器，那么new一个SimpleApplicationEventMulticaster广播器对象并注册到bean factory中
			// 9）AbstractApplicationContext定义了一个模板方法onRefresh，留给子类覆写，比如ServletWebServerApplicationContext覆写了该方法来创建内嵌的tomcat容器
			// 10）注册实现了ApplicationListener接口的监听器，之前已经有了事件广播器，此时就可以派发一些early application events
			// 11）完成容器bean factory的初始化，并初始化所有剩余的单例bean。这一步非常重要，一些bean postprocessor会在这里调用。
			// 12）完成容器的刷新工作，并且调用生命周期处理器的onRefresh()方法，并且发布ContextRefreshedEvent事件
			refreshContext(context);

			// 【8】执行刷新容器后的后置处理逻辑，注意这里为空方法
			afterRefresh(context, applicationArguments);
			// 停止stopWatch计时
			stopWatch.stop();
			// 打印日志
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass)
						.logStarted(getApplicationLog(), stopWatch);
			}

			// 》》》》》发射【ApplicationStartedEvent】事件，标志spring容器已经刷新，此时所有的bean实例都已经加载完毕
			//只有一个org.springframework.boot.autoconfigure.BackgroundPreinitializer，初始化一些数据
			listeners.started(context);
			// 【9】调用ApplicationRunner和CommandLineRunner的run方法，实现spring容器启动后需要做的一些东西比如加载一些业务数据等
			callRunners(context, applicationArguments);

		}
		// 【10】若启动过程中抛出异常，此时用FailureAnalyzers来报告异常
		// 并》》》》》发射【ApplicationFailedEvent】事件，标志SpringBoot启动失败
		catch (Throwable ex) {
			handleRunFailure(context, ex, exceptionReporters, listeners);
			throw new IllegalStateException(ex);
		}

		try {
			// 》》》》》发射【ApplicationReadyEvent】事件，标志SpringApplication已经正在运行即已经成功启动，可以接收服务请求了。
			listeners.running(context);
		}
		// 若出现异常，此时仅仅报告异常，而不会发射任何事件
		catch (Throwable ex) {
			handleRunFailure(context, ex, exceptionReporters, null);
			throw new IllegalStateException(ex);
		}


		// 这个语句是我自己加上去的，spring容器刷新完毕后用来打印容器中加载的bean信息
		String[] definitionNames = context.getBeanDefinitionNames();
		/** TODO
		 * **思考**： SpringBoot的`run`方法会调用`prepareContext`会加载一些`bean`，
		 * 同时，在调用`AbstractApplicationContext`的`refresh`方法时也会加载一些`bean`，这些加载的`bean`有什么不同？
		 */
		// TODO 只有在这一步才会打印剩下的bean，不是在prepareEnvironment加载的bean？？？
		System.out.println("=======================下面开始打印容器中所有的bean name=============================");
		for (String name : definitionNames) {
			System.out.println(name);
		}
		System.out.println("=======================打印容器中所有的bean name结束=============================");


		// 【11】最终返回容器
		return context;
	}



	private ConfigurableEnvironment prepareEnvironment(
			SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments) {
		// Create and configure the environment
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		// 这里为之前创建的environment配置一些命令行参数形式的环境变量
		// 2. 配置环境的信息
		configureEnvironment(environment, applicationArguments.getSourceArgs()); 
		//利用事件监听机制来为environment环境变量配置application.properties中的环境变量或@{}形式的环境变量
		//这里有很多的事件，来处理数据对什么事件感兴趣
		listeners.environmentPrepared(environment); 
		bindToSpringApplication(environment); // TODO 将environment绑定在SpringApplication，后续用来干嘛呢？
		if (!this.isCustomEnvironment) {
			// TODO 这里什么情况下需要进行转换？
			environment = new EnvironmentConverter(getClassLoader())
					.convertEnvironmentIfNecessary(environment, deduceEnvironmentClass());
		}
		ConfigurationPropertySources.attach(environment);
		return environment;
	}

	private Class<? extends StandardEnvironment> deduceEnvironmentClass() {
		switch (this.webApplicationType) {
		case SERVLET:
			return StandardServletEnvironment.class;
		case REACTIVE:
			return StandardReactiveWebEnvironment.class;
		default:
			return StandardEnvironment.class;
		}
	}
	//	做了8件事:
	//	    为上下文设置Environment. 注意 这里传入的是 StandardServletEnvironment
	//	    调用postProcessApplicationContext方法设置上下文的beanNameGenerator和resourceLoader(如果SpringApplication有的话)
	//	    拿到之前实例化SpringApplication对象的时候设置的ApplicationContextInitializer，调用它们的initialize方法，对上下文做初始化
	//	    调用listeners#contextPrepared,该方法是一个空实现
	//	    打印启动日志
	//	    往上下文的beanFactory中注册一个singleton的bean，bean的名字是springApplicationArguments，bean的实例是之前实例化的ApplicationArguments对象,
	//	    如果之前获取的printedBanner不为空，那么往上下文的beanFactory中注册一个singleton的bean，bean的名字是springBootBanner，bean的实例就是这个printedBanner.这里默认是SpringBootBanner.
	//	    调用load方法注册启动类的bean定义，也就是调用SpringApplication.run(Application.class, args);的类，SpringApplication的load方法内会创建BeanDefinitionLoader的对象，并调用它的load()方法
	//	    调用listeners的contextLoaded方法，说明上下文已经加载，该方法先找到所有的ApplicationListener，遍历这些listener，
	//    如果该listener继承了ApplicationContextAware类，那么在这一步会调用它的setApplicationContext方法，设置context
 
	private void prepareContext(ConfigurableApplicationContext context,
			ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments, Banner printedBanner) {
		  // 1. 上下文设置环境 ，这个类的 {@link AnnotationConfigServletWebServerApplicationContext}，#setEnvironment,
		context.setEnvironment(environment);
	    // 2. 调用postProcessApplicationContext方法设置上下文的beanNameGenerator和resourceLoader(如果SpringApplication有的话)
		postProcessApplicationContext(context);
	    // 3. 拿到之前实例化SpringApplication对象的时候设置的ApplicationContextInitializer，调用它们的initialize方法，对上下文做初始化
		applyInitializers(context);
		// 4. contextPrepareds 是一个空实现 
		listeners.contextPrepared(context);
		//5.打印日志文件
		if (this.logStartupInfo) {
			logStartupInfo(context.getParent() == null);
			logStartupProfileInfo(context);
		}
		// Add boot specific singleton beans
	    // 6. 日志往上下文的beanFactory中注册一个singleton的bean，
		//bean的名字是springApplicationArguments，bean的实例是之前实例化的ApplicationArguments对象
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
	    // 如果之前获取的printedBanner不为空，那么往上下文的beanFactory中注册一个singleton的bean，bean的名字是springBootBanner，bean的实例就是这个printedBanner
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		if (beanFactory instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) beanFactory)
					.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		// Load the sources
		Set<Object> sources = getAllSources();
		Assert.notEmpty(sources, "Sources must not be empty");
		// TODO 这里加载的是什么bean?这里加载bean后，spring容器刷新过程中还会继续加载bean吗？
	    // 7. 调用load方法注册启动类的bean定义，也就是调用SpringApplication.run(Application.class, args);的类，SpringApplication的load方法内会创建BeanDefinitionLoader的对象，并调用它的load()方法
		load(context, sources.toArray(new Object[0]));
	    // 8. 调用listeners的contextLoaded方法，说明上下文已经加载，该方法先找到所有的ApplicationListener，遍历这些listener，如果该listener继承了ApplicationContextAware类，那么在这一步会调用它的setApplicationContext方法，设置context
		listeners.contextLoaded(context);
	}

	private void refreshContext(ConfigurableApplicationContext context) {
		refresh(context);
		if (this.registerShutdownHook) {
			try {
				context.registerShutdownHook();
			}
			catch (AccessControlException ex) {
				// Not allowed in some environments.
			}
		}
	}

	private void configureHeadlessProperty() {
		System.setProperty(SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, System.getProperty(
				SYSTEM_PROPERTY_JAVA_AWT_HEADLESS, Boolean.toString(this.headless)));
	}

	private SpringApplicationRunListeners getRunListeners(String[] args) {
		// 构造一个由SpringApplication.class和String[].class组成的types
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
		// 1) 根据SpringApplicationRunListener接口去spring.factories配置文件中加载其SPI扩展实现类
		// 2) 构建一个SpringApplicationRunListeners对象并返回
		return new SpringApplicationRunListeners(logger, getSpringFactoriesInstances(
				SpringApplicationRunListener.class, types, this, args));
	}

	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, new Class<?>[] {});
	}

	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type,
			Class<?>[] parameterTypes, Object... args) {
		// 【1】获得类加载器
		ClassLoader classLoader = getClassLoader();
		// Use names and ensure unique to protect against duplicates
		// 【2】将接口类型和类加载器作为参数传入loadFactoryNames方法，从spring.factories配置文件中进行加载接口实现类
		Set<String> names = new LinkedHashSet<>(
				SpringFactoriesLoader.loadFactoryNames(type, classLoader));
		// 【3】实例化从spring.factories中加载的接口实现类
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes,
				classLoader, args, names);
		// 【4】进行排序
		AnnotationAwareOrderComparator.sort(instances);
		// 【5】返回加载并实例化好的接口实现类
		return instances;
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> createSpringFactoriesInstances(Class<T> type,
			Class<?>[] parameterTypes, ClassLoader classLoader, Object[] args,
			Set<String> names) {
		// 新建instances集合，用于存储稍后实例化后的SPI扩展类对象
		List<T> instances = new ArrayList<>(names.size());
		// 遍历name集合，names集合存储了所有SPI扩展类的全限定名
		for (String name : names) {
			try {
				// 根据全限定名利用反射加载类
				Class<?> instanceClass = ClassUtils.forName(name, classLoader);
				// 断言刚才加载的SPI扩展类是否属于SPI接口类型
				Assert.isAssignable(type, instanceClass);
				// 获得SPI扩展类的构造器
				Constructor<?> constructor = instanceClass
						.getDeclaredConstructor(parameterTypes);
				// 实例化SPI扩展类
				T instance = (T) BeanUtils.instantiateClass(constructor, args);
				// 添加进instances集合
				instances.add(instance);
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException(
						"Cannot instantiate " + type + " : " + name, ex);
			}
		}
		// 返回
		return instances;
	}

	private ConfigurableEnvironment getOrCreateEnvironment() {
		if (this.environment != null) {
			return this.environment;
		}
		switch (this.webApplicationType) {
		  // 2. 如果是web环境则直接实例化StandardServletEnvironment类
		case SERVLET:
			//在StandardServletEnvironment实例化时,会触发AbstractEnvironment实例化.
			//而在AbstractEnvironment的构造器中会调用customizePropertySources方法.
			//customizePropertySources其实就是StandardServletEnvironment的方法，是通过父类调用了自己的抽象方法
			return new StandardServletEnvironment();
		case REACTIVE:
			return new StandardReactiveWebEnvironment();
		default:
			  // 2. 如果是web环境则直接实例化StandardServletEnvironment类
			return new StandardEnvironment();
		}
	}

	/**
	 * 做了2件事:
		配置PropertySources
		配置Profiles
		增加转换的服务
	 * Template method delegating to
	 * {@link #configurePropertySources(ConfigurableEnvironment, String[])} and
	 * {@link #configureProfiles(ConfigurableEnvironment, String[])} in that order.
	 * Override this method for complete control over Environment customization, or one of
	 * the above for fine-grained control over property sources or profiles, respectively.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureProfiles(ConfigurableEnvironment, String[])
	 * @see #configurePropertySources(ConfigurableEnvironment, String[])
	 */
	protected void configureEnvironment(ConfigurableEnvironment environment,
			String[] args) {
		if (this.addConversionService) {
			//增加spring类型转换和springboot的类型转换，非常重要，
			ConversionService conversionService = ApplicationConversionService
					.getSharedInstance();
			environment.setConversionService(
					(ConfigurableConversionService) conversionService);
		}
		configurePropertySources(environment, args);
		configureProfiles(environment, args);
	}

	/**
	 * 做了2件事:
	  *如果defaultProperties不为空，则继续添加defaultProperties
	 * 在当前环境下defaultProperties没有添加进去.
	 * 如果addCommandLineProperties为true并且有命令参数，
	 *分两步骤走：第一步存在commandLineArgs则继续设置属性；第二步commandLineArgs不存在则在头部添加commandLineArgs
	 *那么该方法执行完毕后,MutablePropertySources类中propertySourceList已经存在的属性为:
	 commandLineArgs、servletConfigInitParams、servletContextInitParams、jndiProperties（如果存在）、
	 systemProperties、systemEnvironment、defaultProperties（如果存在）
	 * Add, remove or re-order any {@link PropertySource}s in this application's
	 * environment.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 */
	protected void configurePropertySources(ConfigurableEnvironment environment,
			String[] args) {
		//这个是在上面增加了org.springframework.core.env.MutablePropertySources
		//应该总共有5个值的数据，defaultProperties的值好像是空的，没有数据。
		//[StubPropertySource {name='servletConfigInitParams'}, 
		//StubPropertySource {name='servletContextInitParams'},
		//MapPropertySource {name='systemProperties'}, 
		//SystemEnvironmentPropertySource {name='systemEnvironment'}]
		MutablePropertySources sources = environment.getPropertySources();
		if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
			sources.addLast(new MapPropertySource("defaultProperties", this.defaultProperties));
		}
		//addCommandLineProperties解析命令行数据，
		if (this.addCommandLineProperties && args.length > 0) {
//			如果addCommandLineProperties为true并且有命令参数，
//			分两步骤走：第一步存在commandLineArgs则继续设置属性；第二步commandLineArgs不存在则在头部添加commandLineArgs
//			那么该方法执行完毕后,MutablePropertySources类中propertySourceList已经存在的属性为:
//			————————————————
//			版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//			原文链接：https://blog.csdn.net/qq_26000415/article/details/78914944
			String name = CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME;
			if (sources.contains(name)) {
				PropertySource<?> source = sources.get(name);
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(new SimpleCommandLinePropertySource(
						"springApplicationCommandLineArgs", args));
				composite.addPropertySource(source);
				sources.replace(name, composite);
			}
			else {
				sources.addFirst(new SimpleCommandLinePropertySource(args));
			}
		}
	}

	/**
	 * Configure which profiles are active (or active by default) for this application
	 * environment. Additional profiles may be activated during configuration file
	 * processing via the {@code spring.profiles.active} property.
	 * @param environment this application's environment
	 * @param args arguments passed to the {@code run} method
	 * @see #configureEnvironment(ConfigurableEnvironment, String[])
	 * @see org.springframework.boot.context.config.ConfigFileApplicationListener
	 */
	protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
		environment.getActiveProfiles(); // ensure they are initialized
		// But these ones should go first (last wins in a property key clash)
		Set<String> profiles = new LinkedHashSet<>(this.additionalProfiles);
		profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
		environment.setActiveProfiles(StringUtils.toStringArray(profiles));
	}

	private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
		if (System.getProperty(
				CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME) == null) {
			Boolean ignore = environment.getProperty("spring.beaninfo.ignore",
					Boolean.class, Boolean.TRUE);
			System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME,
					ignore.toString());
		}
	}

	/**
	 * Bind the environment to the {@link SpringApplication}.
	 * @param environment the environment to bind
	 */
	protected void bindToSpringApplication(ConfigurableEnvironment environment) {
		try {
			Binder.get(environment).bind("spring.main", Bindable.ofInstance(this));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot bind to SpringApplication", ex);
		}
	}

	private Banner printBanner(ConfigurableEnvironment environment) {
		 // 1. 首先判断banner的输出级别。如果禁用了，则直接返回空。
		if (this.bannerMode == Banner.Mode.OFF) {
			return null;
		}
		 // 2. 获取资源加载器ResourceLoader
		ResourceLoader resourceLoader = (this.resourceLoader != null)
				? this.resourceLoader : new DefaultResourceLoader(getClassLoader());
		  // 3. 实例化SpringApplicationBannerPrinter类
		SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
        // 如果banner的输出模式是Mode.LOG，则直接将其信息输出到logger日志中，否则将其输出到控制台，也就是System.out
		if (this.bannerMode == Mode.LOG) {
			return bannerPrinter.print(environment, this.mainApplicationClass, logger);
		}
		return bannerPrinter.print(environment, this.mainApplicationClass, System.out);
	}

	/**
	 * Strategy method used to create the {@link ApplicationContext}. By default this
	 * method will respect any explicitly set application context or application context
	 * class before falling back to a suitable default.
	 * @return the application context (not yet refreshed)
	 * @see #setApplicationContextClass(Class)
	 */
	protected ConfigurableApplicationContext createApplicationContext() {
		Class<?> contextClass = this.applicationContextClass;
		if (contextClass == null) {
			try {
				switch (this.webApplicationType) {
				case SERVLET:
					contextClass = Class.forName(DEFAULT_SERVLET_WEB_CONTEXT_CLASS);
					break;
				case REACTIVE:
					contextClass = Class.forName(DEFAULT_REACTIVE_WEB_CONTEXT_CLASS);
					break;
				default:
					contextClass = Class.forName(DEFAULT_CONTEXT_CLASS);
				}
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(
						"Unable create a default ApplicationContext, "
								+ "please specify an ApplicationContextClass",
						ex);
			}
		}
		return (ConfigurableApplicationContext) BeanUtils.instantiateClass(contextClass);
	}

	/**
	 * Apply any relevant post processing the {@link ApplicationContext}. Subclasses can
	 * apply additional processing as required.
	 * @param context the application context
	 */
	protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
		if (this.beanNameGenerator != null) {
			context.getBeanFactory().registerSingleton(
					AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR,
					this.beanNameGenerator);
		}
		//这个是空值
		if (this.resourceLoader != null) {
			if (context instanceof GenericApplicationContext) {
				((GenericApplicationContext) context)
						.setResourceLoader(this.resourceLoader);
			}
			if (context instanceof DefaultResourceLoader) {
				((DefaultResourceLoader) context)
						.setClassLoader(this.resourceLoader.getClassLoader());
			}
		}
		//把类型转成增加到org.springframework.beans.factory.support.DefaultListableBeanFactory这个容器中
		if (this.addConversionService) {
			context.getBeanFactory().setConversionService(
					ApplicationConversionService.getSharedInstance());
		}
	}

	/**
	 * 这里是在spring.factories,这里进行实例化和调用初始化initialize方法
	 * 遍历之,调用其initialize 进行初始化.当前的initialize有如下:
			org.springframework.boot.context.config.DelegatingApplicationContextInitializer, 
			org.springframework.boot.context.ContextIdApplicationContextInitializer, 
			org.springframework.boot.context.ConfigurationWarningsApplicationContextInitializer, 
			org.springframework.boot.context.embedded.ServerPortInfoApplicationContextInitializer, 
			org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer, 
			org.springframework.boot.autoconfigure.logging.AutoConfigurationReportLoggingInitializer
 
	 * 
	 * Apply any {@link ApplicationContextInitializer}s to the context before it is
	 * refreshed.
	 * @param context the configured ApplicationContext (not refreshed yet)
	 * @see ConfigurableApplicationContext#refresh()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void applyInitializers(ConfigurableApplicationContext context) {
	    // 1. 从SpringApplication类中的initializers集合获取所有的ApplicationContextInitializer
		for (ApplicationContextInitializer initializer : getInitializers()) {
			Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(
					initializer.getClass(), ApplicationContextInitializer.class);
			Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
			   // 2. 循环调用ApplicationContextInitializer中的initialize方法
			initializer.initialize(context);
		}
	}

	/**
	 * Called to log startup information, subclasses may override to add additional
	 * logging.
	 * @param isRoot true if this application is the root of a context hierarchy
	 */
	protected void logStartupInfo(boolean isRoot) {
		if (isRoot) {
			new StartupInfoLogger(this.mainApplicationClass)
					.logStarting(getApplicationLog());
		}
	}

	/**
	 * Called to log active profile information.
	 * @param context the application context
	 */
	protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
		Log log = getApplicationLog();
		if (log.isInfoEnabled()) {
			String[] activeProfiles = context.getEnvironment().getActiveProfiles();
			if (ObjectUtils.isEmpty(activeProfiles)) {
				String[] defaultProfiles = context.getEnvironment().getDefaultProfiles();
				log.info("No active profile set, falling back to default profiles: "
						+ StringUtils.arrayToCommaDelimitedString(defaultProfiles));
			}
			else {
				log.info("The following profiles are active: "
						+ StringUtils.arrayToCommaDelimitedString(activeProfiles));
			}
		}
	}

	/**
	 * Returns the {@link Log} for the application. By default will be deduced.
	 * @return the application log
	 */
	protected Log getApplicationLog() {
		if (this.mainApplicationClass == null) {
			return logger;
		}
		return LogFactory.getLog(this.mainApplicationClass);
	}

	/**
	 * Load beans into the application context.
	 * @param context the context to load beans into
	 * @param sources the sources to load
	 */
	protected void load(ApplicationContext context, Object[] sources) {
		if (logger.isDebugEnabled()) {
			logger.debug(
					"Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
		}
	    // 1. 实例化BeanDefinitionLoader
		BeanDefinitionLoader loader = createBeanDefinitionLoader(
				getBeanDefinitionRegistry(context), sources);
	    // 2. 如果当前的beanNameGenerator 不会null的话,就将SpringApplication中的beanNameGenerator赋值给BeanDefinitionLoader
		if (this.beanNameGenerator != null) {
			loader.setBeanNameGenerator(this.beanNameGenerator);
		}
	    // 3. 如果当前的resourceLoader 不会null的话,就将SpringApplication中的resourceLoader赋值给BeanDefinitionLoader
		if (this.resourceLoader != null) {
			loader.setResourceLoader(this.resourceLoader);
		}
	    // 4. 如果当前的environment 不会null的话,就将SpringApplication中的environment赋值给BeanDefinitionLoader
		if (this.environment != null) {
			loader.setEnvironment(this.environment);
		}
		// 5这里应该只是把标有@SpringBootApplication注解的启动类注册到容器中
		loader.load();
	}

	/**
	 * The ResourceLoader that will be used in the ApplicationContext.
	 * @return the resourceLoader the resource loader that will be used in the
	 * ApplicationContext (or null if the default)
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * Either the ClassLoader that will be used in the ApplicationContext (if
	 * {@link #setResourceLoader(ResourceLoader) resourceLoader} is set, or the context
	 * class loader (if not null), or the loader of the Spring {@link ClassUtils} class.
	 * @return a ClassLoader (never null)
	 */
	public ClassLoader getClassLoader() {
		// 前面在构造SpringApplicaiton对象时，传入的resourceLoader参数是null，因此不会执行if语句里面的逻辑
		if (this.resourceLoader != null) {
			return this.resourceLoader.getClassLoader();
		}
		// 获取默认的类加载器
		return ClassUtils.getDefaultClassLoader();
	}

	/**
	 * Get the bean definition registry.
	 * @param context the application context
	 * @return the BeanDefinitionRegistry if it can be determined
	 */
	private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
		if (context instanceof BeanDefinitionRegistry) {
			return (BeanDefinitionRegistry) context;
		}
		if (context instanceof AbstractApplicationContext) {
			return (BeanDefinitionRegistry) ((AbstractApplicationContext) context)
					.getBeanFactory();
		}
		throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
	}

	/**
	 * Factory method used to create the {@link BeanDefinitionLoader}.
	 * @param registry the bean definition registry
	 * @param sources the sources to load
	 * @return the {@link BeanDefinitionLoader} that will be used to load beans
	 */
	protected BeanDefinitionLoader createBeanDefinitionLoader(
			BeanDefinitionRegistry registry, Object[] sources) {
		return new BeanDefinitionLoader(registry, sources);
	}

	/**
	 * Refresh the underlying {@link ApplicationContext}.
	 * @param applicationContext the application context to refresh
	 */
	protected void refresh(ApplicationContext applicationContext) {
		Assert.isInstanceOf(AbstractApplicationContext.class, applicationContext);
		((AbstractApplicationContext) applicationContext).refresh();
	}

	/**
	 * Called after the context has been refreshed.
	 * @param context the application context
	 * @param args the application arguments
	 */
	protected void afterRefresh(ConfigurableApplicationContext context,
			ApplicationArguments args) {
	}

	private void callRunners(ApplicationContext context, ApplicationArguments args) {
		List<Object> runners = new ArrayList<>();
		runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
		runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
		AnnotationAwareOrderComparator.sort(runners);
		for (Object runner : new LinkedHashSet<>(runners)) {
			if (runner instanceof ApplicationRunner) {
				callRunner((ApplicationRunner) runner, args);
			}
			if (runner instanceof CommandLineRunner) {
				callRunner((CommandLineRunner) runner, args);
			}
		}
	}

	private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute ApplicationRunner", ex);
		}
	}

	private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
		try {
			(runner).run(args.getSourceArgs());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to execute CommandLineRunner", ex);
		}
	}

	private void handleRunFailure(ConfigurableApplicationContext context,
			Throwable exception,
			Collection<SpringBootExceptionReporter> exceptionReporters,
			SpringApplicationRunListeners listeners) {
		
		try {
			try {
				handleExitCode(context, exception);
				if (listeners != null) {
					listeners.failed(context, exception);
				}
			}
			finally {
				reportFailure(exceptionReporters, exception);
				if (context != null) {
					context.close();
				}
			}
		}
		catch (Exception ex) {
			logger.warn("Unable to close ApplicationContext", ex);
		}
		ReflectionUtils.rethrowRuntimeException(exception);
	}

	private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters,
			Throwable failure) {
		try {
			for (SpringBootExceptionReporter reporter : exceptionReporters) {
				if (reporter.reportException(failure)) {
					registerLoggedException(failure);
					return;
				}
			}
		}
		catch (Throwable ex) {
			// Continue with normal handling of the original failure
		}
		if (logger.isErrorEnabled()) {
			logger.error("Application run failed", failure);
			registerLoggedException(failure);
		}
	}

	/**
	 * Register that the given exception has been logged. By default, if the running in
	 * the main thread, this method will suppress additional printing of the stacktrace.
	 * @param exception the exception that was logged
	 */
	protected void registerLoggedException(Throwable exception) {
		SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
		if (handler != null) {
			handler.registerLoggedException(exception);
		}
	}

	private void handleExitCode(ConfigurableApplicationContext context,
			Throwable exception) {
		int exitCode = getExitCodeFromException(context, exception);
		if (exitCode != 0) {
			if (context != null) {
				context.publishEvent(new ExitCodeEvent(context, exitCode));
			}
			SpringBootExceptionHandler handler = getSpringBootExceptionHandler();
			if (handler != null) {
				handler.registerExitCode(exitCode);
			}
		}
	}

	private int getExitCodeFromException(ConfigurableApplicationContext context,
			Throwable exception) {
		int exitCode = getExitCodeFromMappedException(context, exception);
		if (exitCode == 0) {
			exitCode = getExitCodeFromExitCodeGeneratorException(exception);
		}
		return exitCode;
	}

	private int getExitCodeFromMappedException(ConfigurableApplicationContext context,
			Throwable exception) {
		if (context == null || !context.isActive()) {
			return 0;
		}
		ExitCodeGenerators generators = new ExitCodeGenerators();
		Collection<ExitCodeExceptionMapper> beans = context
				.getBeansOfType(ExitCodeExceptionMapper.class).values();
		generators.addAll(exception, beans);
		return generators.getExitCode();
	}

	private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
		if (exception == null) {
			return 0;
		}
		if (exception instanceof ExitCodeGenerator) {
			return ((ExitCodeGenerator) exception).getExitCode();
		}
		return getExitCodeFromExitCodeGeneratorException(exception.getCause());
	}

	SpringBootExceptionHandler getSpringBootExceptionHandler() {
		if (isMainThread(Thread.currentThread())) {
			return SpringBootExceptionHandler.forCurrentThread();
		}
		return null;
	}

	private boolean isMainThread(Thread currentThread) {
		return ("main".equals(currentThread.getName())
				|| "restartedMain".equals(currentThread.getName()))
				&& "main".equals(currentThread.getThreadGroup().getName());
	}

	/**
	 * Returns the main application class that has been deduced or explicitly configured.
	 * @return the main application class or {@code null}
	 */
	public Class<?> getMainApplicationClass() {
		return this.mainApplicationClass;
	}

	/**
	 * Set a specific main application class that will be used as a log source and to
	 * obtain version information. By default the main application class will be deduced.
	 * Can be set to {@code null} if there is no explicit application class.
	 * @param mainApplicationClass the mainApplicationClass to set or {@code null}
	 */
	public void setMainApplicationClass(Class<?> mainApplicationClass) {
		this.mainApplicationClass = mainApplicationClass;
	}

	/**
	 * Returns the type of web application that is being run.
	 * @return the type of web application
	 * @since 2.0.0
	 */
	public WebApplicationType getWebApplicationType() {
		return this.webApplicationType;
	}

	/**
	 * Sets the type of web application to be run. If not explicitly set the type of web
	 * application will be deduced based on the classpath.
	 * @param webApplicationType the web application type
	 * @since 2.0.0
	 */
	public void setWebApplicationType(WebApplicationType webApplicationType) {
		Assert.notNull(webApplicationType, "WebApplicationType must not be null");
		this.webApplicationType = webApplicationType;
	}

	/**
	 * Sets if bean definition overriding, by registering a definition with the same name
	 * as an existing definition, should be allowed. Defaults to {@code false}.
	 * @param allowBeanDefinitionOverriding if overriding is allowed
	 * @since 2.1
	 * @see DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Sets if the application is headless and should not instantiate AWT. Defaults to
	 * {@code true} to prevent java icons appearing.
	 * @param headless if the application is headless
	 */
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	/**
	 * Sets if the created {@link ApplicationContext} should have a shutdown hook
	 * registered. Defaults to {@code true} to ensure that JVM shutdowns are handled
	 * gracefully.
	 * @param registerShutdownHook if the shutdown hook should be registered
	 */
	public void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	/**
	 * Sets the {@link Banner} instance which will be used to print the banner when no
	 * static banner file is provided.
	 * @param banner the Banner instance to use
	 */
	public void setBanner(Banner banner) {
		this.banner = banner;
	}

	/**
	 * Sets the mode used to display the banner when the application runs. Defaults to
	 * {@code Banner.Mode.CONSOLE}.
	 * @param bannerMode the mode used to display the banner
	 */
	public void setBannerMode(Banner.Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	/**
	 * Sets if the application information should be logged when the application starts.
	 * Defaults to {@code true}.
	 * @param logStartupInfo if startup info should be logged.
	 */
	public void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	/**
	 * Sets if a {@link CommandLinePropertySource} should be added to the application
	 * context in order to expose arguments. Defaults to {@code true}.
	 * @param addCommandLineProperties if command line arguments should be exposed
	 */
	public void setAddCommandLineProperties(boolean addCommandLineProperties) {
		this.addCommandLineProperties = addCommandLineProperties;
	}

	/**
	 * Sets if the {@link ApplicationConversionService} should be added to the application
	 * context's {@link Environment}.
	 * @param addConversionService if the application conversion service should be added
	 * @since 2.1.0
	 */
	public void setAddConversionService(boolean addConversionService) {
		this.addConversionService = addConversionService;
	}

	/**
	 * Set default environment properties which will be used in addition to those in the
	 * existing {@link Environment}.
	 * @param defaultProperties the additional properties to set
	 */
	public void setDefaultProperties(Map<String, Object> defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	/**
	 * Convenient alternative to {@link #setDefaultProperties(Map)}.
	 * @param defaultProperties some {@link Properties}
	 */
	public void setDefaultProperties(Properties defaultProperties) {
		this.defaultProperties = new HashMap<>();
		for (Object key : Collections.list(defaultProperties.propertyNames())) {
			this.defaultProperties.put((String) key, defaultProperties.get(key));
		}
	}

	/**
	 * Set additional profile values to use (on top of those set in system or command line
	 * properties).
	 * @param profiles the additional profiles to set
	 */
	public void setAdditionalProfiles(String... profiles) {
		this.additionalProfiles = new LinkedHashSet<>(Arrays.asList(profiles));
	}

	/**
	 * Sets the bean name generator that should be used when generating bean names.
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = beanNameGenerator;
	}

	/**
	 * Sets the underlying environment that should be used with the created application
	 * context.
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.isCustomEnvironment = true;
		this.environment = environment;
	}

	/**
	 * Add additional items to the primary sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called.
	 * <p>
	 * The sources here are added to those that were set in the constructor. Most users
	 * should consider using {@link #getSources()}/{@link #setSources(Set)} rather than
	 * calling this method.
	 * @param additionalPrimarySources the additional primary sources to add
	 * @see #SpringApplication(Class...)
	 * @see #getSources()
	 * @see #setSources(Set)
	 * @see #getAllSources()
	 */
	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		this.primarySources.addAll(additionalPrimarySources);
	}

	/**
	 * Returns a mutable set of the sources that will be added to an ApplicationContext
	 * when {@link #run(String...)} is called.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @return the application sources.
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public Set<String> getSources() {
		return this.sources;
	}

	/**
	 * Set additional sources that will be used to create an ApplicationContext. A source
	 * can be: a class name, package name, or an XML resource location.
	 * <p>
	 * Sources set here will be used in addition to any primary sources set in the
	 * constructor.
	 * @param sources the application sources to set
	 * @see #SpringApplication(Class...)
	 * @see #getAllSources()
	 */
	public void setSources(Set<String> sources) {
		Assert.notNull(sources, "Sources must not be null");
		this.sources = new LinkedHashSet<>(sources);
	}

	/**
	 * Return an immutable set of all the sources that will be added to an
	 * ApplicationContext when {@link #run(String...)} is called. This method combines any
	 * primary sources specified in the constructor with any additional ones that have
	 * been {@link #setSources(Set) explicitly set}.
	 * @return an immutable set of all sources
	 */
	public Set<Object> getAllSources() {
		Set<Object> allSources = new LinkedHashSet<>();
		if (!CollectionUtils.isEmpty(this.primarySources)) {
			allSources.addAll(this.primarySources);
		}
		if (!CollectionUtils.isEmpty(this.sources)) {
			allSources.addAll(this.sources);
		}
		return Collections.unmodifiableSet(allSources);
	}

	/**
	 * Sets the {@link ResourceLoader} that should be used when loading resources.
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}

	/**
	 * Sets the type of Spring {@link ApplicationContext} that will be created. If not
	 * specified defaults to {@link #DEFAULT_SERVLET_WEB_CONTEXT_CLASS} for web based
	 * applications or {@link AnnotationConfigApplicationContext} for non web based
	 * applications.
	 * @param applicationContextClass the context class to set
	 */
	public void setApplicationContextClass(
			Class<? extends ConfigurableApplicationContext> applicationContextClass) {
		this.applicationContextClass = applicationContextClass;
		this.webApplicationType = WebApplicationType
				.deduceFromApplicationContext(applicationContextClass);
	}

	/**
	 * Sets the {@link ApplicationContextInitializer} that will be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to set
	 */
	public void setInitializers(
			Collection<? extends ApplicationContextInitializer<?>> initializers) {
		this.initializers = new ArrayList<>();
		this.initializers.addAll(initializers);
	}

	/**
	 * Add {@link ApplicationContextInitializer}s to be applied to the Spring
	 * {@link ApplicationContext}.
	 * @param initializers the initializers to add
	 */
	public void addInitializers(ApplicationContextInitializer<?>... initializers) {
		this.initializers.addAll(Arrays.asList(initializers));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationContextInitializer}s that
	 * will be applied to the Spring {@link ApplicationContext}.
	 * @return the initializers
	 */
	public Set<ApplicationContextInitializer<?>> getInitializers() {
		return asUnmodifiableOrderedSet(this.initializers);
	}

	/**
	 * Sets the {@link ApplicationListener}s that will be applied to the SpringApplication
	 * and registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to set
	 */
	public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
		this.listeners = new ArrayList<>();
		this.listeners.addAll(listeners);
	}

	/**
	 * Add {@link ApplicationListener}s to be applied to the SpringApplication and
	 * registered with the {@link ApplicationContext}.
	 * @param listeners the listeners to add
	 */
	public void addListeners(ApplicationListener<?>... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
	}

	/**
	 * Returns read-only ordered Set of the {@link ApplicationListener}s that will be
	 * applied to the SpringApplication and registered with the {@link ApplicationContext}
	 * .
	 * @return the listeners
	 */
	public Set<ApplicationListener<?>> getListeners() {
		return asUnmodifiableOrderedSet(this.listeners);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified source using default settings.
	 * @param primarySource the primary source to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	// run方法是一个静态方法，用于启动SpringBoot
	public static ConfigurableApplicationContext run(Class<?> primarySource,
			String... args) {
		// 继续调用静态的run方法
		return run(new Class<?>[] { primarySource }, args);
	}

	/**
	 * Static helper that can be used to run a {@link SpringApplication} from the
	 * specified sources using default settings and user supplied arguments.
	 * @param primarySources the primary sources to load
	 * @param args the application arguments (usually passed from a Java main method)
	 * @return the running {@link ApplicationContext}
	 */
	// run方法是一个静态方法，用于启动SpringBoot
	public static ConfigurableApplicationContext run(Class<?>[] primarySources,
			String[] args) {
		// 构建一个SpringApplication对象，并调用其run方法来启动
		return new SpringApplication(primarySources).run(args);
	}

	/**
	 * A basic main that can be used to launch an application. This method is useful when
	 * application sources are defined via a {@literal --spring.main.sources} command line
	 * argument.
	 * <p>
	 * Most developers will want to define their own main method and call the
	 * {@link #run(Class, String...) run} method instead.
	 * @param args command line arguments
	 * @throws Exception if the application cannot be started
	 * @see SpringApplication#run(Class[], String[])
	 * @see SpringApplication#run(Class, String...)
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(new Class<?>[0], args);
	}

	/**
	 * Static helper that can be used to exit a {@link SpringApplication} and obtain a
	 * code indicating success (0) or otherwise. Does not throw exceptions but should
	 * print stack traces of any encountered. Applies the specified
	 * {@link ExitCodeGenerator} in addition to any Spring beans that implement
	 * {@link ExitCodeGenerator}. In the case of multiple exit codes the highest value
	 * will be used (or if all values are negative, the lowest value will be used)
	 * @param context the context to close if possible
	 * @param exitCodeGenerators exist code generators
	 * @return the outcome (0 if successful)
	 */
	public static int exit(ApplicationContext context,
			ExitCodeGenerator... exitCodeGenerators) {
		Assert.notNull(context, "Context must not be null");
		int exitCode = 0;
		try {
			try {
				ExitCodeGenerators generators = new ExitCodeGenerators();
				Collection<ExitCodeGenerator> beans = context
						.getBeansOfType(ExitCodeGenerator.class).values();
				generators.addAll(exitCodeGenerators);
				generators.addAll(beans);
				exitCode = generators.getExitCode();
				if (exitCode != 0) {
					context.publishEvent(new ExitCodeEvent(context, exitCode));
				}
			}
			finally {
				close(context);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			exitCode = (exitCode != 0) ? exitCode : 1;
		}
		return exitCode;
	}

	private static void close(ApplicationContext context) {
		if (context instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext closable = (ConfigurableApplicationContext) context;
			closable.close();
		}
	}

	private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
		List<E> list = new ArrayList<>();
		list.addAll(elements);
		list.sort(AnnotationAwareOrderComparator.INSTANCE);
		return new LinkedHashSet<>(list);
	}

}
