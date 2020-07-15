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

package org.springframework.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link DeferredImportSelector} to handle {@link EnableAutoConfiguration
 * auto-configuration}. This class can also be subclassed if a custom variant of
 * {@link EnableAutoConfiguration @EnableAutoConfiguration} is needed.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Madhura Bhave
 * @since 1.3.0
 * @see EnableAutoConfiguration
 */
public class AutoConfigurationImportSelector
		implements DeferredImportSelector, BeanClassLoaderAware, ResourceLoaderAware,
		BeanFactoryAware, EnvironmentAware, Ordered {

	private static final AutoConfigurationEntry EMPTY_ENTRY = new AutoConfigurationEntry();

	private static final String[] NO_IMPORTS = {};

	private static final Log logger = LogFactory
			.getLog(AutoConfigurationImportSelector.class);

	private static final String PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE = "spring.autoconfigure.exclude";

	private ConfigurableListableBeanFactory beanFactory;

	private Environment environment;

	private ClassLoader beanClassLoader;

	private ResourceLoader resourceLoader;

	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		if (!isEnabled(annotationMetadata)) {
			return NO_IMPORTS;
		}
		// 加载spring-autoconfigure-metadata.properties文件的键值对数据到autoConfigurationMetadata，
		// 供AutoConfigurationImportSelector过滤加载。注意spring-autoconfigure-metadata.properties文件为maven build构建的时候生成的
		AutoConfigurationMetadata autoConfigurationMetadata = AutoConfigurationMetadataLoader
				.loadMetadata(this.beanClassLoader);
		// 基于@Configuration注解的元数据相关过滤条件返回自动配置类，如@SpringBootApplication(exclude = FreeMarkerAutoConfiguration.class)，
		// 则要排除FreeMarkerAutoConfiguration这个自动配置类
		AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(
				autoConfigurationMetadata, annotationMetadata);
		return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());
	}

	/**
	 * Return the {@link AutoConfigurationEntry} based on the {@link AnnotationMetadata}
	 * of the importing {@link Configuration @Configuration} class.
	 * @param autoConfigurationMetadata the auto-configuration metadata
	 * @param annotationMetadata the annotation metadata of the configuration class
	 * @return the auto-configurations that should be imported
	 */
	// 获取符合条件的自动配置类，避免加载不必要的自动配置类从而造成内存浪费
	protected AutoConfigurationEntry getAutoConfigurationEntry(
			AutoConfigurationMetadata autoConfigurationMetadata,
			AnnotationMetadata annotationMetadata) {
		// 获取是否有配置spring.boot.enableautoconfiguration属性，默认返回true
		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}
		// 获得@Congiguration标注的Configuration类即被审视introspectedClass的注解数据，
		// 比如：@SpringBootApplication(exclude = FreeMarkerAutoConfiguration.class)
		// 将会获取到exclude = FreeMarkerAutoConfiguration.class和excludeName=""的注解数据
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
		// 【1】得到spring.factories文件配置的所有自动配置类
		List<String> configurations = getCandidateConfigurations(annotationMetadata,
				attributes);
		// 利用LinkedHashSet移除重复的配置类
		configurations = removeDuplicates(configurations);
		// 得到要排除的自动配置类，比如注解属性exclude的配置类
		// 比如：@SpringBootApplication(exclude = FreeMarkerAutoConfiguration.class)
		// 将会获取到exclude = FreeMarkerAutoConfiguration.class的注解数据
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		// 检查要被排除的配置类，因为有些不是自动配置类，故要抛出异常
		checkExcludedClasses(configurations, exclusions);
		// 【2】将要排除的配置类移除
		configurations.removeAll(exclusions);
		// 【3】因为从spring.factories文件获取的自动配置类太多，如果有些不必要的自动配置类都加载进内存，会造成内存浪费，因此这里需要进行过滤
		// 注意这里会调用AutoConfigurationImportFilter的match方法来判断是否符合@ConditionalOnBean,@ConditionalOnClass或@ConditionalOnWebApplication，后面会重点分析一下
		configurations = filter(configurations, autoConfigurationMetadata);
		// 【4】获取了符合条件的自动配置类后，此时触发AutoConfigurationImportEvent事件，
		// 目的是告诉ConditionEvaluationReport条件评估报告器对象来记录符合条件的自动配置类
		// 该事件什么时候会被触发？--> 在刷新容器时调用invokeBeanFactoryPostProcessors后置处理器时触发
		fireAutoConfigurationImportEvents(configurations, exclusions);
		// 【5】将符合条件和要排除的自动配置类封装进AutoConfigurationEntry对象，并返回
		return new AutoConfigurationEntry(configurations, exclusions);
	}

	@Override
	public Class<? extends Group> getImportGroup() {
		return AutoConfigurationGroup.class;
	}

	protected boolean isEnabled(AnnotationMetadata metadata) {
		if (getClass() == AutoConfigurationImportSelector.class) {
			// 获取是否有配置spring.boot.enableautoconfiguration属性，默认返回true
			return getEnvironment().getProperty(
					EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY, Boolean.class,
					true);
		}
		return true;
	}

	/**
	 * Return the appropriate {@link AnnotationAttributes} from the
	 * {@link AnnotationMetadata}. By default this method will return attributes for
	 * {@link #getAnnotationClass()}.
	 * @param metadata the annotation metadata
	 * @return annotation attributes
	 */
	protected AnnotationAttributes getAttributes(AnnotationMetadata metadata) {
		String name = getAnnotationClass().getName();
		AnnotationAttributes attributes = AnnotationAttributes
				.fromMap(metadata.getAnnotationAttributes(name, true));
		Assert.notNull(attributes,
				() -> "No auto-configuration attributes found. Is "
						+ metadata.getClassName() + " annotated with "
						+ ClassUtils.getShortName(name) + "?");
		return attributes;
	}

	/**
	 * Return the source annotation class used by the selector.
	 * @return the annotation class
	 */
	protected Class<?> getAnnotationClass() {
		return EnableAutoConfiguration.class;
	}

	/**
	 * Return the auto-configuration class names that should be considered. By default
	 * this method will load candidates using {@link SpringFactoriesLoader} with
	 * {@link #getSpringFactoriesLoaderFactoryClass()}.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return a list of candidate configurations
	 */
	protected List<String> getCandidateConfigurations(AnnotationMetadata metadata,
			AnnotationAttributes attributes) {
		List<String> configurations = SpringFactoriesLoader.loadFactoryNames( // 得到所有自动配置类
				getSpringFactoriesLoaderFactoryClass(), getBeanClassLoader()); // getSpringFactoriesLoaderFactoryClass()返回EnableAutoConfiguration.class
		Assert.notEmpty(configurations,
				"No auto configuration classes found in META-INF/spring.factories. If you "
						+ "are using a custom packaging, make sure that file is correct.");
		return configurations;
	}

	/**
	 * Return the class used by {@link SpringFactoriesLoader} to load configuration
	 * candidates.
	 * @return the factory class
	 */
	protected Class<?> getSpringFactoriesLoaderFactoryClass() {
		return EnableAutoConfiguration.class;
	}

	private void checkExcludedClasses(List<String> configurations,
			Set<String> exclusions) {
		List<String> invalidExcludes = new ArrayList<>(exclusions.size());
		for (String exclusion : exclusions) {
			if (ClassUtils.isPresent(exclusion, getClass().getClassLoader())
					&& !configurations.contains(exclusion)) {
				invalidExcludes.add(exclusion);
			}
		}
		if (!invalidExcludes.isEmpty()) {
			handleInvalidExcludes(invalidExcludes);
		}
	}

	/**
	 * Handle any invalid excludes that have been specified.
	 * @param invalidExcludes the list of invalid excludes (will always have at least one
	 * element)
	 */
	protected void handleInvalidExcludes(List<String> invalidExcludes) {
		StringBuilder message = new StringBuilder();
		for (String exclude : invalidExcludes) {
			message.append("\t- ").append(exclude).append(String.format("%n"));
		}
		throw new IllegalStateException(String
				.format("The following classes could not be excluded because they are"
						+ " not auto-configuration classes:%n%s", message));
	}

	/**
	 * Return any exclusions that limit the candidate configurations.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return exclusions or an empty set
	 */
	protected Set<String> getExclusions(AnnotationMetadata metadata,
			AnnotationAttributes attributes) {
		Set<String> excluded = new LinkedHashSet<>();
		excluded.addAll(asList(attributes, "exclude"));
		excluded.addAll(Arrays.asList(attributes.getStringArray("excludeName")));
		excluded.addAll(getExcludeAutoConfigurationsProperty());
		return excluded;
	}

	private List<String> getExcludeAutoConfigurationsProperty() {
		if (getEnvironment() instanceof ConfigurableEnvironment) {
			Binder binder = Binder.get(getEnvironment());
			return binder.bind(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class)
					.map(Arrays::asList).orElse(Collections.emptyList());
		}
		String[] excludes = getEnvironment()
				.getProperty(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class);
		return (excludes != null) ? Arrays.asList(excludes) : Collections.emptyList();
	}

	private List<String> filter(List<String> configurations,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		long startTime = System.nanoTime();
		// 将从spring.factories中获取的自动配置类转出字符串数组
		String[] candidates = StringUtils.toStringArray(configurations);
		// 定义skip数组，是否需要跳过。注意skip数组与candidates数组顺序一一对应
		boolean[] skip = new boolean[candidates.length];
		boolean skipped = false;
		// getAutoConfigurationImportFilters方法：拿到OnBeanCondition，OnClassCondition和OnWebApplicationCondition
		// 然后遍历这三个条件类去过滤从spring.factories加载的大量配置类
		for (AutoConfigurationImportFilter filter : getAutoConfigurationImportFilters()) {
			// 调用各种aware方法，将beanClassLoader,beanFactory等注入到filter对象中，
			// 这里的filter对象即OnBeanCondition，OnClassCondition或OnWebApplicationCondition
			invokeAwareMethods(filter);
			// 判断各种filter来判断每个candidate（这里实质要通过candidate(自动配置类)拿到其标注的
			// @ConditionalOnClass,@ConditionalOnBean和@ConditionalOnWebApplication里面的注解值）是否匹配，
			// 注意candidates数组与match数组一一对应
			/* *********************【重点关注】******************************* */
			boolean[] match = filter.match(candidates, autoConfigurationMetadata);
			// 遍历match数组，注意match顺序跟candidates的自动配置类一一对应
			for (int i = 0; i < match.length; i++) {
				// 若有不匹配的话
				if (!match[i]) {
					// 不匹配的将记录在skip数组，标志skip[i]为true，也与candidates数组一一对应
					skip[i] = true;
					// 因为不匹配，将相应的自动配置类置空
					candidates[i] = null;
					// 标注skipped为true
					skipped = true;
				}
			}
		}
		// 这里表示若所有自动配置类经过OnBeanCondition，OnClassCondition和OnWebApplicationCondition过滤后，全部都匹配的话，则全部原样返回
		if (!skipped) {
			return configurations;
		}
		// 建立result集合来装匹配的自动配置类
		List<String> result = new ArrayList<>(candidates.length);
		for (int i = 0; i < candidates.length; i++) {
			// 若skip[i]为false，则说明是符合条件的自动配置类，此时添加到result集合中
			if (!skip[i]) {
				result.add(candidates[i]);
			}
		}
		// 打印日志
		if (logger.isTraceEnabled()) {
			int numberFiltered = configurations.size() - result.size();
			logger.trace("Filtered " + numberFiltered + " auto configuration class in "
					+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
					+ " ms");
		}
		// 最后返回符合条件的自动配置类
		return new ArrayList<>(result);
	}
	// 拿到OnBeanCondition，OnClassCondition和OnWebApplicationCondition
	protected List<AutoConfigurationImportFilter> getAutoConfigurationImportFilters() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class,
				this.beanClassLoader);
	}

	protected final <T> List<T> removeDuplicates(List<T> list) {
		return new ArrayList<>(new LinkedHashSet<>(list));
	}

	protected final List<String> asList(AnnotationAttributes attributes, String name) {
		String[] value = attributes.getStringArray(name);
		return Arrays.asList((value != null) ? value : new String[0]);
	}

	private void fireAutoConfigurationImportEvents(List<String> configurations,
			Set<String> exclusions) {
		// 从spring.factories总获取到AutoConfigurationImportListener即ConditionEvaluationReportAutoConfigurationImportListener
		List<AutoConfigurationImportListener> listeners = getAutoConfigurationImportListeners();
		if (!listeners.isEmpty()) {
			// 新建一个AutoConfigurationImportEvent事件
			AutoConfigurationImportEvent event = new AutoConfigurationImportEvent(this,
					configurations, exclusions);
			// 遍历刚获取到的AutoConfigurationImportListener
			for (AutoConfigurationImportListener listener : listeners) {
				// 这里调用各种Aware方法用于触发事件前赋值，比如设置factory,environment等
				invokeAwareMethods(listener);
				// 真正触发AutoConfigurationImportEvent事件即回调listener的onXXXEveent方法。这里用于记录自动配置类的评估信息
				listener.onAutoConfigurationImportEvent(event);
			}
		}
	}

	protected List<AutoConfigurationImportListener> getAutoConfigurationImportListeners() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportListener.class,
				this.beanClassLoader);
	}

	private void invokeAwareMethods(Object instance) {
		if (instance instanceof Aware) {
			if (instance instanceof BeanClassLoaderAware) {
				((BeanClassLoaderAware) instance)
						.setBeanClassLoader(this.beanClassLoader);
			}
			if (instance instanceof BeanFactoryAware) {
				((BeanFactoryAware) instance).setBeanFactory(this.beanFactory);
			}
			if (instance instanceof EnvironmentAware) {
				((EnvironmentAware) instance).setEnvironment(this.environment);
			}
			if (instance instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) instance).setResourceLoader(this.resourceLoader);
			}
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory);
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}

	protected final ConfigurableListableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	protected final Environment getEnvironment() {
		return this.environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	protected final ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	private static class AutoConfigurationGroup implements DeferredImportSelector.Group,
			BeanClassLoaderAware, BeanFactoryAware, ResourceLoaderAware {

		private final Map<String, AnnotationMetadata> entries = new LinkedHashMap<>();

		private final List<AutoConfigurationEntry> autoConfigurationEntries = new ArrayList<>();

		private ClassLoader beanClassLoader;

		private BeanFactory beanFactory;

		private ResourceLoader resourceLoader;

		private AutoConfigurationMetadata autoConfigurationMetadata;

		@Override
		public void setBeanClassLoader(ClassLoader classLoader) {
			this.beanClassLoader = classLoader;
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.resourceLoader = resourceLoader;
		}
		// 这里用来处理自动配置类，比如过滤掉不符合匹配条件的自动配置类
		@Override
		public void process(AnnotationMetadata annotationMetadata,
				DeferredImportSelector deferredImportSelector) {
			Assert.state(
					deferredImportSelector instanceof AutoConfigurationImportSelector,
					() -> String.format("Only %s implementations are supported, got %s",
							AutoConfigurationImportSelector.class.getSimpleName(),
							deferredImportSelector.getClass().getName()));
			// 1,调用getAutoConfigurationEntry方法得到自动配置类放入autoConfigurationEntry对象中
			AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector) deferredImportSelector)
					.getAutoConfigurationEntry(getAutoConfigurationMetadata(), // 这里注意autoConfigurationMetadata和annotationMetadata的区别，autoConfigurationMetadata的properteis的键是自动配置类+条件注解类，值是条件注解类里面的属性值，TODO 唯一得注意的是有些自定义的配置类或加载配置类不在里面，这里不太明白
							annotationMetadata); // annotationMetadata即的启动类标注有@SpringBootApplication的注解属性值
			// 2，又将封装了自动配置类的autoConfigurationEntry对象装进autoConfigurationEntries集合
			this.autoConfigurationEntries.add(autoConfigurationEntry);
			// 3，遍历刚获取的自动配置类
			for (String importClassName : autoConfigurationEntry.getConfigurations()) {
				// 这里符合条件的自动配置类作为key，annotationMetadata作为值放进entries集合
				this.entries.putIfAbsent(importClassName, annotationMetadata);
			}
		}
		// selectImports这个方法在上面的process方法后面调用
		@Override
		public Iterable<Entry> selectImports() {
			if (this.autoConfigurationEntries.isEmpty()) {
				return Collections.emptyList();
			}
			// 这里得到所有要排除的自动配置类的set集合
			Set<String> allExclusions = this.autoConfigurationEntries.stream()
					.map(AutoConfigurationEntry::getExclusions)
					.flatMap(Collection::stream).collect(Collectors.toSet());
			// 这里得到经过滤后所有符合条件的自动配置类的set集合
			Set<String> processedConfigurations = this.autoConfigurationEntries.stream()
					.map(AutoConfigurationEntry::getConfigurations)
					.flatMap(Collection::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			// 移除掉要排除的自动配置类
			processedConfigurations.removeAll(allExclusions);
			// 对标注有@Order注解的自动配置类进行排序，
			return sortAutoConfigurations(processedConfigurations,
					getAutoConfigurationMetadata())
							.stream()
							.map((importClassName) -> new Entry(
									this.entries.get(importClassName), importClassName))
							.collect(Collectors.toList());
		}

		private AutoConfigurationMetadata getAutoConfigurationMetadata() {
			if (this.autoConfigurationMetadata == null) {
				this.autoConfigurationMetadata = AutoConfigurationMetadataLoader
						.loadMetadata(this.beanClassLoader);
			}
			return this.autoConfigurationMetadata;
		}

		private List<String> sortAutoConfigurations(Set<String> configurations,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			return new AutoConfigurationSorter(getMetadataReaderFactory(),
					autoConfigurationMetadata).getInPriorityOrder(configurations);
		}

		private MetadataReaderFactory getMetadataReaderFactory() {
			try {
				return this.beanFactory.getBean(
						SharedMetadataReaderFactoryContextInitializer.BEAN_NAME,
						MetadataReaderFactory.class);
			}
			catch (NoSuchBeanDefinitionException ex) {
				return new CachingMetadataReaderFactory(this.resourceLoader);
			}
		}

	}

	protected static class AutoConfigurationEntry {

		private final List<String> configurations;

		private final Set<String> exclusions;

		private AutoConfigurationEntry() {
			this.configurations = Collections.emptyList();
			this.exclusions = Collections.emptySet();
		}

		/**
		 * Create an entry with the configurations that were contributed and their
		 * exclusions.
		 * @param configurations the configurations that should be imported
		 * @param exclusions the exclusions that were applied to the original list
		 */
		AutoConfigurationEntry(Collection<String> configurations,
				Collection<String> exclusions) {
			this.configurations = new ArrayList<>(configurations);
			this.exclusions = new HashSet<>(exclusions);
		}

		public List<String> getConfigurations() {
			return this.configurations;
		}

		public Set<String> getExclusions() {
			return this.exclusions;
		}

	}

}
