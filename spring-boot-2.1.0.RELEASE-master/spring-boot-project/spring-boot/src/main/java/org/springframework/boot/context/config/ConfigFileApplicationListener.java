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

package org.springframework.boot.context.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * {@link EnvironmentPostProcessor} that configures the context environment by
 * loading properties from well known file locations. By default properties will
 * be loaded from 'application.properties' and/or 'application.yml' files in the
 * following locations:
 * <ul>
 * <li>classpath:</li>
 * <li>file:./</li>
 * <li>classpath:config/</li>
 * <li>file:./config/:</li>
 * </ul>
 * <p>
 * Alternative search locations and names can be specified using
 * {@link #setSearchLocations(String)} and {@link #setSearchNames(String)}.
 * <p>
 * Additional files will also be loaded based on active profiles. For example if
 * a 'web' profile is active 'application-web.properties' and
 * 'application-web.yml' will be considered.
 * <p>
 * The 'spring.config.name' property can be used to specify an alternative name
 * to load and the 'spring.config.location' property can be used to specify
 * alternative search locations or specific files.
 * <p>
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Eddú Meléndez
 * @author Madhura Bhave
 */
public class ConfigFileApplicationListener implements EnvironmentPostProcessor, SmartApplicationListener, Ordered {

	private static final String DEFAULT_PROPERTIES = "defaultProperties";

	// Note the order is from least to most specific (last one wins)
	private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";

	private static final String DEFAULT_NAMES = "application";

	private static final Set<String> NO_SEARCH_NAMES = Collections.singleton(null);

	private static final Bindable<String[]> STRING_ARRAY = Bindable.of(String[].class);

	/**
	 * The "active profiles" property name.
	 */
	public static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";

	/**
	 * The "includes profiles" property name.
	 */
	public static final String INCLUDE_PROFILES_PROPERTY = "spring.profiles.include";

	/**
	 * The "config name" property name.
	 */
	public static final String CONFIG_NAME_PROPERTY = "spring.config.name";

	/**
	 * The "config location" property name.
	 */
	public static final String CONFIG_LOCATION_PROPERTY = "spring.config.location";

	/**
	 * The "config additional location" property name.
	 */
	public static final String CONFIG_ADDITIONAL_LOCATION_PROPERTY = "spring.config.additional-location";

	/**
	 * The default order for the processor.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

	private final DeferredLog logger = new DeferredLog();

	private String searchLocations;

	private String names;

	private int order = DEFAULT_ORDER;

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType)
				|| ApplicationPreparedEvent.class.isAssignableFrom(eventType);
	}
//	监听器支持ApplicationEnvironmentPreparedEvent、ApplicationPreparedEvent事件，
	//其中ApplicationEnvironmentPreparedEvent事件是在
	//SpringApplication#prepareEnvironment方法中通过listeners.environmentPrepared(environment)触发，
	//ApplicationPreparedEvent事件是在SpringApplication#prepareContext方法中通过listeners.contextLoaded(context)触发；
//	————————————————
//	版权声明：本文为CSDN博主「随风yy」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/yaomingyang/article/details/109259884
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
	    //事件会在应用程序启动后准备Environment环境时触发
		if (event instanceof ApplicationEnvironmentPreparedEvent) {
			onApplicationEnvironmentPreparedEvent((ApplicationEnvironmentPreparedEvent) event);
		}
	    //时间会在应用程序已经启动，ApplicationContext已经准备好，但是还未调用refreshed方式时触发
		if (event instanceof ApplicationPreparedEvent) {
			onApplicationPreparedEvent(event);
		}
	}
	//先看ApplicationEnvironmentPreparedEvent时间分支，这个时间主要是用来加载properties及yml配置文件的业务处理；
	private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
		//通过SPI的方式加载环境相关EnvironmentPostProcessor后置处理器
		List<EnvironmentPostProcessor> postProcessors = loadPostProcessors();
//		首先调用SpringFactoriesLoader加载EnvironmentPostProcessor.
		// 同时也将自己加入到postProcessors.排序后依次调用其postProcessEnvironment方法.
//		对于当前场景来说. EnvironmentPostProcessor如下:
//
//		org.springframework.boot.env.SpringApplicationJsonEnvironmentPostProcessor, 
//		org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor, 
//		org.springframework.boot.context.config.ConfigFileApplicationListener
//		————————————————
//		版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//		原文链接：https://blog.csdn.net/qq_26000415/article/details/78914944
		postProcessors.add(this);
		AnnotationAwareOrderComparator.sort(postProcessors);
		for (EnvironmentPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessEnvironment(event.getEnvironment(), event.getSpringApplication());
		}
	}

	List<EnvironmentPostProcessor> loadPostProcessors() {
		return SpringFactoriesLoader.loadFactories(EnvironmentPostProcessor.class, getClass().getClassLoader());
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		//添加配置文件属性源到指定的环境
		addPropertySources(environment, application.getResourceLoader());
	}

	private void onApplicationPreparedEvent(ApplicationEvent event) {
		this.logger.switchTo(ConfigFileApplicationListener.class);
		addPostProcessors(((ApplicationPreparedEvent) event).getApplicationContext());
	}

	/**
	 * Add config file property sources to the specified environment.
	 * 
	 * @param environment    the environment to add source to
	 * @param resourceLoader the resource loader
	 * @see #addPostProcessors(ConfigurableApplicationContext)
	 */
	protected void addPropertySources(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
		// 调用了RandomValuePropertySource#addToEnvironment方法,向environment中名为systemEnvironment的添加了RandomValuePropertySource(名称为random)
		// 正是因此我们才能在配置文件中使用${random.int} 生成随机值.
		RandomValuePropertySource.addToEnvironment(environment);
		new Loader(environment, resourceLoader).load(); // 加载配置文件等配置属性，TODO:应该有配置文件和外部配置等？【加载配置文件的主线】
	}

	/**
	 * Add appropriate post-processors to post-configure the property-sources.
	 * 
	 * @param context the context to configure
	 */
	protected void addPostProcessors(ConfigurableApplicationContext context) {
		context.addBeanFactoryPostProcessor(new PropertySourceOrderingPostProcessor(context));
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Set the search locations that will be considered as a comma-separated list.
	 * Each search location should be a directory path (ending in "/") and it will
	 * be prefixed by the file names constructed from {@link #setSearchNames(String)
	 * search names} and profiles (if any) plus file extensions supported by the
	 * properties loaders. Locations are considered in the order specified, with
	 * later items taking precedence (like a map merge).
	 * 
	 * @param locations the search locations
	 */
	public void setSearchLocations(String locations) {
		Assert.hasLength(locations, "Locations must not be empty");
		this.searchLocations = locations;
	}

	/**
	 * Sets the names of the files that should be loaded (excluding file extension)
	 * as a comma-separated list.
	 * 
	 * @param names the names to load
	 */
	public void setSearchNames(String names) {
		Assert.hasLength(names, "Names must not be empty");
		this.names = names;
	}

	/**
	 * {@link BeanFactoryPostProcessor} to re-order our property sources below any
	 * {@code @PropertySource} items added by the
	 * {@link ConfigurationClassPostProcessor}.
	 */
	private class PropertySourceOrderingPostProcessor implements BeanFactoryPostProcessor, Ordered {

		private ConfigurableApplicationContext context;

		PropertySourceOrderingPostProcessor(ConfigurableApplicationContext context) {
			this.context = context;
		}

		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			reorderSources(this.context.getEnvironment());
		}

		private void reorderSources(ConfigurableEnvironment environment) {
			PropertySource<?> defaultProperties = environment.getPropertySources().remove(DEFAULT_PROPERTIES);
			if (defaultProperties != null) {
				environment.getPropertySources().addLast(defaultProperties);
			}
		}

	}

	/**
	 * Loads candidate property sources and configures the active profiles.
	 */
	private class Loader {

		private final Log logger = ConfigFileApplicationListener.this.logger;

		private final ConfigurableEnvironment environment;

		private final PropertySourcesPlaceholdersResolver placeholdersResolver;

		private final ResourceLoader resourceLoader;

		private final List<PropertySourceLoader> propertySourceLoaders;

		private Deque<Profile> profiles;

		private List<Profile> processedProfiles;

		private boolean activatedProfiles;

		private Map<Profile, MutablePropertySources> loaded;

		private Map<DocumentsCacheKey, List<Document>> loadDocumentsCache = new HashMap<>();

		Loader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
			this.environment = environment;
			this.placeholdersResolver = new PropertySourcesPlaceholdersResolver(this.environment);
			this.resourceLoader = (resourceLoader != null) ? resourceLoader : new DefaultResourceLoader();
			//可以发现loaders 是通过SpringFactoriesLoader加载META-INF/spring.factories中org.springframework.boot.env.PropertySourceLoader的配置.其中,配置如下://
			//# PropertySource Loaders
			//org.springframework.boot.env.PropertySourceLoader=\
			//org.springframework.boot.env.PropertiesPropertySourceLoader,\
			//org.springframework.boot.env.YamlPropertySourceLoader
			//至此,我们就知道了loaders 为 PropertiesPropertySourceLoader,YamlPropertySourceLoader
			//通过源码可知.PropertiesPropertySourceLoader支持的扩展名为–> properties, xml.
			//YamlPropertySourceLoader支持的扩展名为–> yml, yaml.
			this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class,
					getClass().getClassLoader());
		}

//	    处理步骤如下:
//	        1. 调用 initializeActiveProfiles.获得ActiveProfiles.将未激活的Profiles加入到profiles中.如果profiles为空的话,就将spring.profiles.default配置的profile添加到profiles中.
//	        2. 依次遍历 profiles 中的profile.依次在classpath:/,classpath:/config/,file:./,file:./config/中加载application的配置.调用ConfigFileApplicationListener$Loader#load进行加载.
//	        3. 调用 addLoadedPropertySources,向environment中添加 ConfigurationPropertySources.
		public void load() {
			  //候选配置文件后缀，如：application-test.properties指的是test
			this.profiles = new LinkedList<>();
			this.processedProfiles = new LinkedList<>();
			//是否有激活的配置文件：spring.profiles.active
			this.activatedProfiles = false;
			  //存储从配置文件加载并解析后的配置文件
			this.loaded = new LinkedHashMap<>();
			 //初始化默认的profile配置
			initializeProfiles();
			while (!this.profiles.isEmpty()) {
				Profile profile = this.profiles.poll();
				if (profile != null && !profile.isDefaultProfile()) {
					addProfileToEnvironment(profile.getName());
				} // 【加载配置文件的主线】
//				逻辑如下:
//				    遍历profiles
//				    在SearchLocations中依次进行加载profile的配置.这
				// 里有判断location是否是已/结尾的.如果不是以/结尾的,就意味着明确了文件的路径,直接加载即可.否则遍历location下的SearchName进行加载.
				load(profile, this::getPositiveProfileFilter, addToLoaded(MutablePropertySources::addLast, false));
				this.processedProfiles.add(profile);
			}
			resetEnvironmentProfiles(this.processedProfiles);
			load(null, this::getNegativeProfileFilter, addToLoaded(MutablePropertySources::addFirst, true));
			addLoadedPropertySources();
		}

		/**
		 * 
		 * 代码的逻辑如下:
		 * 
		 * 1. 如果environment不含有spring.profiles.active和spring.profiles.include的配置话,返回空集合
		 * 
		 * > 注意: 当前的environment拥有的source有
		 * commandLineArgs、servletConfigInitParams、servletContextInitParams、systemProperties、systemEnvironment,
		 * RandomValuePropertySource 如果想 不返回空的话,就需要在以上的source中有配置.最简单的方式是通过命令行的方式传入
		 * --spring.profiles.active=you profiles 即可 *
		 * 3.getProfilesActivatedViaProperty方法将数据进行绑定，将activatedProfiles设为true
		 * Initialize profile information from both the {@link Environment} active
		 * profiles and any
		 * {@code spring.profiles.active}/{@code spring.profiles.include} properties
		 * that are already set.
		 */
		private void initializeProfiles() {
			// The default profile for these purposes is represented as null. We add it
			// first so that it is processed first and has lowest priority.
			this.profiles.add(null);
			Set<Profile> activatedViaProperty = getProfilesActivatedViaProperty();
			this.profiles.addAll(getOtherActiveProfiles(activatedViaProperty));
			// Any pre-existing active profiles set via property sources (e.g.
			// System properties) take precedence over those added in config files.
			addActiveProfiles(activatedViaProperty);
			if (this.profiles.size() == 1) { // only has null profile
				for (String defaultProfileName : this.environment.getDefaultProfiles()) {
					Profile defaultProfile = new Profile(defaultProfileName, true);
					this.profiles.add(defaultProfile);
				}
			}
		}

		private Set<Profile> getProfilesActivatedViaProperty() {
			if (!this.environment.containsProperty(ACTIVE_PROFILES_PROPERTY)
					&& !this.environment.containsProperty(INCLUDE_PROFILES_PROPERTY)) {
				return Collections.emptySet();
			}
			Binder binder = Binder.get(this.environment);
			Set<Profile> activeProfiles = new LinkedHashSet<>();
			activeProfiles.addAll(getProfiles(binder, INCLUDE_PROFILES_PROPERTY));
			activeProfiles.addAll(getProfiles(binder, ACTIVE_PROFILES_PROPERTY));
			return activeProfiles;
		}

		private List<Profile> getOtherActiveProfiles(Set<Profile> activatedViaProperty) {
			return Arrays.stream(this.environment.getActiveProfiles()).map(Profile::new)
					.filter((profile) -> !activatedViaProperty.contains(profile)).collect(Collectors.toList());
		}

		void addActiveProfiles(Set<Profile> profiles) {
			if (profiles.isEmpty()) {
				return;
			}
			if (this.activatedProfiles) {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("Profiles already activated, '" + profiles + "' will not be applied");
				}
				return;
			}
			this.profiles.addAll(profiles);
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Activated activeProfiles " + StringUtils.collectionToCommaDelimitedString(profiles));
			}
			this.activatedProfiles = true;
			removeUnprocessedDefaultProfiles();
		}

		private void removeUnprocessedDefaultProfiles() {
			this.profiles.removeIf((profile) -> (profile != null && profile.isDefaultProfile()));
		}
//		这一块的作用是
//		defaultFilter：profile 为 null 的情况，处理默认情况，即未定义 spring.profiles
//		profileFilter：profile 非 null 的情况，处理配置文件中定义了 spring.profiles 属性，则需要使用 profile 和 spring.profiles 匹配。
//
//		就是当配置文件中配置了spring.profiles，位置所属的profile就不在根据文件名后缀来判断而是根据spring.profiles来判断
//		————————————————
//		版权声明：本文为CSDN博主「大·风」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//		原文链接：https://blog.csdn.net/qq330983778/article/details/92703967
		private DocumentFilter getPositiveProfileFilter(Profile profile) {
			return (Document document) -> {
				if (profile == null) {
					return ObjectUtils.isEmpty(document.getProfiles());
				}
				return ObjectUtils.containsElement(document.getProfiles(), profile.getName())
						&& this.environment.acceptsProfiles(Profiles.of(document.getProfiles()));
			};
		}

		private DocumentFilter getNegativeProfileFilter(Profile profile) {
			return (Document document) -> (profile == null && !ObjectUtils.isEmpty(document.getProfiles())
					&& this.environment.acceptsProfiles(Profiles.of(document.getProfiles())));
		}

		private DocumentConsumer addToLoaded(BiConsumer<MutablePropertySources, PropertySource<?>> addMethod,
				boolean checkForExisting) {
			return (profile, document) -> {
				if (checkForExisting) {
					for (MutablePropertySources merged : this.loaded.values()) {
						if (merged.contains(document.getPropertySource().getName())) {
							return;
						}
					}
				}
				MutablePropertySources merged = this.loaded.computeIfAbsent(profile,
						(k) -> new MutablePropertySources());
				addMethod.accept(merged, document.getPropertySource());
			};
		}

		// 【加载配置文件的主线】
		// 首先判断environment是否有spring.config.location的配置,如果有的话就加入的搜索路径中.然后加入默认的路径–>classpath:/,classpath:/config/,file:./,file:./config/
		private void load(Profile profile, DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
			getSearchLocations().forEach((location) -> {
				boolean isFolder = location.endsWith("/");
				Set<String> names = isFolder ? getSearchNames() : NO_SEARCH_NAMES;
				names.forEach( // 【加载配置文件的主线】
						(name) -> load(location, name, profile, filterFactory, consumer));
			});
		}

//		
//
//		    循环遍历可加载文件的扩展名,对于当前场景来说,可加载的扩展名为–> properties, xml, yml, yaml.
//
//		    如果profile不等于null的话, 调用loadIntoGroup,加载的location为 location/application-profile.ext
//
//		    循环遍历loadForFileExtension, 加载的location为 location/application-processedProfile.ext
//		    调用loadIntoGroup,加载的location为 location/application-pprofile.ext,这是因为人们有时会在application-dev.yml 中配置 spring.profiles: dev 这种情况下,也应该进行加载.
//		    最后,调用调用loadIntoGroup,,加载的location为location/application.txt.
//		————————————————
//		版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//		原文链接：https://blog.csdn.net/qq_26000415/article/details/78914944
		private void load(String location, String name, Profile profile, DocumentFilterFactory filterFactory,
				DocumentConsumer consumer) {
			if (!StringUtils.hasText(name)) {
				for (PropertySourceLoader loader : this.propertySourceLoaders) {
					if (canLoadFileExtension(loader, location)) {
						load(loader, location, profile, filterFactory.getDocumentFilter(profile), consumer);
						return;
					}
				}
			}
			Set<String> processed = new HashSet<>();
			for (PropertySourceLoader loader : this.propertySourceLoaders) {
				for (String fileExtension : loader.getFileExtensions()) {
					if (processed.add(fileExtension)) { // 【加载配置文件的主线】
						loadForFileExtension(loader, location + name, "." + fileExtension, profile, filterFactory,
								consumer);
					}
				}
			}
		}

		private boolean canLoadFileExtension(PropertySourceLoader loader, String name) {
			return Arrays.stream(loader.getFileExtensions())
					.anyMatch((fileExtension) -> StringUtils.endsWithIgnoreCase(name, fileExtension));
		}

		private void loadForFileExtension(PropertySourceLoader loader, String prefix, String fileExtension,
				Profile profile, DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
			// 获得默认DocumentFilter和配置的DocumentFilter
			DocumentFilter defaultFilter = filterFactory.getDocumentFilter(null);
			DocumentFilter profileFilter = filterFactory.getDocumentFilter(profile);
			if (profile != null) {
				// Try profile-specific file & profile section in profile file (gh-340)
				String profileSpecificFile = prefix + "-" + profile + fileExtension;
				load(loader, profileSpecificFile, profile, defaultFilter, consumer);
				load(loader, profileSpecificFile, profile, profileFilter, consumer);
				// Try profile specific sections in files we've already processed
				// 特殊情况，之前读取 Profile 对应的配置文件，也可被当前 Profile 所读取
				for (Profile processedProfile : this.processedProfiles) {
					if (processedProfile != null) {
						String previouslyLoaded = prefix + "-" + processedProfile + fileExtension;
						load(loader, previouslyLoaded, profile, profileFilter, consumer);
					}
				}
			} // 【加载配置文件的主线】
				// Also try the profile-specific section (if any) of the normal file
			 //加载（无需带 Profile）指定的配置文件
			load(loader, prefix + fileExtension, profile, profileFilter, consumer);
		}

		private void load(PropertySourceLoader loader, String location, Profile profile, DocumentFilter filter,
				DocumentConsumer consumer) {
			try {
				//根据参数配置文件路径获取配置文件对应的Resource资源对象
		  		//classpath开头的环境变量配置返回的是ClassPathResource对象
		  		//file开头的环境变量配置路径返回的是FileSystemResource对象
				Resource resource = this.resourceLoader.getResource(location);
				   //如果配置文件资源不存在，则继续循环
				if (resource == null || !resource.exists()) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped missing config ", location, resource,
								profile);
						this.logger.trace(description);
					}
					return;
				}  //如果文件扩展名不存在，则继续循环
				if (!StringUtils.hasText(StringUtils.getFilenameExtension(resource.getFilename()))) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped empty config extension ", location,
								resource, profile);
						this.logger.trace(description);
					}
					return;
				} // 【加载配置文件的主线：application.properties】
				//将资源配置文件路径拼接成为PropertySourceLoader加载程序name需要的格式
		          //如：applicationConfig: [classpath:/application-test.properties]
				String name = "applicationConfig: [" + location + "]";
				 //核心方法，传递资源加载程序PropertySourceLoader实例、配置文件name及资源Resource对象
		          //返回的是加载到的配置文件对象，包含PropertySource对象、配置profiles、激活配置activeProfiles
		          //包含配置includeProfiles属性
				List<Document> documents = loadDocuments(loader, name, resource); 
				if (CollectionUtils.isEmpty(documents)) {
					if (this.logger.isTraceEnabled()) {
						StringBuilder description = getDescription("Skipped unloaded config ", location, resource,
								profile);
						this.logger.trace(description);
					}
					return;
				}
				List<Document> loaded = new ArrayList<>();
				for (Document document : documents) {
					if (filter.match(document)) {
						addActiveProfiles(document.getActiveProfiles());
						addIncludedProfiles(document.getIncludeProfiles());
						loaded.add(document);
					}
				}
				Collections.reverse(loaded);
				if (!loaded.isEmpty()) {
					loaded.forEach((document) -> consumer.accept(profile, document));
					if (this.logger.isDebugEnabled()) {
						StringBuilder description = getDescription("Loaded config file ", location, resource, profile);
						this.logger.debug(description);
					}
				}
			} catch (Exception ex) {
				throw new IllegalStateException("Failed to load property " + "source from location '" + location + "'",
						ex);
			}
		}

		private void addIncludedProfiles(Set<Profile> includeProfiles) {
			LinkedList<Profile> existingProfiles = new LinkedList<>(this.profiles);
			this.profiles.clear();
			this.profiles.addAll(includeProfiles);
			this.profiles.removeAll(this.processedProfiles);
			this.profiles.addAll(existingProfiles);
		}

		private List<Document> loadDocuments(PropertySourceLoader loader, String name, Resource resource)
				throws IOException {
			//创建用于保存多次加载同一文档的缓存键
			DocumentsCacheKey cacheKey = new DocumentsCacheKey(loader, resource);
			//获取键对应的文档信息
			List<Document> documents = this.loadDocumentsCache.get(cacheKey);
			//如果缓存中文档信息不存在
			if (documents == null) {
				List<PropertySource<?>> loaded = loader.load(name, resource);
				 //将资源PropertySource属性资源对象转换为Document对象
				documents = asDocuments(loaded);
				this.loadDocumentsCache.put(cacheKey, documents);
			}
			return documents;
		}

		private List<Document> asDocuments(List<PropertySource<?>> loaded) {
			if (loaded == null) {
				return Collections.emptyList();
			}
			return loaded.stream().map((propertySource) -> {
				Binder binder = new Binder( // 将propertySource对象作为Binder的构造参数封装进Binder对象，留在后续属性绑定时用，此时也是在listener对象中完成
						ConfigurationPropertySources.from(propertySource), this.placeholdersResolver);
				return new Document(propertySource, binder.bind("spring.profiles", STRING_ARRAY).orElse(null),
						getProfiles(binder, ACTIVE_PROFILES_PROPERTY), getProfiles(binder, INCLUDE_PROFILES_PROPERTY));
			}).collect(Collectors.toList());
		}

		private StringBuilder getDescription(String prefix, String location, Resource resource, Profile profile) {
			StringBuilder result = new StringBuilder(prefix);
			try {
				if (resource != null) {
					String uri = resource.getURI().toASCIIString();
					result.append("'");
					result.append(uri);
					result.append("' (");
					result.append(location);
					result.append(")");
				}
			} catch (IOException ex) {
				result.append(location);
			}
			if (profile != null) {
				result.append(" for profile ");
				result.append(profile);
			}
			return result;
		}

		private Set<Profile> getProfiles(Binder binder, String name) {
			return binder.bind(name, STRING_ARRAY).map(this::asProfileSet).orElse(Collections.emptySet());
		}

		private Set<Profile> asProfileSet(String[] profileNames) {
			List<Profile> profiles = new ArrayList<>();
			for (String profileName : profileNames) {
				profiles.add(new Profile(profileName));
			}
			return new LinkedHashSet<>(profiles);
		}

		private void addProfileToEnvironment(String profile) {
			for (String activeProfile : this.environment.getActiveProfiles()) {
				if (activeProfile.equals(profile)) {
					return;
				}
			}
			this.environment.addActiveProfile(profile);
		}

		private Set<String> getSearchLocations() {
			// 假如环境中存在 spring.config.location 则解析数据直接返回
			if (this.environment.containsProperty(CONFIG_LOCATION_PROPERTY)) {
				return getSearchLocations(CONFIG_LOCATION_PROPERTY);
			}
			// 获得spring.config.additional-location配置的值并解析成地址
			Set<String> locations = getSearchLocations(CONFIG_ADDITIONAL_LOCATION_PROPERTY);
			// 添加 searchLocations 到 locations 中 
						// 假如不存在 则添加classpath:/,classpath:/config/,file:./,file:./config/进去
			locations.addAll(asResolvedSet(ConfigFileApplicationListener.this.searchLocations, DEFAULT_SEARCH_LOCATIONS));
			return locations;
		}

		private Set<String> getSearchLocations(String propertyName) {
			Set<String> locations = new LinkedHashSet<>();
			if (this.environment.containsProperty(propertyName)) {
				for (String path : asResolvedSet(this.environment.getProperty(propertyName), null)) {
					if (!path.contains("$")) {
						path = StringUtils.cleanPath(path);
						if (!ResourceUtils.isUrl(path)) {
							path = ResourceUtils.FILE_URL_PREFIX + path;
						}
					}
					locations.add(path);
				}
			}
			return locations;
		}

//		还是同样的套路,如果environment有配置spring.config.name的话,就用配置的.否则就返回默认的application的.
//
//		到这里我们就明白了. 在classpath:/,classpath:/config/,file:./,file:./config/ 路径下,加载 application-profile 的配置文件.
//
//		加载代码如下:
//		————————————————
//		版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//		原文链接：https://blog.csdn.net/qq_26000415/article/details/78914944
		private Set<String> getSearchNames() {
			if (this.environment.containsProperty(CONFIG_NAME_PROPERTY)) {
				String property = this.environment.getProperty(CONFIG_NAME_PROPERTY);
				return asResolvedSet(property, null);
			}
			return asResolvedSet(ConfigFileApplicationListener.this.names, DEFAULT_NAMES);
		}

		private Set<String> asResolvedSet(String value, String fallback) {
			List<String> list = Arrays.asList(StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(
					(value != null) ? this.environment.resolvePlaceholders(value) : fallback)));
			Collections.reverse(list);
			return new LinkedHashSet<>(list);
		}

		/**
		 * This ensures that the order of active profiles in the {@link Environment}
		 * matches the order in which the profiles were processed.
		 * 
		 * @param processedProfiles the processed profiles
		 */
		private void resetEnvironmentProfiles(List<Profile> processedProfiles) {
			String[] names = processedProfiles.stream()
					.filter((profile) -> profile != null && !profile.isDefaultProfile()).map(Profile::getName)
					.toArray(String[]::new);
			this.environment.setActiveProfiles(names);
		}

		private void addLoadedPropertySources() {
//			逻辑很简单,如果environment中有defaultProperties的话,就添加ConfigurationPropertySources到defaultProperties.否则直接进行添加.
//
//		    在笔者的测试环境中,是直接加入到environment中的.因此现在environment中有如下source:
//		    [SimpleCommandLinePropertySource {name=’commandLineArgs’},
			// StubPropertySource {name=’servletConfigInitParams’}, StubPropertySource
			// {name=’servletContextInitParams’}, MapPropertySource
			// {name=’systemProperties’}, SystemEnvironmentPropertySource
			// {name=’systemEnvironment’}, RandomValuePropertySource {name=’random’},
			// ConfigurationPropertySources {name=’applicationConfigurationProperties’}]
			MutablePropertySources destination = this.environment.getPropertySources();
			List<MutablePropertySources> loaded = new ArrayList<>(this.loaded.values());
			Collections.reverse(loaded);
			String lastAdded = null;
			Set<String> added = new HashSet<>();
			for (MutablePropertySources sources : loaded) {
				for (PropertySource<?> source : sources) {
					if (added.add(source.getName())) {
						addLoadedPropertySource(destination, lastAdded, source);
						lastAdded = source.getName();
					}
				}
			}
		}

		private void addLoadedPropertySource(MutablePropertySources destination, String lastAdded,
				PropertySource<?> source) {
			if (lastAdded == null) {
				if (destination.contains(DEFAULT_PROPERTIES)) {
					destination.addBefore(DEFAULT_PROPERTIES, source);
				} else {
					destination.addLast(source);
				}
			} else {
				destination.addAfter(lastAdded, source);
			}
		}

	}

	/**
	 * A Spring Profile that can be loaded.
	 */
	private static class Profile {

		private final String name;

		private final boolean defaultProfile;

		Profile(String name) {
			this(name, false);
		}

		Profile(String name, boolean defaultProfile) {
			Assert.notNull(name, "Name must not be null");
			this.name = name;
			this.defaultProfile = defaultProfile;
		}

		public String getName() {
			return this.name;
		}

		public boolean isDefaultProfile() {
			return this.defaultProfile;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}
			return ((Profile) obj).name.equals(this.name);
		}

		@Override
		public int hashCode() {
			return this.name.hashCode();
		}

		@Override
		public String toString() {
			return this.name;
		}

	}

	/**
	 * Cache key used to save loading the same document multiple times.
	 */
	private static class DocumentsCacheKey {

		private final PropertySourceLoader loader;

		private final Resource resource;

		DocumentsCacheKey(PropertySourceLoader loader, Resource resource) {
			this.loader = loader;
			this.resource = resource;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			DocumentsCacheKey other = (DocumentsCacheKey) obj;
			return this.loader.equals(other.loader) && this.resource.equals(other.resource);
		}

		@Override
		public int hashCode() {
			return this.loader.hashCode() * 31 + this.resource.hashCode();
		}

	}

	/**
	 * A single document loaded by a {@link PropertySourceLoader}.
	 */
	private static class Document {

		private final PropertySource<?> propertySource;

		private String[] profiles;

		private final Set<Profile> activeProfiles;

		private final Set<Profile> includeProfiles;

		Document(PropertySource<?> propertySource, String[] profiles, Set<Profile> activeProfiles,
				Set<Profile> includeProfiles) {
			this.propertySource = propertySource;
			this.profiles = profiles;
			this.activeProfiles = activeProfiles;
			this.includeProfiles = includeProfiles;
		}

		public PropertySource<?> getPropertySource() {
			return this.propertySource;
		}

		public String[] getProfiles() {
			return this.profiles;
		}

		public Set<Profile> getActiveProfiles() {
			return this.activeProfiles;
		}

		public Set<Profile> getIncludeProfiles() {
			return this.includeProfiles;
		}

		@Override
		public String toString() {
			return this.propertySource.toString();
		}

	}

	/**
	 * Factory used to create a {@link DocumentFilter}.
	 */
	@FunctionalInterface
	private interface DocumentFilterFactory {

		/**
		 * Create a filter for the given profile.
		 * 
		 * @param profile the profile or {@code null}
		 * @return the filter
		 */
		DocumentFilter getDocumentFilter(Profile profile);

	}

	/**
	 * Filter used to restrict when a {@link Document} is loaded.
	 */
	@FunctionalInterface
	private interface DocumentFilter {

		boolean match(Document document);

	}

	/**
	 * Consumer used to handle a loaded {@link Document}.
	 */
	@FunctionalInterface
	private interface DocumentConsumer {

		void accept(Profile profile, Document document);

	}

}
