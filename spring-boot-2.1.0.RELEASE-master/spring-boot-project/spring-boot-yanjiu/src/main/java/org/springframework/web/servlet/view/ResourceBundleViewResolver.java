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

package org.springframework.web.servlet.view;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.PropertiesBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.View;

/**
 * 它和XmlViewResolver一样，也是继承自AbstractCachingViewResolver，但是它缓存的不是视图，这个会在后面有说到。和XmlViewResolver一样它也需要有一个配置文件来定义逻辑视图名称和真正的View对象的对应关系，不同的是ResourceBundleViewResolver的配置文件是一个属性文件，而且必须是放在classpath路径下面的，默认情况下这个配置文件是在classpath根目录下的views.properties文件，如果不使用默认值的话，则可以通过属性baseName或baseNames来指定。baseName只是指定一个基名称，Spring会在指定的classpath根目录下寻找以指定的baseName开始的属性文件进行View解析，如指定的baseName是base，那么base.properties、baseabc.properties等等以base开始的属性文件都会被Spring当做ResourceBundleViewResolver解析视图的资源文件。ResourceBundleViewResolver使用的属性配置文件的内容类似于这样：
 * 
 * resourceBundle.(class)=org.springframework.web.servlet.view.InternalResourceView
 * resourceBundle.url=/index.jsp
 * test.(class)=org.springframework.web.servlet.view.InternalResourceView
 * test.url=/test.jsp
 * 在这个配置文件中我们定义了两个InternalResourceView对象，一个的名称是resourceBundle，对应URL是/index.jsp，另一个名称是test，对应的URL是/test.jsp。从这个定义来看我们可以知道resourceBundle是对应的视图名称，使用resourceBundle.(class)来指定它对应的视图类型，resourceBundle.url指定这个视图的url属性。会思考的读者看到这里可能会有这样一个问题：为什么resourceBundle的class属性要用小括号包起来，而它的url属性就不需要呢？这就需要从ResourceBundleViewResolver进行视图解析的方法来说了。ResourceBundleViewResolver还是通过bean工厂来获得对应视图名称的视图bean对象来解析视图的。那么这些bean从哪里来呢？就是从我们定义的properties属性文件中来。在ResourceBundleViewResolver第一次进行视图解析的时候会先new一个BeanFactory对象，然后把properties文件中定义好的属性按照它自身的规则生成一个个的bean对象注册到该BeanFactory中，之后会把该BeanFactory对象保存起来，所以ResourceBundleViewResolver缓存的是BeanFactory，而不是直接的缓存从BeanFactory中取出的视图bean。然后会从bean工厂中取出名称为逻辑视图名称的视图bean进行返回。接下来就讲讲Spring通过properties文件生成bean的规则。它会把properties文件中定义的属性名称按最后一个点“.”进行分割，把点前面的内容当做是bean名称，点后面的内容当做是bean的属性。这其中有几个特别的属性，Spring把它们用小括号包起来了，这些特殊的属性一般是对应的attribute，但不是bean对象所有的attribute都可以这样用。其中(class)是一个，除了(class)之外，还有(scope)、(parent)、(abstract)、(lazy-init)。而除了这些特殊的属性之外的其他属性，Spring会把它们当做bean对象的一般属性进行处理，就是bean对象对应的property。所以根据上面的属性配置文件将生成如下两个bean对象：
 * 
 * A {@link org.springframework.web.servlet.ViewResolver} implementation that
 * uses bean definitions in a {@link ResourceBundle}, specified by the bundle
 * basename.
 *
 * <p>
 * The bundle is typically defined in a properties file, located in the
 * classpath. The default bundle basename is "views".
 *
 * <p>
 * This {@code ViewResolver} supports localized view definitions, using the
 * default support of {@link java.util.PropertyResourceBundle}. For example, the
 * basename "views" will be resolved as class path resources
 * "views_de_AT.properties", "views_de.properties", "views.properties" - for a
 * given Locale "de_AT".
 *
 * <p>
 * Note: This {@code ViewResolver} implements the {@link Ordered} interface in
 * order to allow for flexible participation in {@code ViewResolver} chaining.
 * For example, some special views could be defined via this
 * {@code ViewResolver} (giving it 0 as "order" value), while all remaining
 * views could be resolved by a {@link UrlBasedViewResolver}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see java.util.ResourceBundle#getBundle
 * @see java.util.PropertyResourceBundle
 * @see UrlBasedViewResolver
 */
public class ResourceBundleViewResolver extends AbstractCachingViewResolver
		implements Ordered, InitializingBean, DisposableBean {

	/** The default basename if no other basename is supplied */
	public static final String DEFAULT_BASENAME = "views";

	private String[] basenames = new String[] { DEFAULT_BASENAME };

	private ClassLoader bundleClassLoader = Thread.currentThread().getContextClassLoader();

	@Nullable
	private String defaultParentView;

	@Nullable
	private Locale[] localesToInitialize;

	private int order = Ordered.LOWEST_PRECEDENCE; // default: same as non-Ordered

	/* Locale -> BeanFactory */
	private final Map<Locale, BeanFactory> localeCache = new HashMap<>();

	/* List of ResourceBundle -> BeanFactory */
	private final Map<List<ResourceBundle>, ConfigurableApplicationContext> bundleCache = new HashMap<>();

	/**
	 * Set a single basename, following {@link java.util.ResourceBundle}
	 * conventions. The default is "views".
	 * <p>
	 * {@code ResourceBundle} supports different locale suffixes. For example, a
	 * base name of "views" might map to {@code ResourceBundle} files "views",
	 * "views_en_au" and "views_de".
	 * <p>
	 * Note that ResourceBundle names are effectively classpath locations: As a
	 * consequence, the JDK's standard ResourceBundle treats dots as package
	 * separators. This means that "test.theme" is effectively equivalent to
	 * "test/theme", just like it is for programmatic
	 * {@code java.util.ResourceBundle} usage.
	 * 
	 * @see #setBasenames
	 * @see ResourceBundle#getBundle(String)
	 * @see ResourceBundle#getBundle(String, Locale)
	 */
	public void setBasename(String basename) {
		setBasenames(basename);
	}

	/**
	 * Set an array of basenames, each following {@link java.util.ResourceBundle}
	 * conventions. The default is a single basename "views".
	 * <p>
	 * {@code ResourceBundle} supports different locale suffixes. For example, a
	 * base name of "views" might map to {@code ResourceBundle} files "views",
	 * "views_en_au" and "views_de".
	 * <p>
	 * The associated resource bundles will be checked sequentially when resolving a
	 * message code. Note that message definitions in a <i>previous</i> resource
	 * bundle will override ones in a later bundle, due to the sequential lookup.
	 * <p>
	 * Note that ResourceBundle names are effectively classpath locations: As a
	 * consequence, the JDK's standard ResourceBundle treats dots as package
	 * separators. This means that "test.theme" is effectively equivalent to
	 * "test/theme", just like it is for programmatic
	 * {@code java.util.ResourceBundle} usage.
	 * 
	 * @see #setBasename
	 * @see ResourceBundle#getBundle(String)
	 * @see ResourceBundle#getBundle(String, Locale)
	 */
	public void setBasenames(String... basenames) {
		this.basenames = basenames;
	}

	/**
	 * Set the {@link ClassLoader} to load resource bundles with. Default is the
	 * thread context {@code ClassLoader}.
	 */
	public void setBundleClassLoader(ClassLoader classLoader) {
		this.bundleClassLoader = classLoader;
	}

	/**
	 * Return the {@link ClassLoader} to load resource bundles with.
	 * <p>
	 * Default is the specified bundle {@code ClassLoader}, usually the thread
	 * context {@code ClassLoader}.
	 */
	protected ClassLoader getBundleClassLoader() {
		return this.bundleClassLoader;
	}

	/**
	 * Set the default parent for views defined in the {@code ResourceBundle}.
	 * <p>
	 * This avoids repeated "yyy1.(parent)=xxx", "yyy2.(parent)=xxx" definitions in
	 * the bundle, especially if all defined views share the same parent.
	 * <p>
	 * The parent will typically define the view class and common attributes.
	 * Concrete views might simply consist of an URL definition then: a la
	 * "yyy1.url=/my.jsp", "yyy2.url=/your.jsp".
	 * <p>
	 * View definitions that define their own parent or carry their own class can
	 * still override this. Strictly speaking, the rule that a default parent
	 * setting does not apply to a bean definition that carries a class is there for
	 * backwards compatibility reasons. It still matches the typical use case.
	 */
	public void setDefaultParentView(String defaultParentView) {
		this.defaultParentView = defaultParentView;
	}

	/**
	 * Specify Locales to initialize eagerly, rather than lazily when actually
	 * accessed.
	 * <p>
	 * Allows for pre-initialization of common Locales, eagerly checking the view
	 * configuration for those Locales.
	 */
	public void setLocalesToInitialize(Locale... localesToInitialize) {
		this.localesToInitialize = localesToInitialize;
	}

	/**
	 * Specify the order value for this ViewResolver bean.
	 * <p>
	 * The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * 
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Eagerly initialize Locales if necessary.
	 * 
	 * @see #setLocalesToInitialize
	 */
	@Override
	public void afterPropertiesSet() throws BeansException {
		if (this.localesToInitialize != null) {
			for (Locale locale : this.localesToInitialize) {
				initFactory(locale);
			}
		}
	}

	@Override
	protected View loadView(String viewName, Locale locale) throws Exception {
		BeanFactory factory = initFactory(locale);
		try {
			return factory.getBean(viewName, View.class);
		} catch (NoSuchBeanDefinitionException ex) {
			// Allow for ViewResolver chaining...
			return null;
		}
	}

	/**
	 * Initialize the View {@link BeanFactory} from the {@code ResourceBundle}, for
	 * the given {@link Locale locale}.
	 * <p>
	 * Synchronized because of access by parallel threads.
	 * 
	 * @param locale the target {@code Locale}
	 * @return the View factory for the given Locale
	 * @throws BeansException in case of initialization errors
	 */
	protected synchronized BeanFactory initFactory(Locale locale) throws BeansException {
		// Try to find cached factory for Locale:
		// Have we already encountered that Locale before?
		if (isCache()) {
			BeanFactory cachedFactory = this.localeCache.get(locale);
			if (cachedFactory != null) {
				return cachedFactory;
			}
		}

		// Build list of ResourceBundle references for Locale.
		List<ResourceBundle> bundles = new LinkedList<>();
		for (String basename : this.basenames) {
			ResourceBundle bundle = getBundle(basename, locale);
			bundles.add(bundle);
		}

		// Try to find cached factory for ResourceBundle list:
		// even if Locale was different, same bundles might have been found.
		if (isCache()) {
			BeanFactory cachedFactory = this.bundleCache.get(bundles);
			if (cachedFactory != null) {
				this.localeCache.put(locale, cachedFactory);
				return cachedFactory;
			}
		}

		// Create child ApplicationContext for views.
		GenericWebApplicationContext factory = new GenericWebApplicationContext();
		factory.setParent(getApplicationContext());
		factory.setServletContext(getServletContext());

		// Load bean definitions from resource bundle.
		PropertiesBeanDefinitionReader reader = new PropertiesBeanDefinitionReader(factory);
		reader.setDefaultParentBean(this.defaultParentView);
		for (ResourceBundle bundle : bundles) {
			reader.registerBeanDefinitions(bundle);
		}

		factory.refresh();

		// Cache factory for both Locale and ResourceBundle list.
		if (isCache()) {
			this.localeCache.put(locale, factory);
			this.bundleCache.put(bundles, factory);
		}

		return factory;
	}

	/**
	 * Obtain the resource bundle for the given basename and {@link Locale}.
	 * 
	 * @param basename the basename to look for
	 * @param locale   the {@code Locale} to look for
	 * @return the corresponding {@code ResourceBundle}
	 * @throws MissingResourceException if no matching bundle could be found
	 * @see ResourceBundle#getBundle(String, Locale, ClassLoader)
	 */
	protected ResourceBundle getBundle(String basename, Locale locale) throws MissingResourceException {
		return ResourceBundle.getBundle(basename, locale, getBundleClassLoader());
	}

	/**
	 * Close the bundle View factories on context shutdown.
	 */
	@Override
	public void destroy() throws BeansException {
		for (ConfigurableApplicationContext factory : this.bundleCache.values()) {
			factory.close();
		}
		this.localeCache.clear();
		this.bundleCache.clear();
	}

}
