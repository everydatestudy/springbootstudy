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

package org.springframework.cloud.bootstrap.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.cloud.bootstrap.BootstrapApplicationListener;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.logging.LoggingRebinder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
@Configuration
@EnableConfigurationProperties(PropertySourceBootstrapProperties.class)
public class PropertySourceBootstrapConfiguration implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	/**
	 * Bootstrap property source name.
	 */
	public static final String BOOTSTRAP_PROPERTY_SOURCE_NAME = BootstrapApplicationListener.BOOTSTRAP_PROPERTY_SOURCE_NAME
			+ "Properties";

	private static Log logger = LogFactory
			.getLog(PropertySourceBootstrapConfiguration.class);

	private int order = Ordered.HIGHEST_PRECEDENCE + 10;

	@Autowired(required = false)
	private List<PropertySourceLocator> propertySourceLocators = new ArrayList<>();

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setPropertySourceLocators(
			Collection<PropertySourceLocator> propertySourceLocators) {
		this.propertySourceLocators = new ArrayList<>(propertySourceLocators);
	}
	//此类也是ApplicationContextInitializer接口的实现类，阅读过cloud源码的都知道，此类被调用是在子类上下文初始化的时候，我们主要看下其复写的initialize()方法
	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		CompositePropertySource composite = new CompositePropertySource(
				BOOTSTRAP_PROPERTY_SOURCE_NAME);
		// 对在boostrap上下文类型为PropertySourceLocator的bean集合进行排序 
		AnnotationAwareOrderComparator.sort(this.propertySourceLocators);
		boolean empty = true;
		ConfigurableEnvironment environment = applicationContext.getEnvironment();
		for (PropertySourceLocator locator : this.propertySourceLocators) {
			PropertySource<?> source = null;
			 // 读取外部配置源
			source = locator.locate(environment);
			if (source == null) {
				continue;
			}
			logger.info("Located property source: " + source);
			composite.addPropertySource(source);
			empty = false;
		}
		if (!empty) {
			MutablePropertySources propertySources = environment.getPropertySources();
			String logConfig = environment.resolvePlaceholders("${logging.config:}");
			LogFile logFile = LogFile.get(environment);
			if (propertySources.contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
				propertySources.remove(BOOTSTRAP_PROPERTY_SOURCE_NAME);
			}
			// 插入至Environment环境对象中
			insertPropertySources(propertySources, composite);
			reinitializeLoggingSystem(environment, logConfig, logFile);
			setLogLevels(applicationContext, environment);
			handleIncludedProfiles(environment);
		}
	}

	private void reinitializeLoggingSystem(ConfigurableEnvironment environment,
			String oldLogConfig, LogFile oldLogFile) {
		Map<String, Object> props = Binder.get(environment)
				.bind("logging", Bindable.mapOf(String.class, Object.class))
				.orElseGet(Collections::emptyMap);
		if (!props.isEmpty()) {
			String logConfig = environment.resolvePlaceholders("${logging.config:}");
			LogFile logFile = LogFile.get(environment);
			LoggingSystem system = LoggingSystem
					.get(LoggingSystem.class.getClassLoader());
			try {
				ResourceUtils.getURL(logConfig).openStream().close();
				// Three step initialization that accounts for the clean up of the logging
				// context before initialization. Spring Boot doesn't initialize a logging
				// system that hasn't had this sequence applied (since 1.4.1).
				system.cleanUp();
				system.beforeInitialize();
				system.initialize(new LoggingInitializationContext(environment),
						logConfig, logFile);
			}
			catch (Exception ex) {
				PropertySourceBootstrapConfiguration.logger
						.warn("Error opening logging config file " + logConfig, ex);
			}
		}
	}

	private void setLogLevels(ConfigurableApplicationContext applicationContext,
			ConfigurableEnvironment environment) {
		LoggingRebinder rebinder = new LoggingRebinder();
		rebinder.setEnvironment(environment);
		// We can't fire the event in the ApplicationContext here (too early), but we can
		// create our own listener and poke it (it doesn't need the key changes)
		rebinder.onApplicationEvent(new EnvironmentChangeEvent(applicationContext,
				Collections.<String>emptySet()));
	}
	//1.上述的配置属性均会映射到PropertySourceBootstrapProperties实体类中，且其中的默认值罗列如下

	private void insertPropertySources(MutablePropertySources propertySources,
			CompositePropertySource composite) {
		// 外部源配置集合
		MutablePropertySources incoming = new MutablePropertySources();
		incoming.addFirst(composite);
		 // 从外部源配置源集合中读取PropertySourceBootstrapProperties的相关属性
		 // 例如spring.cloud.config.overrideSystemProperties等属性 
		PropertySourceBootstrapProperties remoteProperties = new PropertySourceBootstrapProperties();
		Binder.get(environment(incoming)).bind("spring.cloud.config",
				Bindable.ofInstance(remoteProperties));
		if (!remoteProperties.isAllowOverride() || (!remoteProperties.isOverrideNone()
				&& remoteProperties.isOverrideSystemProperties())) {
			propertySources.addFirst(composite);
			return;
		}
		// spring.cloud.config.override-none=true则处于最低读取位
		if (remoteProperties.isOverrideNone()) {
			propertySources.addLast(composite);
			return;
		}
		 // 根据spring.cloud.config.override-system-properties属性判断是放在systemProperties前还是后
		if (propertySources
				.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
			if (!remoteProperties.isOverrideSystemProperties()) {
				propertySources.addAfter(
						StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
						composite);
			}
			else {
				propertySources.addBefore(
						StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
						composite);
			}
		}
		else {
			propertySources.addLast(composite);
		}
	}

	private Environment environment(MutablePropertySources incoming) {
		StandardEnvironment environment = new StandardEnvironment();
		for (PropertySource<?> source : environment.getPropertySources()) {
			environment.getPropertySources().remove(source.getName());
		}
		for (PropertySource<?> source : incoming) {
			environment.getPropertySources().addLast(source);
		}
		return environment;
	}

	private void handleIncludedProfiles(ConfigurableEnvironment environment) {
		Set<String> includeProfiles = new TreeSet<>();
		for (PropertySource<?> propertySource : environment.getPropertySources()) {
			addIncludedProfilesTo(includeProfiles, propertySource);
		}
		List<String> activeProfiles = new ArrayList<>();
		Collections.addAll(activeProfiles, environment.getActiveProfiles());

		// If it's already accepted we assume the order was set intentionally
		includeProfiles.removeAll(activeProfiles);
		if (includeProfiles.isEmpty()) {
			return;
		}
		// Prepend each added profile (last wins in a property key clash)
		for (String profile : includeProfiles) {
			activeProfiles.add(0, profile);
		}
		environment.setActiveProfiles(
				activeProfiles.toArray(new String[activeProfiles.size()]));
	}

	private Set<String> addIncludedProfilesTo(Set<String> profiles,
			PropertySource<?> propertySource) {
		if (propertySource instanceof CompositePropertySource) {
			for (PropertySource<?> nestedPropertySource : ((CompositePropertySource) propertySource)
					.getPropertySources()) {
				addIncludedProfilesTo(profiles, nestedPropertySource);
			}
		}
		else {
			Collections.addAll(profiles, getProfilesForValue(propertySource.getProperty(
					ConfigFileApplicationListener.INCLUDE_PROFILES_PROPERTY)));
		}
		return profiles;
	}

	private String[] getProfilesForValue(Object property) {
		final String value = (property == null ? null : property.toString());
		return property == null ? new String[0]
				: StringUtils.tokenizeToStringArray(value, ",");
	}

}
