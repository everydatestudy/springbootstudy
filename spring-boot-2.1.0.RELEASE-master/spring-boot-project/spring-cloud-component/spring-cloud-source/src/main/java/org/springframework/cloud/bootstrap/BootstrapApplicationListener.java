/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.bootstrap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.cloud.bootstrap.encrypt.EnvironmentDecryptApplicationInitializer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * 这个是扫描spring-cloud-context-xx.版本下面的spring.factories的文件得到了springcloud事件，
 * 然后调用springApplication.run方法这样会刷新数据
 *  org.springframework.context.ApplicationListener=\
	org.springframework.cloud.bootstrap.BootstrapApplicationListener,\
    org.springframework.cloud.bootstrap.LoggingSystemShutdownListener,\
    org.springframework.cloud.context.restart.RestartListener
 *BootstrapApplicationListener 监听了ApplicationEnvironmentPreparedEvent，
 *所以在事件触发时候会执行onApplicationEvent(),并且会增加BootstrapImportSelector Configuration
 *这个类，然后通过@Import(BootstrapImportSelector.class)导入该类，他实现了selectImports这个方法
 *然后 List<String> names = new ArrayList<>(SpringFactoriesLoader.loadFactoryNames(BootstrapConfiguration.class, classLoader));
 *调用可以得到springcloud的类启动
 *org.springframework.cloud.bootstrap.BootstrapConfiguration=
 *org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration,\
org.springframework.cloud.bootstrap.encrypt.EncryptionBootstrapConfiguration,\
org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration,\
org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
 *
 * A listener that prepares a SpringApplication (e.g. populating its Environment) by
 * delegating to {@link ApplicationContextInitializer} beans in a separate bootstrap
 * context. The bootstrap context is a SpringApplication created from sources defined in
 * spring.factories as {@link BootstrapConfiguration}, and initialized with external
 * config taken from "bootstrap.properties" (or yml), instead of the normal
 * "application.properties".
 *此监视器是用于响应ApplicationEnvironmentPreparedEvent应用环境变量预初始化事件，
 *表明BootstrapContext的加载时机在用户上下文之前，且其加载顺序比ConfigFileApplicationListener监听器超前
 * @author Dave Syer
 *
 */
public class BootstrapApplicationListener
		implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

	/**
	 * Property source name for bootstrap.
	 */
	public static final String BOOTSTRAP_PROPERTY_SOURCE_NAME = "bootstrap";

	/**
	 * The default order for this listener.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	/**
	 * The name of the default properties.
	 */
	public static final String DEFAULT_PROPERTIES = "defaultProperties";

	private int order = DEFAULT_ORDER;

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		// 获取环境变量对象
		ConfigurableEnvironment environment = event.getEnvironment();
		// 读取spring.cloud.bootstrap.enabled环境属性，默认为true。可通过系统变量设置
		if (!environment.getProperty("spring.cloud.bootstrap.enabled", Boolean.class,
				true)) {
			return;
		}
		// don't listen to events in a bootstrap context
		//判断是否已经有了bootstrap,这里第一次会没有的，在下面新增，执行第二次判断会有值。
		if (environment.getPropertySources().contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
			return;
		}
		// 寻找当前环境是否已存在BootstrapContext
		ConfigurableApplicationContext context = null;
		String configName = environment
				.resolvePlaceholders("${spring.cloud.bootstrap.name:bootstrap}");
		//这里都是springboot的Initializer，这里是不成立的，不会走
		for (ApplicationContextInitializer<?> initializer : event.getSpringApplication()
				.getInitializers()) {
			if (initializer instanceof ParentContextApplicationContextInitializer) {
				context = findBootstrapContext(
						(ParentContextApplicationContextInitializer) initializer,
						configName);
			}
		}
		// 如果还没有被创建，则开始创建
		if (context == null) {
			context = bootstrapServiceContext(environment, event.getSpringApplication(),
					configName);
			// 注册注销监听器
			event.getSpringApplication()
					.addListeners(new CloseContextOnFailureApplicationListener(context));
		}

		apply(context, event.getSpringApplication(), environment);
	}

	private ConfigurableApplicationContext findBootstrapContext(
			ParentContextApplicationContextInitializer initializer, String configName) {
		Field field = ReflectionUtils
				.findField(ParentContextApplicationContextInitializer.class, "parent");
		ReflectionUtils.makeAccessible(field);
		ConfigurableApplicationContext parent = safeCast(
				ConfigurableApplicationContext.class,
				ReflectionUtils.getField(field, initializer));
		if (parent != null && !configName.equals(parent.getId())) {
			parent = safeCast(ConfigurableApplicationContext.class, parent.getParent());
		}
		return parent;
	}

	private <T> T safeCast(Class<T> type, Object object) {
		try {
			return type.cast(object);
		}
		catch (ClassCastException e) {
			return null;
		}
	}

	private ConfigurableApplicationContext bootstrapServiceContext(
			ConfigurableEnvironment environment, final SpringApplication application,
			String configName) {
		// create empty environment
		StandardEnvironment bootstrapEnvironment = new StandardEnvironment();
		MutablePropertySources bootstrapProperties = bootstrapEnvironment
				.getPropertySources();
		for (PropertySource<?> source : bootstrapProperties) {
			bootstrapProperties.remove(source.getName());
		}
		// 读取spring.cloud.bootstrap.location属性，一般通过系统变量设置，默认为空
		String configLocation = environment
				.resolvePlaceholders("${spring.cloud.bootstrap.location:}");
		Map<String, Object> bootstrapMap = new HashMap<>();
		
		bootstrapMap.put("spring.config.name", configName);
		// if an app (or test) uses spring.main.web-application-type=reactive, bootstrap
		// will fail
		// force the environment to use none, because if though it is set below in the
		// builder
		// the environment overrides it
		bootstrapMap.put("spring.main.web-application-type", "none");
		// 加载bootstrapContext配置文件的路径，与spring.config.name搭配使用
		if (StringUtils.hasText(configLocation)) {
			bootstrapMap.put("spring.config.location", configLocation);
		}
		//这里新增了context的为bootstrap的容器
		bootstrapProperties.addFirst(
				new MapPropertySource(BOOTSTRAP_PROPERTY_SOURCE_NAME, bootstrapMap));
		for (PropertySource<?> source : environment.getPropertySources()) {
			if (source instanceof StubPropertySource) {
				continue;
			}
			bootstrapProperties.addLast(source);
		}
		// TODO: is it possible or sensible to share a ResourceLoader?
		SpringApplicationBuilder builder = new SpringApplicationBuilder()
				.profiles(environment.getActiveProfiles()).bannerMode(Mode.OFF)
				// 应用bootstrap本身的环境变量
				.environment(bootstrapEnvironment)
				// Don't use the default properties in this builder
				.registerShutdownHook(false).logStartupInfo(false)
				.web(WebApplicationType.NONE);
		final SpringApplication builderApplication = builder.application();
		// 配置入口函数类
		if (builderApplication.getMainApplicationClass() == null) {
			// gh_425:
			// SpringApplication cannot deduce the MainApplicationClass here
			// if it is booted from SpringBootServletInitializer due to the
			// absense of the "main" method in stackTraces.
			// But luckily this method's second parameter "application" here
			// carries the real MainApplicationClass which has been explicitly
			// set by SpringBootServletInitializer itself already.
			builder.main(application.getMainApplicationClass());
		}
		if (environment.getPropertySources().contains("refreshArgs")) {
			// If we are doing a context refresh, really we only want to refresh the
			// Environment, and there are some toxic listeners (like the
			// LoggingApplicationListener) that affect global static state, so we need a
			// way to switch those off.
			builderApplication
					.setListeners(filterListeners(builderApplication.getListeners()));
		}
		//这里增加了BootstrapImportSelectorConfiguration这个作为springboot的启动了，然后解析这个类
		builder.sources(BootstrapImportSelectorConfiguration.class);
		//构建出BootstrapContext
		final ConfigurableApplicationContext context = builder.run();
		// gh-214 using spring.application.name=bootstrap to set the context id via
		// `ContextIdApplicationContextInitializer` prevents apps from getting the actual
		// spring.application.name
		// during the bootstrap phase.
		 // 设置bootstrapContext的别名为bootstrap
		context.setId("bootstrap");
		// Make the bootstrap context a parent of the app context
		 // 设置BootstrapContext成为应用Context的父容器,此处分析见步骤4
		addAncestorInitializer(application, context);
		// It only has properties in it now that we don't want in the parent so remove
		// it (and it will be added back later)
		bootstrapProperties.remove(BOOTSTRAP_PROPERTY_SOURCE_NAME);
		//运行完新环境，然后添加一个初始化器AncestorInitializer，里面创建ParentContextApplicationContextInitializer，
		//将新上下文封装进去，这样下次就可以直接取出来用户，不用需要重新创建上下文了，然后删除bootstrap属性源，最后对老环境的属性源进行整合：
		mergeDefaultProperties(environment.getPropertySources(), bootstrapProperties);
		return context;
	}

	private Collection<? extends ApplicationListener<?>> filterListeners(
			Set<ApplicationListener<?>> listeners) {
		Set<ApplicationListener<?>> result = new LinkedHashSet<>();
		for (ApplicationListener<?> listener : listeners) {
			if (!(listener instanceof LoggingApplicationListener)
					&& !(listener instanceof LoggingSystemShutdownListener)) {
				result.add(listener);
			}
		}
		return result;
	}

	private void mergeDefaultProperties(MutablePropertySources environment,
			MutablePropertySources bootstrap) {
		String name = DEFAULT_PROPERTIES;
		if (bootstrap.contains(name)) {
			PropertySource<?> source = bootstrap.get(name);
			if (!environment.contains(name)) {
				environment.addLast(source);
			}
			else {
				PropertySource<?> target = environment.get(name);
				if (target instanceof MapPropertySource && target != source
						&& source instanceof MapPropertySource) {
					Map<String, Object> targetMap = ((MapPropertySource) target)
							.getSource();
					Map<String, Object> map = ((MapPropertySource) source).getSource();
					for (String key : map.keySet()) {
						if (!target.containsProperty(key)) {
							targetMap.put(key, map.get(key));
						}
					}
				}
			}
		}
		mergeAdditionalPropertySources(environment, bootstrap);
	}

	private void mergeAdditionalPropertySources(MutablePropertySources environment,
			MutablePropertySources bootstrap) {
		PropertySource<?> defaultProperties = environment.get(DEFAULT_PROPERTIES);
		ExtendedDefaultPropertySource result = defaultProperties instanceof ExtendedDefaultPropertySource
				? (ExtendedDefaultPropertySource) defaultProperties
				: new ExtendedDefaultPropertySource(DEFAULT_PROPERTIES,
						defaultProperties);
		for (PropertySource<?> source : bootstrap) {
			if (!environment.contains(source.getName())) {
				result.add(source);
			}
		}
		for (String name : result.getPropertySourceNames()) {
			bootstrap.remove(name);
		}
		addOrReplace(environment, result);
		addOrReplace(bootstrap, result);
	}

	private void addOrReplace(MutablePropertySources environment,
			PropertySource<?> result) {
		if (environment.contains(result.getName())) {
			environment.replace(result.getName(), result);
		}
		else {
			environment.addLast(result);
		}
	}

	private void addAncestorInitializer(SpringApplication application,
			ConfigurableApplicationContext context) {
		boolean installed = false;
		//从spring.factories文件中获取初始化器的配置类且类型为AncestorInitializer
		for (ApplicationContextInitializer<?> initializer : application
				.getInitializers()) {
			if (initializer instanceof AncestorInitializer) {
				installed = true;
				// New parent
				((AncestorInitializer) initializer).setParent(context);
			}
		}
		 //默认情况下是没有配围置AncestorInitializer这样的类，此处是则执行由BootstrapListener提供的内部类
		if (!installed) {
			//将BootstrapContext作为父容器传到AncestorInitializer实例中，并将其放入SpringApplication实例的初始器列表中
			application.addInitializers(new AncestorInitializer(context));
		}

	}

	private void apply(ConfigurableApplicationContext context,
			SpringApplication application, ConfigurableEnvironment environment) {
		@SuppressWarnings("rawtypes")
		List<ApplicationContextInitializer> initializers = getOrderedBeansOfType(context,
				ApplicationContextInitializer.class);
		application.addInitializers(initializers
				.toArray(new ApplicationContextInitializer[initializers.size()]));
		addBootstrapDecryptInitializer(application);
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void addBootstrapDecryptInitializer(SpringApplication application) {
		DelegatingEnvironmentDecryptApplicationInitializer decrypter = null;
		for (ApplicationContextInitializer<?> ini : application.getInitializers()) {
			if (ini instanceof EnvironmentDecryptApplicationInitializer) {
				
				ApplicationContextInitializer del = (ApplicationContextInitializer) ini;
				decrypter = new DelegatingEnvironmentDecryptApplicationInitializer(del);
			}
		}
		if (decrypter != null) {
			application.addInitializers(decrypter);
		}
	}

	private <T> List<T> getOrderedBeansOfType(ListableBeanFactory context,
			Class<T> type) {
		List<T> result = new ArrayList<T>();
		for (String name : context.getBeanNamesForType(type)) {
			result.add(context.getBean(name, type));
		}
		AnnotationAwareOrderComparator.sort(result);
		return result;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	private static class AncestorInitializer implements
			ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

		private ConfigurableApplicationContext parent;

		AncestorInitializer(ConfigurableApplicationContext parent) {
			this.parent = parent;
		}

		public void setParent(ConfigurableApplicationContext parent) {
			this.parent = parent;
		}

		@Override
		public int getOrder() {
			// Need to run not too late (so not unordered), so that, for instance, the
			// ContextIdApplicationContextInitializer runs later and picks up the merged
			// Environment. Also needs to be quite early so that other initializers can
			// pick up the parent (especially the Environment).
			return Ordered.HIGHEST_PRECEDENCE + 5;
		}

		@Override
		public void initialize(ConfigurableApplicationContext context) {
			while (context.getParent() != null && context.getParent() != context) {
				context = (ConfigurableApplicationContext) context.getParent();
			}
			reorderSources(context.getEnvironment());
			new ParentContextApplicationContextInitializer(this.parent)
					.initialize(context);
		}

		private void reorderSources(ConfigurableEnvironment environment) {
			PropertySource<?> removed = environment.getPropertySources()
					.remove(DEFAULT_PROPERTIES);
			if (removed instanceof ExtendedDefaultPropertySource) {
				ExtendedDefaultPropertySource defaultProperties = (ExtendedDefaultPropertySource) removed;
				environment.getPropertySources().addLast(new MapPropertySource(
						DEFAULT_PROPERTIES, defaultProperties.getSource()));
				for (PropertySource<?> source : defaultProperties.getPropertySources()
						.getPropertySources()) {
					if (!environment.getPropertySources().contains(source.getName())) {
						environment.getPropertySources().addBefore(DEFAULT_PROPERTIES,
								source);
					}
				}
			}
		}

	}

	/**
	 * A special initializer designed to run before the property source bootstrap and
	 * decrypt any properties needed there (e.g. URL of config server).
	 */
	@Order(Ordered.HIGHEST_PRECEDENCE + 9)
	private static class DelegatingEnvironmentDecryptApplicationInitializer
			implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		private ApplicationContextInitializer<ConfigurableApplicationContext> delegate;

		DelegatingEnvironmentDecryptApplicationInitializer(
				ApplicationContextInitializer<ConfigurableApplicationContext> delegate) {
			this.delegate = delegate;
		}

		@Override
		public void initialize(ConfigurableApplicationContext applicationContext) {
			this.delegate.initialize(applicationContext);
		}

	}

	private static class ExtendedDefaultPropertySource
			extends SystemEnvironmentPropertySource {

		private final CompositePropertySource sources;

		private final List<String> names = new ArrayList<>();

		ExtendedDefaultPropertySource(String name, PropertySource<?> propertySource) {
			super(name, findMap(propertySource));
			this.sources = new CompositePropertySource(name);
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> findMap(PropertySource<?> propertySource) {
			if (propertySource instanceof MapPropertySource) {
				return (Map<String, Object>) propertySource.getSource();
			}
			return new LinkedHashMap<String, Object>();
		}

		public CompositePropertySource getPropertySources() {
			return this.sources;
		}

		public List<String> getPropertySourceNames() {
			return this.names;
		}

		public void add(PropertySource<?> source) {
			// Only add map property sources added by boot, see gh-476
			if (source instanceof OriginTrackedMapPropertySource
					&& !this.names.contains(source.getName())) {
				this.sources.addPropertySource(source);
				this.names.add(source.getName());
			}
		}

		@Override
		public Object getProperty(String name) {
			if (this.sources.containsProperty(name)) {
				return this.sources.getProperty(name);
			}
			return super.getProperty(name);
		}

		@Override
		public boolean containsProperty(String name) {
			if (this.sources.containsProperty(name)) {
				return true;
			}
			return super.containsProperty(name);
		}

		@Override
		public String[] getPropertyNames() {
			List<String> names = new ArrayList<>();
			names.addAll(Arrays.asList(this.sources.getPropertyNames()));
			names.addAll(Arrays.asList(super.getPropertyNames()));
			return names.toArray(new String[0]);
		}

	}

	private static class CloseContextOnFailureApplicationListener
			implements SmartApplicationListener {

		private final ConfigurableApplicationContext context;

		CloseContextOnFailureApplicationListener(ConfigurableApplicationContext context) {
			this.context = context;
		}

		@Override
		public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
			return ApplicationFailedEvent.class.isAssignableFrom(eventType);
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			if (event instanceof ApplicationFailedEvent) {
				this.context.close();
			}

		}

	}

}
