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

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 * springboot在解析加載类会用到这个东西
 * 
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	private final ConditionContextImpl context;

	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry, @Nullable Environment environment,
			@Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
	}

	/**
	 * Determine if an item should be skipped based on {@code @Conditional}
	 * annotations. The {@link ConfigurationPhase} will be deduced from the type of
	 * item (i.e. a {@code @Configuration} class will be
	 * {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 * 
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		return shouldSkip(metadata, null);
	}

	/**
	 * 这个方法主要是如果是解析阶段则跳过，如果是注册阶段则不跳过 Determine if an item should be skipped based on
	 * {@code @Conditional} annotations.
	 * 
	 * @param metadata the meta data
	 * @param phase    the phase of the call
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {

		// 若没有被@Conditional或其派生注解所标注，则不会跳过
		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			return false;
		}
		// 没有指定phase，注意phase可以分为PARSE_CONFIGURATION或REGISTER_BEAN类型
		if (phase == null) {
			// 若标有@Component，@Import，@Bean或@Configuration等注解的话，则说明是PARSE_CONFIGURATION类型
			if (metadata instanceof AnnotationMetadata
					&& ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
			} // 否则是REGISTER_BEAN类型
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
		}

		List<Condition> conditions = new ArrayList<>();
		// TODO 获得所有标有@Conditional注解或其派生注解里面的Condition接口实现类并实例化成对象。
		// 比如@Conditional(OnBeanCondition.class)则获得OnBeanCondition.class，OnBeanCondition.class往往实现了Condition接口
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			for (String conditionClass : conditionClasses) {
				Condition condition = getCondition(conditionClass, this.context.getClassLoader());
				conditions.add(condition);
			}
		}
		// 排序，即按照Condition的优先级进行排序
		AnnotationAwareOrderComparator.sort(conditions);

		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;
			if (condition instanceof ConfigurationCondition) {
				// 从condition中获得对bean是解析还是注册,对于ConfigurationPhase的接口，有
				// PARSE_CONFIGURATION解析阶段，REGISTER_BEAN注册阶段
				requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
			}
			// 若requiredPhase为null或获取的阶段类型正是当前阶段类型且不符合condition的matches条件，则跳过
			// shouldSkip这个方法执行的逻辑主要是如果是解析阶段则跳过，如果是注册阶段则不跳过；
			// 如果是在注册阶段即REGISTER_BEAN阶段的话，
			// 此时会得到所有的Condition接口的具体实现类并实例化这些实现类，然后再执行下面关键的代码进行判断是否需要跳过。
			// matches调用了具体的实现类，看具体的调用数据
			// 上面代码最重要的逻辑是调用了Condition接口的具体实现类的matches方法，
			// 若matches返回false，则跳过，不进行注册bean的操作；若matches返回true，则不跳过，进行注册bean的操作；
			if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(),
				true);
		Object values = (attributes != null ? attributes.get("value") : null);
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

	private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
		Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
		return (Condition) BeanUtils.instantiateClass(conditionClass);
	}

	/**
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;

		private final Environment environment;

		private final ResourceLoader resourceLoader;

		@Nullable
		private final ClassLoader classLoader;

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry, @Nullable Environment environment,
				@Nullable ResourceLoader resourceLoader) {

			this.registry = registry;
			this.beanFactory = deduceBeanFactory(registry);
			this.environment = (environment != null ? environment : deduceEnvironment(registry));
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory) {
				return (ConfigurableListableBeanFactory) source;
			}
			if (source instanceof ConfigurableApplicationContext) {
				return (((ConfigurableApplicationContext) source).getBeanFactory());
			}
			return null;
		}

		private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable) {
				return ((EnvironmentCapable) source).getEnvironment();
			}
			return new StandardEnvironment();
		}

		private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader) {
				return (ResourceLoader) source;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
				@Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
