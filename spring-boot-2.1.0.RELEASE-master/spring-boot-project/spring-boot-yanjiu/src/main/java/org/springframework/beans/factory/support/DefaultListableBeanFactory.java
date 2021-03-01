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

package org.springframework.beans.factory.support;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.inject.Provider;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.core.OrderComparator;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CompositeIterator;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Spring's default implementation of the
 * {@link ConfigurableListableBeanFactory} and {@link BeanDefinitionRegistry}
 * interfaces: a full-fledged bean factory based on bean definition metadata,
 * extensible through post-processors.
 *
 * <p>
 * Typical usage is registering all bean definitions first (possibly read from a
 * bean definition file), before accessing beans. Bean lookup by name is
 * therefore an inexpensive operation in a local bean definition table,
 * operating on pre-resolved bean definition metadata objects.
 *
 * <p>
 * Note that readers for specific bean definition formats are typically
 * implemented separately rather than as bean factory subclasses: see for
 * example {@link PropertiesBeanDefinitionReader} and
 * {@link org.springframework.beans.factory.xml.XmlBeanDefinitionReader}.
 *
 * <p>
 * For an alternative implementation of the
 * {@link org.springframework.beans.factory.ListableBeanFactory} interface, have
 * a look at {@link StaticListableBeanFactory}, which manages existing bean
 * instances rather than creating new ones based on bean definitions.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 16 April 2001
 * @see #registerBeanDefinition
 * @see #addBeanPostProcessor
 * @see #getBean
 * @see #resolveDependency
 */
@SuppressWarnings("serial")
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
		implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

	@Nullable
	private static Class<?> javaxInjectProviderClass;

	static {
		try {
			javaxInjectProviderClass = ClassUtils.forName("javax.inject.Provider",
					DefaultListableBeanFactory.class.getClassLoader());
		} catch (ClassNotFoundException ex) {
			// JSR-330 API not available - Provider interface simply not supported then.
			javaxInjectProviderClass = null;
		}
	}

	/** Map from serialized id to factory instance. */
	private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories = new ConcurrentHashMap<>(
			8);

	/** Optional id for this factory, for serialization purposes. */
	@Nullable
	private String serializationId;

	/**
	 * Whether to allow re-registration of a different definition with the same
	 * name.
	 */
	private boolean allowBeanDefinitionOverriding = true;

	/** Whether to allow eager class loading even for lazy-init beans. */
	private boolean allowEagerClassLoading = true;

	/** Optional OrderComparator for dependency Lists and arrays. */
	@Nullable
	private Comparator<Object> dependencyComparator;

	/**
	 * Resolver to use for checking if a bean definition is an autowire candidate.
	 */
	private AutowireCandidateResolver autowireCandidateResolver = new SimpleAutowireCandidateResolver();

	/** Map from dependency type to corresponding autowired value. */
	private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);

	/** Map of bean definition objects, keyed by bean name. */
	private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

	/** Map of singleton and non-singleton bean names, keyed by dependency type. */
	private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);

	/** Map of singleton-only bean names, keyed by dependency type. */
	private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);

	/** List of bean definition names, in registration order. */
	private volatile List<String> beanDefinitionNames = new ArrayList<>(256);

	/** List of names of manually registered singletons, in registration order. */
	private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

	/** Cached array of bean definition names in case of frozen configuration. */
	@Nullable
	private volatile String[] frozenBeanDefinitionNames;

	/** Whether bean definition metadata may be cached for all beans. */
	private volatile boolean configurationFrozen = false;

	/**
	 * Create a new DefaultListableBeanFactory.
	 */
	public DefaultListableBeanFactory() {
		super();
	}

	/**
	 * Create a new DefaultListableBeanFactory with the given parent.
	 * 
	 * @param parentBeanFactory the parent BeanFactory
	 */
	public DefaultListableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		super(parentBeanFactory);
	}

	/**
	 * Specify an id for serialization purposes, allowing this BeanFactory to be
	 * deserialized from this id back into the BeanFactory object, if needed.
	 */
	public void setSerializationId(@Nullable String serializationId) {
		if (serializationId != null) {
			serializableFactories.put(serializationId, new WeakReference<>(this));
		} else if (this.serializationId != null) {
			serializableFactories.remove(this.serializationId);
		}
		this.serializationId = serializationId;
	}

	/**
	 * Return an id for serialization purposes, if specified, allowing this
	 * BeanFactory to be deserialized from this id back into the BeanFactory object,
	 * if needed.
	 * 
	 * @since 4.1.2
	 */
	@Nullable
	public String getSerializationId() {
		return this.serializationId;
	}

	/**
	 * Set whether it should be allowed to override bean definitions by registering
	 * a different definition with the same name, automatically replacing the
	 * former. If not, an exception will be thrown. This also applies to overriding
	 * aliases.
	 * <p>
	 * Default is "true".
	 * 
	 * @see #registerBeanDefinition
	 */
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	/**
	 * Return whether it should be allowed to override bean definitions by
	 * registering a different definition with the same name, automatically
	 * replacing the former.
	 * 
	 * @since 4.1.2
	 */
	public boolean isAllowBeanDefinitionOverriding() {
		return this.allowBeanDefinitionOverriding;
	}

	/**
	 * Set whether the factory is allowed to eagerly load bean classes even for bean
	 * definitions that are marked as "lazy-init".
	 * <p>
	 * Default is "true". Turn this flag off to suppress class loading for lazy-init
	 * beans unless such a bean is explicitly requested. In particular, by-type
	 * lookups will then simply ignore bean definitions without resolved class name,
	 * instead of loading the bean classes on demand just to perform a type check.
	 * 
	 * @see AbstractBeanDefinition#setLazyInit
	 */
	public void setAllowEagerClassLoading(boolean allowEagerClassLoading) {
		this.allowEagerClassLoading = allowEagerClassLoading;
	}

	/**
	 * Return whether the factory is allowed to eagerly load bean classes even for
	 * bean definitions that are marked as "lazy-init".
	 * 
	 * @since 4.1.2
	 */
	public boolean isAllowEagerClassLoading() {
		return this.allowEagerClassLoading;
	}

	/**
	 * Set a {@link java.util.Comparator} for dependency Lists and arrays.
	 * 
	 * @since 4.0
	 * @see org.springframework.core.OrderComparator
	 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
	 */
	public void setDependencyComparator(@Nullable Comparator<Object> dependencyComparator) {
		this.dependencyComparator = dependencyComparator;
	}

	/**
	 * Return the dependency comparator for this BeanFactory (may be {@code null}.
	 * 
	 * @since 4.0
	 */
	@Nullable
	public Comparator<Object> getDependencyComparator() {
		return this.dependencyComparator;
	}

	/**
	 * Set a custom autowire candidate resolver for this BeanFactory to use when
	 * deciding whether a bean definition should be considered as a candidate for
	 * autowiring.
	 */
	public void setAutowireCandidateResolver(final AutowireCandidateResolver autowireCandidateResolver) {
		Assert.notNull(autowireCandidateResolver, "AutowireCandidateResolver must not be null");
		if (autowireCandidateResolver instanceof BeanFactoryAware) {
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(DefaultListableBeanFactory.this);
					return null;
				}, getAccessControlContext());
			} else {
				((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
			}
		}
		this.autowireCandidateResolver = autowireCandidateResolver;
	}

	/**
	 * Return the autowire candidate resolver for this BeanFactory (never
	 * {@code null}).
	 */
	public AutowireCandidateResolver getAutowireCandidateResolver() {
		return this.autowireCandidateResolver;
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof DefaultListableBeanFactory) {
			DefaultListableBeanFactory otherListableFactory = (DefaultListableBeanFactory) otherFactory;
			this.allowBeanDefinitionOverriding = otherListableFactory.allowBeanDefinitionOverriding;
			this.allowEagerClassLoading = otherListableFactory.allowEagerClassLoading;
			this.dependencyComparator = otherListableFactory.dependencyComparator;
			// A clone of the AutowireCandidateResolver since it is potentially
			// BeanFactoryAware...
			setAutowireCandidateResolver(BeanUtils.instantiateClass(getAutowireCandidateResolver().getClass()));
			// Make resolvable dependencies (e.g. ResourceLoader) available here as well...
			this.resolvableDependencies.putAll(otherListableFactory.resolvableDependencies);
		}
	}

	// ---------------------------------------------------------------------
	// Implementation of remaining BeanFactory methods
	// ---------------------------------------------------------------------

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		/**
		 * getBean 是一个空壳方法，所有的逻辑都封装在 doGetBean 方法中
		 */
		return getBean(requiredType, (Object[]) null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
		Object resolved = resolveBean(ResolvableType.forRawClass(requiredType), args, false);
		if (resolved == null) {
			throw new NoSuchBeanDefinitionException(requiredType);
		}
		return (T) resolved;
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) throws BeansException {
		return getBeanProvider(ResolvableType.forRawClass(requiredType));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		return new BeanObjectProvider<T>() {
			@Override
			public T getObject() throws BeansException {
				T resolved = resolveBean(requiredType, null, false);
				if (resolved == null) {
					throw new NoSuchBeanDefinitionException(requiredType);
				}
				return resolved;
			}

			@Override
			public T getObject(Object... args) throws BeansException {
				T resolved = resolveBean(requiredType, args, false);
				if (resolved == null) {
					throw new NoSuchBeanDefinitionException(requiredType);
				}
				return resolved;
			}

			@Override
			@Nullable
			public T getIfAvailable() throws BeansException {
				return resolveBean(requiredType, null, false);
			}

			@Override
			@Nullable
			public T getIfUnique() throws BeansException {
				return resolveBean(requiredType, null, true);
			}

			@Override
			public Stream<T> stream() {
				return Arrays.stream(getBeanNamesForTypedStream(requiredType)).map(name -> (T) getBean(name))
						.filter(bean -> !(bean instanceof NullBean));
			}

			@Override
			public Stream<T> orderedStream() {
				String[] beanNames = getBeanNamesForTypedStream(requiredType);
				Map<String, T> matchingBeans = new LinkedHashMap<>(beanNames.length);
				for (String beanName : beanNames) {
					Object beanInstance = getBean(beanName);
					if (!(beanInstance instanceof NullBean)) {
						matchingBeans.put(beanName, (T) beanInstance);
					}
				}
				Stream<T> stream = matchingBeans.values().stream();
				return stream.sorted(adaptOrderComparator(matchingBeans));
			}
		};
	}

	@Nullable
	private <T> T resolveBean(ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) {
		NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args, nonUniqueAsNull);
		if (namedBean != null) {
			return namedBean.getBeanInstance();
		}
		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			return ((DefaultListableBeanFactory) parent).resolveBean(requiredType, args, nonUniqueAsNull);
		} else if (parent != null) {
			ObjectProvider<T> parentProvider = parent.getBeanProvider(requiredType);
			if (args != null) {
				return parentProvider.getObject(args);
			} else {
				return (nonUniqueAsNull ? parentProvider.getIfUnique() : parentProvider.getIfAvailable());
			}
		}
		return null;
	}

	private String[] getBeanNamesForTypedStream(ResolvableType requiredType) {
		return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType);
	}

	// ---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	// ---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return this.beanDefinitionMap.containsKey(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return this.beanDefinitionMap.size();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		String[] frozenNames = this.frozenBeanDefinitionNames;
		if (frozenNames != null) {
			return frozenNames.clone();
		} else {
			return StringUtils.toStringArray(this.beanDefinitionNames);
		}
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		Class<?> resolved = type.resolve();
		if (resolved != null && !type.hasGenerics()) {
			return getBeanNamesForType(resolved, true, true);
		} else {
			return doGetBeanNamesForType(type, true, true);
		}
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		return getBeanNamesForType(type, true, true);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		if (!isConfigurationFrozen() || type == null || !allowEagerInit) {
			return doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit);
		}
		Map<Class<?>, String[]> cache = (includeNonSingletons ? this.allBeanNamesByType
				: this.singletonBeanNamesByType);
		String[] resolvedBeanNames = cache.get(type);
		if (resolvedBeanNames != null) {
			return resolvedBeanNames;
		}
		resolvedBeanNames = doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true);
		if (ClassUtils.isCacheSafe(type, getBeanClassLoader())) {
			cache.put(type, resolvedBeanNames);
		}
		return resolvedBeanNames;
	}

	// 根据类型去factory中获取对应类的名字
	// 比如在工厂初始化的时候可以根据BeanFactory去获取所有的BeanFactoryProcessor
	private String[] doGetBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		List<String> result = new ArrayList<>();

		// Check all bean definitions.
		for (String beanName : this.beanDefinitionNames) {
			// Only consider bean as eligible if the bean name
			// is not defined as alias for some other bean.
			//// 只有当bean名称没有定义为其他bean的别名时，才认为bean是合格的,注解没有别名，只有xml有定义别名的
			if (!isAlias(beanName)) {
				try {
					// 返回合并的RootBeanDefinition，如果指定的bean符合子bean定义，则遍历父bean定义。
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					// Only check bean definition if it is complete.
					if (!mbd.isAbstract() && (allowEagerInit
							|| (mbd.hasBeanClass() || !mbd.isLazyInit() || isAllowEagerClassLoading())
									&& !requiresEagerInitForType(mbd.getFactoryBeanName()))) {
						// In case of FactoryBean, match object created by FactoryBean.
						boolean isFactoryBean = isFactoryBean(beanName, mbd);
						BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
						boolean matchFound = (allowEagerInit || !isFactoryBean || (dbd != null && !mbd.isLazyInit())
								|| containsSingleton(beanName))
								&& (includeNonSingletons || (dbd != null ? mbd.isSingleton() : isSingleton(beanName)))
								&& isTypeMatch(beanName, type);
						if (!matchFound && isFactoryBean) {
							// In case of FactoryBean, try to match FactoryBean instance itself next.
							beanName = FACTORY_BEAN_PREFIX + beanName;
							matchFound = (includeNonSingletons || mbd.isSingleton()) && isTypeMatch(beanName, type);
						}
						if (matchFound) {
							result.add(beanName);
						}
					}
				} catch (CannotLoadBeanClassException ex) {
					if (allowEagerInit) {
						throw ex;
					}
					// Probably a class name with a placeholder: let's ignore it for type matching
					// purposes.
					if (logger.isDebugEnabled()) {
						logger.debug("Ignoring bean class loading failure for bean '" + beanName + "'", ex);
					}
					onSuppressedException(ex);
				} catch (BeanDefinitionStoreException ex) {
					if (allowEagerInit) {
						throw ex;
					}
					// Probably some metadata with a placeholder: let's ignore it for type matching
					// purposes.
					if (logger.isDebugEnabled()) {
						logger.debug("Ignoring unresolvable metadata in bean definition '" + beanName + "'", ex);
					}
					onSuppressedException(ex);
				}
			}
		}

		// Check manually registered singletons too.
		for (String beanName : this.manualSingletonNames) {
			try {
				// In case of FactoryBean, match object created by FactoryBean.
				if (isFactoryBean(beanName)) {
					if ((includeNonSingletons || isSingleton(beanName)) && isTypeMatch(beanName, type)) {
						result.add(beanName);
						// Match found for this bean: do not match FactoryBean itself anymore.
						continue;
					}
					// In case of FactoryBean, try to match FactoryBean itself next.
					beanName = FACTORY_BEAN_PREFIX + beanName;
				}
				// Match raw bean instance (might be raw FactoryBean).
				if (isTypeMatch(beanName, type)) {
					result.add(beanName);
				}
			} catch (NoSuchBeanDefinitionException ex) {
				// Shouldn't happen - probably a result of circular reference resolution...
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to check manually registered singleton with name '" + beanName + "'", ex);
				}
			}
		}

		return StringUtils.toStringArray(result);
	}

	/**
	 * Check whether the specified bean would need to be eagerly initialized in
	 * order to determine its type.
	 * 
	 * @param factoryBeanName a factory-bean reference that the bean definition
	 *                        defines a factory method for
	 * @return whether eager initialization is necessary
	 */
	private boolean requiresEagerInitForType(@Nullable String factoryBeanName) {
		return (factoryBeanName != null && isFactoryBean(factoryBeanName) && !containsSingleton(factoryBeanName));
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		return getBeansOfType(type, true, true);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons,
			boolean allowEagerInit) throws BeansException {

		String[] beanNames = getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
		Map<String, T> result = new LinkedHashMap<>(beanNames.length);
		for (String beanName : beanNames) {
			try {
				Object beanInstance = getBean(beanName);
				if (!(beanInstance instanceof NullBean)) {
					result.put(beanName, (T) beanInstance);
				}
			} catch (BeanCreationException ex) {
				Throwable rootCause = ex.getMostSpecificCause();
				if (rootCause instanceof BeanCurrentlyInCreationException) {
					BeanCreationException bce = (BeanCreationException) rootCause;
					String exBeanName = bce.getBeanName();
					if (exBeanName != null && isCurrentlyInCreation(exBeanName)) {
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring match to currently created bean '" + exBeanName + "': "
									+ ex.getMessage());
						}
						onSuppressedException(ex);
						// Ignore: indicates a circular reference when autowiring constructors.
						// We want to find matches other than the currently created bean itself.
						continue;
					}
				}
				throw ex;
			}
		}
		return result;
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		List<String> result = new ArrayList<>();
		for (String beanName : this.beanDefinitionNames) {
			BeanDefinition beanDefinition = getBeanDefinition(beanName);
			if (!beanDefinition.isAbstract() && findAnnotationOnBean(beanName, annotationType) != null) {
				result.add(beanName);
			}
		}
		for (String beanName : this.manualSingletonNames) {
			if (!result.contains(beanName) && findAnnotationOnBean(beanName, annotationType) != null) {
				result.add(beanName);
			}
		}
		return StringUtils.toStringArray(result);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
		String[] beanNames = getBeanNamesForAnnotation(annotationType);
		Map<String, Object> result = new LinkedHashMap<>(beanNames.length);
		for (String beanName : beanNames) {
			Object beanInstance = getBean(beanName);
			if (!(beanInstance instanceof NullBean)) {
				result.put(beanName, beanInstance);
			}
		}
		return result;
	}

	/**
	 * Find a {@link Annotation} of {@code annotationType} on the specified bean,
	 * traversing its interfaces and super classes if no annotation can be found on
	 * the given class itself, as well as checking its raw bean class if not found
	 * on the exposed bean reference (e.g. in case of a proxy).
	 */
	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		A ann = null;
		Class<?> beanType = getType(beanName);
		if (beanType != null) {
			ann = AnnotationUtils.findAnnotation(beanType, annotationType);
		}
		if (ann == null && containsBeanDefinition(beanName)) {
			BeanDefinition bd = getMergedBeanDefinition(beanName);
			if (bd instanceof AbstractBeanDefinition) {
				AbstractBeanDefinition abd = (AbstractBeanDefinition) bd;
				if (abd.hasBeanClass()) {
					ann = AnnotationUtils.findAnnotation(abd.getBeanClass(), annotationType);
				}
			}
		}
		return ann;
	}

	// ---------------------------------------------------------------------
	// Implementation of ConfigurableListableBeanFactory interface
	// ---------------------------------------------------------------------

	@Override
	public void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue) {
		Assert.notNull(dependencyType, "Dependency type must not be null");
		if (autowiredValue != null) {
			if (!(autowiredValue instanceof ObjectFactory || dependencyType.isInstance(autowiredValue))) {
				throw new IllegalArgumentException("Value [" + autowiredValue
						+ "] does not implement specified dependency type [" + dependencyType.getName() + "]");
			}
			this.resolvableDependencies.put(dependencyType, autowiredValue);
		}
	}

//	isAutowireCandidate：判断候选对象是否可用，有三重过滤规则：①bd.autowireCandidate=true -> ②泛型匹配 -> ③@Qualifier。委托给 ContextAnnotationAutowireCandidateResolver。
//
//			isAutowireCandidate 方法过滤候选对象
	@Override
	public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
			throws NoSuchBeanDefinitionException {
		// getAutowireCandidateResolver:
		// 返回BeanFactory的@Autowire解析器，开启注解后的解析器为：ContextAnnotationAutowireCandidateResolver
		// 解析beanName对应的bean是否有资格作为候选者
		return isAutowireCandidate(beanName, descriptor, getAutowireCandidateResolver());
	}

	/**
	 * Determine whether the specified bean definition qualifies as an autowire
	 * candidate, to be injected into other beans which declare a dependency of
	 * matching type. isAutowireCandidate 判断候选对象是否可用。实际是都是委托给
	 * AutowireCandidateResolver#isAutowireCandidate 接口判断，Spring 中默认的实现是
	 * ContextAnnotationAutowireCandidateResolver。
	 * 
	 * isAutowireCandidate 方法过滤候选对象有三重规则：①bd.autowireCandidate=true -> ②泛型匹配 ->
	 * ③@Qualifier。
	 * 
	 * @param beanName   the name of the bean definition to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @param resolver   the AutowireCandidateResolver to use for the actual
	 *                   resolution algorithm
	 * @return whether the bean should be considered as autowire candidate
	 */
	protected boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor,
			AutowireCandidateResolver resolver) throws NoSuchBeanDefinitionException {
		// 1.解析beanName，去掉FactoryBean的修饰符“&”
		String beanDefinitionName = BeanFactoryUtils.transformedBeanName(beanName);
		if (containsBeanDefinition(beanDefinitionName)) {
			// 2.beanDefinitionMap缓存中存在beanDefinitionName：通过beanDefinitionName缓存拿到MergedBeanDefinition，
			// 将MergedBeanDefinition作为参数，解析beanName是否有资格作为候选者
			return isAutowireCandidate(beanName, getMergedLocalBeanDefinition(beanDefinitionName), descriptor,
					resolver);
		} else if (containsSingleton(beanName)) {
			// 3.singletonObjects缓存中存在beanName：使用beanName构建RootBeanDefinition作为参数，解析beanName是否有资格作为候选者
			return isAutowireCandidate(beanName, new RootBeanDefinition(getType(beanName)), descriptor, resolver);
		}
		// 4.在beanDefinitionMap缓存和singletonObjects缓存中都不存在，则在parentBeanFactory中递归解析beanName是否有资格作为候选者
		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			return ((DefaultListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor, resolver);
		} else if (parent instanceof ConfigurableListableBeanFactory) {
			// If no DefaultListableBeanFactory, can't pass the resolver along.
			return ((ConfigurableListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor);
		} else {
			return true;
		}
	}

	/**
	 * Determine whether the specified bean definition qualifies as an autowire
	 * candidate, to be injected into other beans which declare a dependency of
	 * matching type.
	 * 
	 * @param beanName   the name of the bean definition to check
	 * @param mbd        the merged bean definition to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @param resolver   the AutowireCandidateResolver to use for the actual
	 *                   resolution algorithm
	 * @return whether the bean should be considered as autowire candidate
	 */
	protected boolean isAutowireCandidate(String beanName, RootBeanDefinition mbd, DependencyDescriptor descriptor,
			AutowireCandidateResolver resolver) {

		String beanDefinitionName = BeanFactoryUtils.transformedBeanName(beanName);
		resolveBeanClass(mbd, beanDefinitionName);
		if (mbd.isFactoryMethodUnique && mbd.factoryMethodToIntrospect == null) {
			new ConstructorResolver(this).resolveFactoryMethodIfPossible(mbd);
		}
		return resolver.isAutowireCandidate(new BeanDefinitionHolder(mbd, beanName, getAliases(beanDefinitionName)),
				descriptor);
	}

	@Override
	public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		BeanDefinition bd = this.beanDefinitionMap.get(beanName);
		if (bd == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("No bean named '" + beanName + "' found in " + this);
			}
			throw new NoSuchBeanDefinitionException(beanName);
		}
		return bd;
	}

	@Override
	public Iterator<String> getBeanNamesIterator() {
		CompositeIterator<String> iterator = new CompositeIterator<>();
		iterator.add(this.beanDefinitionNames.iterator());
		iterator.add(this.manualSingletonNames.iterator());
		return iterator;
	}

	@Override
	public void clearMetadataCache() {
		super.clearMetadataCache();
		clearByTypeCache();
	}

	@Override
	public void freezeConfiguration() {
		this.configurationFrozen = true;
		this.frozenBeanDefinitionNames = StringUtils.toStringArray(this.beanDefinitionNames);
	}

	@Override
	public boolean isConfigurationFrozen() {
		return this.configurationFrozen;
	}

	/**
	 * Considers all beans as eligible for metadata caching if the factory's
	 * configuration has been marked as frozen.
	 * 
	 * @see #freezeConfiguration()
	 */
	@Override
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return (this.configurationFrozen || super.isBeanEligibleForMetadataCaching(beanName));
	}

	@Override
	public void preInstantiateSingletons() throws BeansException {
		if (logger.isTraceEnabled()) {
			logger.trace("Pre-instantiating singletons in " + this);
		}

		// Iterate over a copy to allow for init methods which in turn register new bean
		// definitions.
		// While this may not be part of the regular factory bootstrap, it does
		// otherwise work fine.
		List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

		// Trigger initialization of all non-lazy singleton beans...
		for (String beanName : beanNames) {
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
				if (isFactoryBean(beanName)) {
					// 如果是FactoryBean则加上&
					Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
					if (bean instanceof FactoryBean) {
						final FactoryBean<?> factory = (FactoryBean<?>) bean;
						boolean isEagerInit;
						if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
							isEagerInit = AccessController.doPrivileged(
									(PrivilegedAction<Boolean>) ((SmartFactoryBean<?>) factory)::isEagerInit,
									getAccessControlContext());
						} else {
							isEagerInit = (factory instanceof SmartFactoryBean
									&& ((SmartFactoryBean<?>) factory).isEagerInit());
						}
						if (isEagerInit) {
							getBean(beanName);
						}
					}
				} else {
					getBean(beanName);
				}
			}
		}

		// Trigger post-initialization callback for all applicable beans...
		for (String beanName : beanNames) {
			Object singletonInstance = getSingleton(beanName);
			if (singletonInstance instanceof SmartInitializingSingleton) {
				final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
				if (System.getSecurityManager() != null) {
					AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
						smartSingleton.afterSingletonsInstantiated();
						return null;
					}, getAccessControlContext());
				} else {
					smartSingleton.afterSingletonsInstantiated();
				}
			}
		}
	}

	// ---------------------------------------------------------------------
	// Implementation of BeanDefinitionRegistry interface
	// ---------------------------------------------------------------------

	@Override
	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException {

		Assert.hasText(beanName, "Bean name must not be empty");
		Assert.notNull(beanDefinition, "BeanDefinition must not be null");

		if (beanDefinition instanceof AbstractBeanDefinition) {
			try {
				((AbstractBeanDefinition) beanDefinition).validate();
			} catch (BeanDefinitionValidationException ex) {
				throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName,
						"Validation of bean definition failed", ex);
			}
		}

		BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
		if (existingDefinition != null) {
			if (!isAllowBeanDefinitionOverriding()) {
				throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
			} else if (existingDefinition.getRole() < beanDefinition.getRole()) {
				// e.g. was ROLE_APPLICATION, now overriding with ROLE_SUPPORT or
				// ROLE_INFRASTRUCTURE
				if (logger.isInfoEnabled()) {
					logger.info("Overriding user-defined bean definition for bean '" + beanName
							+ "' with a framework-generated bean definition: replacing [" + existingDefinition
							+ "] with [" + beanDefinition + "]");
				}
			} else if (!beanDefinition.equals(existingDefinition)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Overriding bean definition for bean '" + beanName
							+ "' with a different definition: replacing [" + existingDefinition + "] with ["
							+ beanDefinition + "]");
				}
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Overriding bean definition for bean '" + beanName
							+ "' with an equivalent definition: replacing [" + existingDefinition + "] with ["
							+ beanDefinition + "]");
				}
			}
			this.beanDefinitionMap.put(beanName, beanDefinition);
		} else {
			if (hasBeanCreationStarted()) {
				// Cannot modify startup-time collection elements anymore (for stable iteration)
				synchronized (this.beanDefinitionMap) {
					this.beanDefinitionMap.put(beanName, beanDefinition);
					List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
					updatedDefinitions.addAll(this.beanDefinitionNames);
					updatedDefinitions.add(beanName);
					this.beanDefinitionNames = updatedDefinitions;
					if (this.manualSingletonNames.contains(beanName)) {
						Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
						updatedSingletons.remove(beanName);
						this.manualSingletonNames = updatedSingletons;
					}
				}
			} else {
				// Still in startup registration phase
				this.beanDefinitionMap.put(beanName, beanDefinition);
				this.beanDefinitionNames.add(beanName);
				this.manualSingletonNames.remove(beanName);
			}
			this.frozenBeanDefinitionNames = null;
		}

		if (existingDefinition != null || containsSingleton(beanName)) {
			resetBeanDefinition(beanName);
		}
	}

	@Override
	public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
		Assert.hasText(beanName, "'beanName' must not be empty");

		BeanDefinition bd = this.beanDefinitionMap.remove(beanName);
		if (bd == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("No bean named '" + beanName + "' found in " + this);
			}
			throw new NoSuchBeanDefinitionException(beanName);
		}

		if (hasBeanCreationStarted()) {
			// Cannot modify startup-time collection elements anymore (for stable iteration)
			synchronized (this.beanDefinitionMap) {
				List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames);
				updatedDefinitions.remove(beanName);
				this.beanDefinitionNames = updatedDefinitions;
			}
		} else {
			// Still in startup registration phase
			this.beanDefinitionNames.remove(beanName);
		}
		this.frozenBeanDefinitionNames = null;

		resetBeanDefinition(beanName);
	}

	/**
	 * Reset all bean definition caches for the given bean, including the caches of
	 * beans that are derived from it.
	 * <p>
	 * Called after an existing bean definition has been replaced or removed,
	 * triggering {@link #clearMergedBeanDefinition}, {@link #destroySingleton} and
	 * {@link MergedBeanDefinitionPostProcessor#resetBeanDefinition} on the given
	 * bean and on all bean definitions that have the given bean as parent.
	 * 
	 * @param beanName the name of the bean to reset
	 * @see #registerBeanDefinition
	 * @see #removeBeanDefinition
	 */
	protected void resetBeanDefinition(String beanName) {
		// Remove the merged bean definition for the given bean, if already created.
		clearMergedBeanDefinition(beanName);

		// Remove corresponding bean from singleton cache, if any. Shouldn't usually
		// be necessary, rather just meant for overriding a context's default beans
		// (e.g. the default StaticMessageSource in a StaticApplicationContext).
		destroySingleton(beanName);

		// Notify all post-processors that the specified bean definition has been reset.
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			if (processor instanceof MergedBeanDefinitionPostProcessor) {
				((MergedBeanDefinitionPostProcessor) processor).resetBeanDefinition(beanName);
			}
		}

		// Reset all bean definitions that have the given bean as parent (recursively).
		for (String bdName : this.beanDefinitionNames) {
			if (!beanName.equals(bdName)) {
				BeanDefinition bd = this.beanDefinitionMap.get(bdName);
				if (beanName.equals(bd.getParentName())) {
					resetBeanDefinition(bdName);
				}
			}
		}
	}

	/**
	 * Only allows alias overriding if bean definition overriding is allowed.
	 */
	@Override
	protected boolean allowAliasOverriding() {
		return isAllowBeanDefinitionOverriding();
	}

	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		super.registerSingleton(beanName, singletonObject);

		if (hasBeanCreationStarted()) {
			// Cannot modify startup-time collection elements anymore (for stable iteration)
			synchronized (this.beanDefinitionMap) {
				if (!this.beanDefinitionMap.containsKey(beanName)) {
					Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames.size() + 1);
					updatedSingletons.addAll(this.manualSingletonNames);
					updatedSingletons.add(beanName);
					this.manualSingletonNames = updatedSingletons;
				}
			}
		} else {
			// Still in startup registration phase
			if (!this.beanDefinitionMap.containsKey(beanName)) {
				this.manualSingletonNames.add(beanName);
			}
		}

		clearByTypeCache();
	}

	@Override
	public void destroySingleton(String beanName) {
		super.destroySingleton(beanName);
		this.manualSingletonNames.remove(beanName);
		clearByTypeCache();
	}

	@Override
	public void destroySingletons() {
		super.destroySingletons();
		this.manualSingletonNames.clear();
		clearByTypeCache();
	}

	/**
	 * Remove any assumptions about by-type mappings.
	 */
	private void clearByTypeCache() {
		this.allBeanNamesByType.clear();
		this.singletonBeanNamesByType.clear();
	}

	// ---------------------------------------------------------------------
	// Dependency resolution functionality
	// ---------------------------------------------------------------------

	@Override
	public <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException {
		NamedBeanHolder<T> namedBean = resolveNamedBean(ResolvableType.forRawClass(requiredType), null, false);
		if (namedBean != null) {
			return namedBean;
		}
		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof AutowireCapableBeanFactory) {
			return ((AutowireCapableBeanFactory) parent).resolveNamedBean(requiredType);
		}
		throw new NoSuchBeanDefinitionException(requiredType);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private <T> NamedBeanHolder<T> resolveNamedBean(ResolvableType requiredType, @Nullable Object[] args,
			boolean nonUniqueAsNull) throws BeansException {

		Assert.notNull(requiredType, "Required type must not be null");
		String[] candidateNames = getBeanNamesForType(requiredType);
		/**
		 * 得到对象的名字，因为对象可能有别名故而需要处理别名
		 */
		// 如果bean名称不止一个（比如你定义了多个同类型但是名称不一样的Bean）
		if (candidateNames.length > 1) {
			List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
			for (String beanName : candidateNames) {
				// 判断：如果beanDefinitionMap不包含key为beanName的键值对或者该bean可以被自动装配到其他bean中。则添加到候选列表中
				if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
					autowireCandidates.add(beanName);
				}
			}
			if (!autowireCandidates.isEmpty()) {
				candidateNames = StringUtils.toStringArray(autowireCandidates);
			}
		}

		if (candidateNames.length == 1) {
			String beanName = candidateNames[0];
			return new NamedBeanHolder<>(beanName, (T) getBean(beanName, requiredType.toClass(), args));
		} else if (candidateNames.length > 1) {
			Map<String, Object> candidates = new LinkedHashMap<>(candidateNames.length);
			for (String beanName : candidateNames) {
				if (containsSingleton(beanName) && args == null) {
					Object beanInstance = getBean(beanName);
					candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
				} else {
					candidates.put(beanName, getType(beanName));
				}
			}
			// 因为这个方法只能返回一个实例，而这种情况我们获取了多个，到底返回哪一个？
			// 这一步：确定给定bean集合中的主要候选对象。---③
			String candidateName = determinePrimaryCandidate(candidates, requiredType.toClass());
			if (candidateName == null) {
				// 这一步：确定给定bean集合中具有最高优先级的对象。---④
				candidateName = determineHighestPriorityCandidate(candidates, requiredType.toClass());
			}
			if (candidateName != null) {
				Object beanInstance = candidates.get(candidateName);
				if (beanInstance == null || beanInstance instanceof Class) {
					beanInstance = getBean(candidateName, requiredType.toClass(), args);
				}
				return new NamedBeanHolder<>(candidateName, (T) beanInstance);
			}
			if (!nonUniqueAsNull) {
				throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
			}
		}

		return null;
	}

//	resolveDependency 依赖查找解决了以下场景：
//
//	Optional：JDK8 提供了 API。主要是将依赖设置非强制依赖，即 descriptor.required=false。
//	延迟依赖注入支持：ObjectFactory、ObjectProvider、javax.inject.Provider 没有本质的区别。
//	另一种延迟注入的支持 - @Lazy 属性。
//	根据类型查找依赖 - doResolveDependency。
	// 根据上面的分析，resolveDependency 方法对 Optional、延迟注入、懒加载注入等分别进行了处理。
	// 之后 doResolveDependency 在正式查找之前看能不能快速查找，如缓存 beanName、@Value
	// 等快速指定需要注入的值，避免通过类型查找，最后才对集合依赖和单一依赖分别进行了处理。
	// 实际上，无论是集合依赖还是单一依赖查找，本质上都是调用 findAutowireCandidates 进行类型依赖查找。
	// 从 findAutowireCandidates 方法，我们可以看到 Spring IoC 依赖注入的来源：
	//
	// 先查找 Spring IoC 内部依赖 resolvableDependencies。
	// 在 AbstractApplicationContext#prepareBeanFactory
	// 方法中默认设置了如下内部依赖：BeanFactory、ResourceLoader、ApplicationEventPublisher、ApplicationContext。
	// 在父子容器进行类型查找：查找类型匹配的 beanNames，beanFactory#beanNamesForType 方法根据类型查找是，
	// 先匹配单例实例类型（包括 Spring 托管 Bean），再匹配 BeanDefinition 的类型。
	// 从这一步，我们可以看到 Spring 依赖注入的另外两个来源：一是 Spring 托管的外部 Bean，二是 Spring BeanDefinition。
	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

		descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
		if (Optional.class == descriptor.getDependencyType()) {
			return createOptionalDependency(descriptor, requestingBeanName);
		} else if (ObjectFactory.class == descriptor.getDependencyType()
				|| ObjectProvider.class == descriptor.getDependencyType()) {
			return new DependencyObjectProvider(descriptor, requestingBeanName);
		} else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
			return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
		} else {
			Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(descriptor,
					requestingBeanName);
			if (result == null) {
				result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
			}
			return result;
		}
	}
	// doResolveDependency 封装了依赖查找的各种情况：
	// 快速查找： @Autowired 注解处理场景。AutowiredAnnotationBeanPostProcessor 处理 @Autowired
	// 注解时，
	// 如果注入的对象只有一个，会将该 bean 对应的名称缓存起来，下次直接通过名称查找会快很多。
	// 注入指定值：@Value 注解处理场景。QualifierAnnotationAutowireCandidateResolver 处理 @Value
	// 注解时，
	// 会读取 @Value 对应的值进行注入。如果是 String 要经过三个过程：①占位符处理 -> ②EL 表达式解析 ->
	// ③类型转换，这也是一般的处理过程，BeanDefinitionValueResolver 处理 String 对象也是这个过程。
	// 集合依赖查询：直接全部委托给 resolveMultipleBeans 方法。
	// 单个依赖查询：先调用 findAutowireCandidates 查找所有可用的依赖，如果有多个依赖，则根据规则匹配： @Primary ->
	// @Priority -> ③方法名称或字段名称。

	@Nullable
	public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {
		// 1.设置当前的descriptor(存储了方法参数等信息)为当前注入点
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			// 1. 快速查找，根据名称查找。AutowiredAnnotationBeanPostProcessor用到
			// 2.如果是ShortcutDependencyDescriptor，则直接通过getBean方法获取Bean实例，并返回；否则返回null
			Object shortcut = descriptor.resolveShortcut(this);
			if (shortcut != null) {
				return shortcut;
			}
			// 2. 注入指定值，QualifierAnnotationAutowireCandidateResolver解析@Value会用到
			// 3.拿到descriptor包装的方法的参数类型（通过参数索引定位到具体的参数）
			Class<?> type = descriptor.getDependencyType();
			// 4.用于支持spring中新增的注解@Value（确定给定的依赖项是否声明Value注解，如果有则拿到值）
			Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
			if (value != null) {
				if (value instanceof String) {
					String strVal = resolveEmbeddedValue((String) value);
					BeanDefinition bd = (beanName != null && containsBean(beanName) ? getMergedBeanDefinition(beanName)
							: null);
					value = evaluateBeanDefinitionString(strVal, bd);
				}
				// 2.2 Spring EL 表达式
				TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
				return (descriptor.getField() != null ? converter.convertIfNecessary(value, type, descriptor.getField())
						: converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
			}
			// 2.3 类型转换
			// // 5.解析MultipleBean（下文的MultipleBean都是指类型为：Array、Collection、Map）
			Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
			if (multipleBeans != null) {
				return multipleBeans;
			}
			// 3. 内部查找依赖也是使用findAutowireCandidates
			// 6.查找与所需类型匹配的Bean实例（matchingBeans，key：beanName；value：匹配的bean实例，或者匹配的bean实例的类型）
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (matchingBeans.isEmpty()) {
				// 6.1 如果require属性为true，而找到的匹配Bean却为空则抛出异常
				if (isRequired(descriptor)) {
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				// 6.2 如果require属性为false，而找到的匹配Bean却为空，则返回nul
				return null;
			}

			String autowiredBeanName;
			Object instanceCandidate;

			if (matchingBeans.size() > 1) {
				// 7.非MultipleBean，但是有多个候选者
				// 7.1 从多个候选者中选出最优的那个
				autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
				if (autowiredBeanName == null) {
					if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
						return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
					} else {
						// In case of an optional Collection/Map, silently ignore a non-unique case:
						// possibly it was meant to be an empty collection of multiple regular beans
						// (before 4.3 in particular when we didn't even look for collection beans).
						return null;
					}
				}
				// 7.2.拿到autowiredBeanName对应的value（bean实例或bean实例类型）
				instanceCandidate = matchingBeans.get(autowiredBeanName);
			} else {
				// We have exactly one match.
				// 8.只找到了一个候选者，则直接使用该候选者
				Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
				autowiredBeanName = entry.getKey();
				instanceCandidate = entry.getValue();
			}
			// 9.将依赖的beanName加到autowiredBeanNames中
			if (autowiredBeanNames != null) {
				autowiredBeanNames.add(autowiredBeanName);
			}
			// 10.如果instanceCandidate为Class，则instanceCandidate为bean实例的类型，执行descriptor.resolveCandidate方法，
			// 通过getBean方法获取bean实例并返回；如果instanceCandidate不是Class，则instanceCandidate为bean实例，直接返回该实例
			if (instanceCandidate instanceof Class) {
				instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
			}
			Object result = instanceCandidate;
			if (result instanceof NullBean) {
				if (isRequired(descriptor)) {
					raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
				}
				result = null;
			}
			if (!ClassUtils.isAssignableValue(type, result)) {
				throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
			}
			return result;
		} finally {
			// 11.执行结束，将注入点修改成原来的注入点
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Nullable
	private Object resolveMultipleBeans(DependencyDescriptor descriptor, @Nullable String beanName,
			@Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) {

		final Class<?> type = descriptor.getDependencyType();

		if (descriptor instanceof StreamDependencyDescriptor) {
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			Stream<Object> stream = matchingBeans.keySet().stream()
					.map(name -> descriptor.resolveCandidate(name, type, this))
					.filter(bean -> !(bean instanceof NullBean));
			if (((StreamDependencyDescriptor) descriptor).isOrdered()) {
				stream = stream.sorted(adaptOrderComparator(matchingBeans));
			}
			return stream;
		} else if (type.isArray()) {
			Class<?> componentType = type.getComponentType();
			ResolvableType resolvableType = descriptor.getResolvableType();
			Class<?> resolvedArrayType = resolvableType.resolve(type);
			if (resolvedArrayType != type) {
				componentType = resolvableType.getComponentType().resolve();
			}
			if (componentType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
			if (result instanceof Object[]) {
				Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
				if (comparator != null) {
					Arrays.sort((Object[]) result, comparator);
				}
			}
			return result;
		} else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
			Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric();
			if (elementType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
			Object result = converter.convertIfNecessary(matchingBeans.values(), type);
			if (result instanceof List) {
				Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
				if (comparator != null) {
					((List<?>) result).sort(comparator);
				}
			}
			return result;
		} else if (Map.class == type) {
			ResolvableType mapType = descriptor.getResolvableType().asMap();
			Class<?> keyType = mapType.resolveGeneric(0);
			if (String.class != keyType) {
				return null;
			}
			Class<?> valueType = mapType.resolveGeneric(1);
			if (valueType == null) {
				return null;
			}
			Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType,
					new MultiElementDescriptor(descriptor));
			if (matchingBeans.isEmpty()) {
				return null;
			}
			if (autowiredBeanNames != null) {
				autowiredBeanNames.addAll(matchingBeans.keySet());
			}
			return matchingBeans;
		} else {
			return null;
		}
	}

	private boolean isRequired(DependencyDescriptor descriptor) {
		return getAutowireCandidateResolver().isRequired(descriptor);
	}

	private boolean indicatesMultipleBeans(Class<?> type) {
		return (type.isArray() || (type.isInterface()
				&& (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))));
	}

	@Nullable
	private Comparator<Object> adaptDependencyComparator(Map<String, ?> matchingBeans) {
		Comparator<Object> comparator = getDependencyComparator();
		if (comparator instanceof OrderComparator) {
			return ((OrderComparator) comparator)
					.withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
		} else {
			return comparator;
		}
	}

	private Comparator<Object> adaptOrderComparator(Map<String, ?> matchingBeans) {
		Comparator<Object> dependencyComparator = getDependencyComparator();
		OrderComparator comparator = (dependencyComparator instanceof OrderComparator
				? (OrderComparator) dependencyComparator
				: OrderComparator.INSTANCE);
		return comparator.withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
	}

	private OrderComparator.OrderSourceProvider createFactoryAwareOrderSourceProvider(Map<String, ?> beans) {
		IdentityHashMap<Object, String> instancesToBeanNames = new IdentityHashMap<>();
		beans.forEach((beanName, instance) -> instancesToBeanNames.put(instance, beanName));
		return new FactoryAwareOrderSourceProvider(instancesToBeanNames);
	}

	/**
	 * Find bean instances that match the required type. Called during autowiring
	 * for the specified bean.
	 * 
	 * @param beanName     the name of the bean that is about to be wired
	 * @param requiredType the actual type of bean to look for (may be an array
	 *                     component type or collection element type)
	 * @param descriptor   the descriptor of the dependency to resolve
	 * @return a Map of candidate names and candidate instances that match the
	 *         required type (never {@code null})
	 * @throws BeansException in case of errors
	 * @see #autowireByType
	 * @see #autowireConstructor
	 */
//	说明： findAutowireCandidates 大致可以分为三步：先查找内部依赖，再根据类型查找，最后没有可注入的依赖则进行补偿。
//
//	查找内部依赖：Spring IoC 容器本身相关依赖，这部分内容是用户而言是透明的，也不用感知。resolvableDependencies 集合中注册如 BeanFactory、ApplicationContext 、ResourceLoader、ApplicationEventPublisher 等。
//	根据类型查找：包括 ①外部托管 Bean ②注册 BeanDefinition。类型查找调用 beanFactory#beanNamesForType 方法，详见 Spring IoC 依赖查找之类型自省。我们来看一下如何过滤的。
//	自身引用：isSelfReference 方法判断 beanName 和 candidate 是否是同一个对象，包括两种情况：一是名称完全相同，二是 candidate 对应的工厂对象创建了 beanName。
//	是否可以注入：底层实际调用 resolver.isAutowireCandidate 方法进行过滤，包含三重规则：①bd.autowireCandidate=true -> ②泛型匹配 -> ③@Qualifier。下面会详细介绍这个方法。
//	补偿机制：如果依赖查找无法匹配，怎么办？Spring 提供了两种补偿机制：一是泛型补偿，允许注入对象对象的泛型无法解析，二是自身引用补偿，对这两种机制使用如下：
//	先使用泛型补偿，不允许自身引用：即 fallbackDescriptor。此时如果是集合依赖，对象必须是 @Qualifier 类型。
//	允许泛型补偿和自身引用补偿：但如果是集合依赖，必须过滤自己本身，即 beanName.equals(candidate) 必须剔除。
	protected Map<String, Object> findAutowireCandidates(@Nullable String beanName, Class<?> requiredType,
			DependencyDescriptor descriptor) {
		// 1.获取给定类型的所有beanName，包括在祖先工厂中定义的beanName
		String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, true,
				descriptor.isEager());
		Map<String, Object> result = new LinkedHashMap<>(candidateNames.length);
		// 2.首先从已经解析的依赖关系缓存中寻找是否存在我们想要的类型
		for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
			Class<?> autowiringType = classObjectEntry.getKey();
			// 2.1 autowiringType是否与requiredType相同，或者是requiredType的超类、超接口
			if (autowiringType.isAssignableFrom(requiredType)) {
				// 2.2 如果requiredType匹配，则从缓存中拿到相应的自动装配值（bean实例）
				Object autowiringValue = classObjectEntry.getValue();
				// 2.3 根据给定的所需类型解析给定的自动装配值
				autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
				if (requiredType.isInstance(autowiringValue)) {
					// 2.4 将autowiringValue放到结果集中，此时的value为bean实例
					result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
					break;
				}
			}
		}
		// 2. 类型查找：本质上递归调用beanFactory#beanNamesForType。先匹配实例类型，再匹配bd。
		// 3.遍历从容器中获取到的类型符合的beanName
		for (String candidate : candidateNames) {
			// 2.1 isSelfReference说明beanName和candidate本质是同一个对象
			// isAutowireCandidate进一步匹配bd.autowireCandidate、泛型、@@Qualifier等进行过滤
			// isAutowireCandidate：判断是否有资格作为依赖注入的候选者
			// 3.1 如果不是自引用 && candidate有资格作为依赖注入的候选者
			if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
				addCandidateEntry(result, candidate, descriptor, requiredType);
			}
		}
		// 3. 补偿机制：如果依赖查找无法匹配，怎么办？包含泛型补偿和自身引用补偿两种。
		if (result.isEmpty()) {

			boolean multiple = indicatesMultipleBeans(requiredType);
			// Consider fallback matches if the first pass failed to find anything...
			// 3.1 fallbackDescriptor: 泛型补偿，实际上是允许注入对象类型的泛型存在无法解析的情况
			DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();
			// 3.2 补偿1：不允许自称依赖，但如果是集合依赖，需要过滤非@Qualifier对象。什么场景？
			for (String candidate : candidateNames) {
				if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor)
						&& (!multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
					addCandidateEntry(result, candidate, descriptor, requiredType);
				}
			}
			if (result.isEmpty() && !multiple) {
				// Consider self references as a final pass...
				// but in the case of a dependency collection, not the very same bean itself.
				// 3.3 补偿2：允许自称依赖，但如果是集合依赖，注入的集合依赖中需要过滤自己
				for (String candidate : candidateNames) {
					if (isSelfReference(beanName, candidate)
							&& (!(descriptor instanceof MultiElementDescriptor) || !beanName.equals(candidate))
							&& isAutowireCandidate(candidate, fallbackDescriptor)) {
						addCandidateEntry(result, candidate, descriptor, requiredType);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Add an entry to the candidate map: a bean instance if available or just the
	 * resolved type, preventing early bean initialization ahead of primary
	 * candidate selection.
	 */
	private void addCandidateEntry(Map<String, Object> candidates, String candidateName,
			DependencyDescriptor descriptor, Class<?> requiredType) {
		// 1. 集合依赖，直接调用 getName(candidateName) 实例化
		// 1.如果descriptor为MultiElementDescriptor类型 ||
		// candidateName已经在singletonObjects缓存中存在
		if (descriptor instanceof MultiElementDescriptor) {
			Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
			// 2.1 resolveCandidate: 通过candidateName获取对应的bean实例
			// 2.2 将beanName -> bean实例 的映射添加到candidates（此时的value为bean实例）
			if (!(beanInstance instanceof NullBean)) {
				candidates.put(candidateName, beanInstance);
			}
		} else if (containsSingleton(candidateName) || (descriptor instanceof StreamDependencyDescriptor
				&& ((StreamDependencyDescriptor) descriptor).isOrdered())) {
			Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
			candidates.put(candidateName, (beanInstance instanceof NullBean ? null : beanInstance));
		} else {
			// 3.将beanName -> bean实例的类型 的映射添加到candidates（此时的value为bean实例的类型）
			candidates.put(candidateName, getType(candidateName));
		}
	}

	/**
	 * Determine the autowire candidate in the given set of beans.
	 * <p>
	 * Looks for {@code @Primary} and {@code @Priority} (in that order).
	 * 
	 * @param candidates a Map of candidate names and candidate instances that match
	 *                   the required type, as returned by
	 *                   {@link #findAutowireCandidates}
	 * @param descriptor the target dependency to match against
	 * @return the name of the autowire candidate, or {@code null} if none found
	 */
	@Nullable
	protected String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
		Class<?> requiredType = descriptor.getDependencyType();
		// 1.根据@Primary注解来选择最优解
		String primaryCandidate = determinePrimaryCandidate(candidates, requiredType);
		if (primaryCandidate != null) {
			return primaryCandidate;
		}
		// 2.根据@Priority注解来选择最优解
		String priorityCandidate = determineHighestPriorityCandidate(candidates, requiredType);
		if (priorityCandidate != null) {
			return priorityCandidate;
		}
		// Fallback
		// Fallback
		// 3.如果通过以上两步都不能选择出最优解，则使用最基本的策略
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateName = entry.getKey();
			Object beanInstance = entry.getValue();
			// 3.1 containsValue：首先如果这个beanInstance已经由Spring注册过依赖关系，则直接使用该beanInstance作为最优解，
			// 3.2 matchesBeanName：如果没有注册过此beanInstance的依赖关系，则根据参数名称来匹配，
			// 如果参数名称和某个候选者的beanName或别名一致，那么直接将此bean作为最优解
			if ((beanInstance != null && this.resolvableDependencies.containsValue(beanInstance))
					|| matchesBeanName(candidateName, descriptor.getDependencyName())) {
				return candidateName;
			}
		}
		return null;
	}

	/**
	 * Determine the primary candidate in the given set of beans.
	 * 
	 * @param candidates   a Map of candidate names and candidate instances (or
	 *                     candidate classes if not created yet) that match the
	 *                     required type
	 * @param requiredType the target dependency type to match against
	 * @return the name of the primary candidate, or {@code null} if none found
	 * @see #isPrimary(String, Object)
	 */
	@Nullable
	protected String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		String primaryBeanName = null;
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();
			// 如果是主要候选对象。这里返回bean定义里的primary对象的值，具体来说就是如果你定义bean的时候用@Primary注解标注的，则是true
			// 2.判断候选者bean是否使用了@Primary注解：如果candidateBeanName在当前BeanFactory中存在BeanDefinition，
			// 则判断当前BeanFactory中的BeanDefinition是否使用@Primary修饰；否则，在parentBeanFactory中判断
			if (isPrimary(candidateBeanName, beanInstance)) {
				// 因为是循环所有的候选名称，第一次primaryBeanName肯定是null，所以在else语句给它赋值。如果出现了多个Primary注解的bean，那就会抛出异常
				if (primaryBeanName != null) {
					// 3.走到这边primaryBeanName不为null，代表标识了@Primary的候选者不止一个，则判断BeanName是否存在于当前BeanFactory
					// candidateLocal：candidateBeanName是否在当前BeanFactory的beanDefinitionMap缓存中
					boolean candidateLocal = containsBeanDefinition(candidateBeanName);
					boolean primaryLocal = containsBeanDefinition(primaryBeanName);
					if (candidateLocal && primaryLocal) {
						// 3.1 如果当前BeanFactory中同一个类型的多个Bean，不止一个Bean使用@Primary注解，则抛出异常
						throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
								"more than one 'primary' bean found among candidates: " + candidates.keySet());
					} else if (candidateLocal) {
						// 3.2
						// candidateLocal为true，primaryLocal为false，则代表primaryBeanName是parentBeanFactory中的Bean，
						// candidateBeanName是当前BeanFactory中的Bean，当存在两个都使用@Primary注解的Bean，优先使用当前BeanFactory中的
						primaryBeanName = candidateBeanName;
						// 3.3
						// candidateLocal为false，primaryLocal为true，则代表primaryBeanName是当前BeanFactory中的Bean，
						// candidateBeanName是parentBeanFactory中的Bean，因此无需修改primaryBeanName的值
					}
				} else {
					// 4.primaryBeanName还为空，代表是第一个符合的候选者，直接将primaryBeanName赋值为candidateBeanName
					primaryBeanName = candidateBeanName;
				}
			}
		}
		// 5.返回唯一的使用@Primary注解的Bean的beanName（如果都没使用@Primary注解则返回null）
		return primaryBeanName;
	}

	/**
	 * Determine the candidate with the highest priority in the given set of beans.
	 * <p>
	 * Based on {@code @javax.annotation.Priority}. As defined by the related
	 * {@link org.springframework.core.Ordered} interface, the lowest value has the
	 * highest priority.
	 * 
	 * @param candidates   a Map of candidate names and candidate instances (or
	 *                     candidate classes if not created yet) that match the
	 *                     required type
	 * @param requiredType the target dependency type to match against
	 * @return the name of the candidate with the highest priority, or {@code null}
	 *         if none found
	 * @see #getPriority(Object)
	 */
	@Nullable
	protected String determineHighestPriorityCandidate(Map<String, Object> candidates, Class<?> requiredType) {
		// 用来保存最高优先级的beanName
		String highestPriorityBeanName = null;
// 用来保存最高优先级的优先级值
		Integer highestPriority = null;
		// 1.遍历所有候选者
		for (Map.Entry<String, Object> entry : candidates.entrySet()) {
			String candidateBeanName = entry.getKey();
			Object beanInstance = entry.getValue();
			if (beanInstance != null) {
				// 获取优先级，通过Primary的例子，这个也可以猜出是标记了@Priority注解，数字越小优先级越高。
				// @Priority注解需要单独引入依赖：

				// <dependency>
				// <groupId>javax.annotation</groupId>
				// <artifactId>javax.annotation-api</artifactId>
				// <version>1.3.2</version>
				// </dependency>
				// 2.拿到beanInstance的优先级
				Integer candidatePriority = getPriority(beanInstance);
				if (candidatePriority != null) {
					if (highestPriorityBeanName != null) {
						// 3.如果之前已经有候选者有优先级，则进行选择
						if (candidatePriority.equals(highestPriority)) {
							throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
									"Multiple beans found with the same priority ('" + highestPriority
											+ "') among candidates: " + candidates.keySet());
						} else if (candidatePriority < highestPriority) {
							highestPriorityBeanName = candidateBeanName;
							highestPriority = candidatePriority;
						}
					} else {
						highestPriorityBeanName = candidateBeanName;
						highestPriority = candidatePriority;
					}
				}
			}
		}
		return highestPriorityBeanName;
	}

	/**
	 * Return whether the bean definition for the given bean name has been marked as
	 * a primary bean.
	 * 
	 * @param beanName     the name of the bean
	 * @param beanInstance the corresponding bean instance (can be null)
	 * @return whether the given bean qualifies as primary
	 */
	protected boolean isPrimary(String beanName, Object beanInstance) {
		if (containsBeanDefinition(beanName)) {
			return getMergedLocalBeanDefinition(beanName).isPrimary();
		}
		BeanFactory parent = getParentBeanFactory();
		return (parent instanceof DefaultListableBeanFactory
				&& ((DefaultListableBeanFactory) parent).isPrimary(beanName, beanInstance));
	}

	/**
	 * Return the priority assigned for the given bean instance by the
	 * {@code javax.annotation.Priority} annotation.
	 * <p>
	 * The default implementation delegates to the specified
	 * {@link #setDependencyComparator dependency comparator}, checking its
	 * {@link OrderComparator#getPriority method} if it is an extension of Spring's
	 * common {@link OrderComparator} - typically, an
	 * {@link org.springframework.core.annotation.AnnotationAwareOrderComparator}.
	 * If no such comparator is present, this implementation returns {@code null}.
	 * 
	 * @param beanInstance the bean instance to check (can be {@code null})
	 * @return the priority assigned to that bean or {@code null} if none is set
	 */
	@Nullable
	protected Integer getPriority(Object beanInstance) {
		Comparator<Object> comparator = getDependencyComparator();
		if (comparator instanceof OrderComparator) {
			return ((OrderComparator) comparator).getPriority(beanInstance);
		}
		return null;
	}

	/**
	 * Determine whether the given candidate name matches the bean name or the
	 * aliases stored in this bean definition.
	 */
	protected boolean matchesBeanName(String beanName, @Nullable String candidateName) {
		return (candidateName != null && (candidateName.equals(beanName)
				|| ObjectUtils.containsElement(getAliases(beanName), candidateName)));
	}

	/**
	 * Determine whether the given beanName/candidateName pair indicates a self
	 * reference, i.e. whether the candidate points back to the original bean or to
	 * a factory method on the original bean.
	 */
	private boolean isSelfReference(@Nullable String beanName, @Nullable String candidateName) {
		return (beanName != null && candidateName != null
				&& (beanName.equals(candidateName) || (containsBeanDefinition(candidateName)
						&& beanName.equals(getMergedLocalBeanDefinition(candidateName).getFactoryBeanName()))));
	}

	/**
	 * Raise a NoSuchBeanDefinitionException or BeanNotOfRequiredTypeException for
	 * an unresolvable dependency.
	 */
	private void raiseNoMatchingBeanFound(Class<?> type, ResolvableType resolvableType, DependencyDescriptor descriptor)
			throws BeansException {

		checkBeanNotOfRequiredType(type, descriptor);

		throw new NoSuchBeanDefinitionException(resolvableType,
				"expected at least 1 bean which qualifies as autowire candidate. " + "Dependency annotations: "
						+ ObjectUtils.nullSafeToString(descriptor.getAnnotations()));
	}

	/**
	 * Raise a BeanNotOfRequiredTypeException for an unresolvable dependency, if
	 * applicable, i.e. if the target type of the bean would match but an exposed
	 * proxy doesn't.
	 */
	private void checkBeanNotOfRequiredType(Class<?> type, DependencyDescriptor descriptor) {
		for (String beanName : this.beanDefinitionNames) {
			RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
			Class<?> targetType = mbd.getTargetType();
			if (targetType != null && type.isAssignableFrom(targetType)
					&& isAutowireCandidate(beanName, mbd, descriptor, getAutowireCandidateResolver())) {
				// Probably a proxy interfering with target type match -> throw meaningful
				// exception.
				Object beanInstance = getSingleton(beanName, false);
				Class<?> beanType = (beanInstance != null && beanInstance.getClass() != NullBean.class
						? beanInstance.getClass()
						: predictBeanType(beanName, mbd));
				if (beanType != null && !type.isAssignableFrom(beanType)) {
					throw new BeanNotOfRequiredTypeException(beanName, type, beanType);
				}
			}
		}

		BeanFactory parent = getParentBeanFactory();
		if (parent instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) parent).checkBeanNotOfRequiredType(type, descriptor);
		}
	}

	/**
	 * Create an {@link Optional} wrapper for the specified dependency.
	 */
	private Optional<?> createOptionalDependency(DependencyDescriptor descriptor, @Nullable String beanName,
			final Object... args) {

		DependencyDescriptor descriptorToUse = new NestedDependencyDescriptor(descriptor) {
			@Override
			public boolean isRequired() {
				return false;
			}

			@Override
			public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
				return (!ObjectUtils.isEmpty(args) ? beanFactory.getBean(beanName, args)
						: super.resolveCandidate(beanName, requiredType, beanFactory));
			}
		};
		return Optional.ofNullable(doResolveDependency(descriptorToUse, beanName, null, null));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(ObjectUtils.identityToString(this));
		sb.append(": defining beans [");
		sb.append(StringUtils.collectionToCommaDelimitedString(this.beanDefinitionNames));
		sb.append("]; ");
		BeanFactory parent = getParentBeanFactory();
		if (parent == null) {
			sb.append("root of factory hierarchy");
		} else {
			sb.append("parent: ").append(ObjectUtils.identityToString(parent));
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------------
	// Serialization support
	// ---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		throw new NotSerializableException("DefaultListableBeanFactory itself is not deserializable - "
				+ "just a SerializedBeanFactoryReference is");
	}

	protected Object writeReplace() throws ObjectStreamException {
		if (this.serializationId != null) {
			return new SerializedBeanFactoryReference(this.serializationId);
		} else {
			throw new NotSerializableException("DefaultListableBeanFactory has no serialization id");
		}
	}

	/**
	 * Minimal id reference to the factory. Resolved to the actual factory instance
	 * on deserialization.
	 */
	private static class SerializedBeanFactoryReference implements Serializable {

		private final String id;

		public SerializedBeanFactoryReference(String id) {
			this.id = id;
		}

		private Object readResolve() {
			Reference<?> ref = serializableFactories.get(this.id);
			if (ref != null) {
				Object result = ref.get();
				if (result != null) {
					return result;
				}
			}
			// Lenient fallback: dummy factory in case of original factory not found...
			DefaultListableBeanFactory dummyFactory = new DefaultListableBeanFactory();
			dummyFactory.serializationId = this.id;
			return dummyFactory;
		}
	}

	/**
	 * A dependency descriptor marker for nested elements.
	 */
	private static class NestedDependencyDescriptor extends DependencyDescriptor {

		public NestedDependencyDescriptor(DependencyDescriptor original) {
			super(original);
			increaseNestingLevel();
		}
	}

	/**
	 * A dependency descriptor for a multi-element declaration with nested elements.
	 */
	private static class MultiElementDescriptor extends NestedDependencyDescriptor {

		public MultiElementDescriptor(DependencyDescriptor original) {
			super(original);
		}
	}

	/**
	 * A dependency descriptor marker for stream access to multiple elements.
	 */
	private static class StreamDependencyDescriptor extends DependencyDescriptor {

		private final boolean ordered;

		public StreamDependencyDescriptor(DependencyDescriptor original, boolean ordered) {
			super(original);
			this.ordered = ordered;
		}

		public boolean isOrdered() {
			return this.ordered;
		}
	}

	private interface BeanObjectProvider<T> extends ObjectProvider<T>, Serializable {
	}

	/**
	 * Serializable ObjectFactory/ObjectProvider for lazy resolution of a
	 * dependency.
	 */
	private class DependencyObjectProvider implements BeanObjectProvider<Object> {

		private final DependencyDescriptor descriptor;

		private final boolean optional;

		@Nullable
		private final String beanName;

		public DependencyObjectProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
			this.descriptor = new NestedDependencyDescriptor(descriptor);
			this.optional = (this.descriptor.getDependencyType() == Optional.class);
			this.beanName = beanName;
		}

		@Override
		public Object getObject() throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName);
			} else {
				Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
				if (result == null) {
					throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
				}
				return result;
			}
		}

		@Override
		public Object getObject(final Object... args) throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName, args);
			} else {
				DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
					@Override
					public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
						return beanFactory.getBean(beanName, args);
					}
				};
				Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
				if (result == null) {
					throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
				}
				return result;
			}
		}

		@Override
		@Nullable
		public Object getIfAvailable() throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName);
			} else {
				DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
					@Override
					public boolean isRequired() {
						return false;
					}
				};
				return doResolveDependency(descriptorToUse, this.beanName, null, null);
			}
		}

		@Override
		@Nullable
		public Object getIfUnique() throws BeansException {
			DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
				@Override
				public boolean isRequired() {
					return false;
				}

				@Override
				@Nullable
				public Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) {
					return null;
				}
			};
			if (this.optional) {
				return createOptionalDependency(descriptorToUse, this.beanName);
			} else {
				return doResolveDependency(descriptorToUse, this.beanName, null, null);
			}
		}

		@Nullable
		protected Object getValue() throws BeansException {
			if (this.optional) {
				return createOptionalDependency(this.descriptor, this.beanName);
			} else {
				return doResolveDependency(this.descriptor, this.beanName, null, null);
			}
		}

		@Override
		public Stream<Object> stream() {
			return resolveStream(false);
		}

		@Override
		public Stream<Object> orderedStream() {
			return resolveStream(true);
		}

		@SuppressWarnings("unchecked")
		private Stream<Object> resolveStream(boolean ordered) {
			DependencyDescriptor descriptorToUse = new StreamDependencyDescriptor(this.descriptor, ordered);
			Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
			Assert.state(result instanceof Stream, "Stream expected");
			return (Stream<Object>) result;
		}
	}

	/**
	 * Separate inner class for avoiding a hard dependency on the
	 * {@code javax.inject} API. Actual {@code javax.inject.Provider} implementation
	 * is nested here in order to make it invisible for Graal's introspection of
	 * DefaultListableBeanFactory's nested classes.
	 */
	private class Jsr330Factory implements Serializable {

		public Object createDependencyProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
			return new Jsr330Provider(descriptor, beanName);
		}

		private class Jsr330Provider extends DependencyObjectProvider implements Provider<Object> {

			public Jsr330Provider(DependencyDescriptor descriptor, @Nullable String beanName) {
				super(descriptor, beanName);
			}

			@Override
			@Nullable
			public Object get() throws BeansException {
				return getValue();
			}
		}
	}

	/**
	 * An {@link org.springframework.core.OrderComparator.OrderSourceProvider}
	 * implementation that is aware of the bean metadata of the instances to sort.
	 * <p>
	 * Lookup for the method factory of an instance to sort, if any, and let the
	 * comparator retrieve the {@link org.springframework.core.annotation.Order}
	 * value defined on it. This essentially allows for the following construct:
	 */
	private class FactoryAwareOrderSourceProvider implements OrderComparator.OrderSourceProvider {

		private final Map<Object, String> instancesToBeanNames;

		public FactoryAwareOrderSourceProvider(Map<Object, String> instancesToBeanNames) {
			this.instancesToBeanNames = instancesToBeanNames;
		}

		@Override
		@Nullable
		public Object getOrderSource(Object obj) {
			RootBeanDefinition beanDefinition = getRootBeanDefinition(this.instancesToBeanNames.get(obj));
			if (beanDefinition == null) {
				return null;
			}
			List<Object> sources = new ArrayList<>(2);
			Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
			if (factoryMethod != null) {
				sources.add(factoryMethod);
			}
			Class<?> targetType = beanDefinition.getTargetType();
			if (targetType != null && targetType != obj.getClass()) {
				sources.add(targetType);
			}
			return sources.toArray();
		}

		@Nullable
		private RootBeanDefinition getRootBeanDefinition(@Nullable String beanName) {
			if (beanName != null && containsBeanDefinition(beanName)) {
				BeanDefinition bd = getMergedBeanDefinition(beanName);
				if (bd instanceof RootBeanDefinition) {
					return (RootBeanDefinition) bd;
				}
			}
			return null;
		}
	}

}
