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

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ansi.AnsiPropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * Banner implementation that prints from a source text {@link Resource}.
 *
 * @author Phillip Webb
 * @author Vedran Pavic
 * @since 1.2.0
 */
public class ResourceBanner implements Banner {

	private static final Log logger = LogFactory.getLog(ResourceBanner.class);

	private Resource resource;

	public ResourceBanner(Resource resource) {
		Assert.notNull(resource, "Resource must not be null");
		Assert.isTrue(resource.exists(), "Resource must exist");
		this.resource = resource;
	}
	//	还是3步:
	//	    获取resource中的输入流，并将其转化为字符串 通过environment获取banner.charset变量，如果不存在，则默认使用UTF-8编码
	//	    循环遍历所有的PropertyResolver 去解析banner中配置的spel表达式.
	//	    首先通过getPropertyResolvers 获得所有的PropertyResolver.
	@Override
	public void printBanner(Environment environment, Class<?> sourceClass,
			PrintStream out) {
		try {
			  // 1. 获取resource中的输入流，并将其转化为字符串 通过environment获取banner.charset变量，如果不存在，则默认使用UTF-8编码
			String banner = StreamUtils.copyToString(this.resource.getInputStream(),
					environment.getProperty("spring.banner.charset", Charset.class,
							StandardCharsets.UTF_8));
			// 2. 循环遍历所有的PropertyResolver 去解析banner中配置的spel表达式
			for (PropertyResolver resolver : getPropertyResolvers(environment,
					sourceClass)) {
				banner = resolver.resolvePlaceholders(banner);
			}
			out.println(banner);
		}
		catch (Exception ex) {
			logger.warn("Banner not printable: " + this.resource + " (" + ex.getClass()
					+ ": '" + ex.getMessage() + "')", ex);
		}
	}

	protected List<PropertyResolver> getPropertyResolvers(Environment environment,
			Class<?> sourceClass) {
	    // 1. 实例化resolvers集合，并添加environment元素，Environment接口继承自PropertyResolver接口
		List<PropertyResolver> resolvers = new ArrayList<>();
		resolvers.add(environment);
	    // 2. 调用getVersionResolver(sourceClass)方法并将其返回值添加到resolvers集合
		resolvers.add(getVersionResolver(sourceClass));
	    // 3. 调用getAnsiResolver(sourceClass)方法并将其返回值添加到resolvers集合 直接设置开启了ansi
		resolvers.add(getAnsiResolver());
	    // 4. 调用getTitleResolver(sourceClass)方法并将其返回值添加到resolvers集合
		resolvers.add(getTitleResolver(sourceClass));
		return resolvers;
	}
//	实例化resolvers集合，并添加environment元素，Environment接口继承自PropertyResolver接口
//
//	调用getVersionResolver(sourceClass)方法并将其返回值添加到resolvers集合
	private PropertyResolver getVersionResolver(Class<?> sourceClass) {
		MutablePropertySources propertySources = new MutablePropertySources();
		propertySources
				.addLast(new MapPropertySource("version", getVersionsMap(sourceClass)));
		return new PropertySourcesPropertyResolver(propertySources);
	}

	private Map<String, Object> getVersionsMap(Class<?> sourceClass) {
		String appVersion = getApplicationVersion(sourceClass);
		String bootVersion = getBootVersion();
		Map<String, Object> versions = new HashMap<>();
		versions.put("application.version", getVersionString(appVersion, false));
		versions.put("spring-boot.version", getVersionString(bootVersion, false));
		versions.put("application.formatted-version", getVersionString(appVersion, true));
		versions.put("spring-boot.formatted-version",
				getVersionString(bootVersion, true));
		return versions;
	}

//    首先通过调用getApplicationVersion方法获得appVersion.其是通过获取sourceClass所在包的版本号. sourceClass为应用的启动类
//    获取Boot版本号.同样是通过获得SpringApplication所在包的版本号完成的
//    在map中存入数据.
//
//该方法最终的数据为:
//
//{application.formatted-version=, application.version=, spring-boot.formatted-version=, spring-boot.version=}
// 
	protected String getApplicationVersion(Class<?> sourceClass) {
		Package sourcePackage = (sourceClass != null) ? sourceClass.getPackage() : null;
		return (sourcePackage != null) ? sourcePackage.getImplementationVersion() : null;
	}

	protected String getBootVersion() {
		return SpringBootVersion.getVersion();
	}

	private String getVersionString(String version, boolean format) {
		if (version == null) {
			return "";
		}
		return format ? " (v" + version + ")" : version;
	}

	private PropertyResolver getAnsiResolver() {
		MutablePropertySources sources = new MutablePropertySources();
		sources.addFirst(new AnsiPropertySource("ansi", true));
		return new PropertySourcesPropertyResolver(sources);
	}

	private PropertyResolver getTitleResolver(Class<?> sourceClass) {
		MutablePropertySources sources = new MutablePropertySources();
		String applicationTitle = getApplicationTitle(sourceClass);
		Map<String, Object> titleMap = Collections.singletonMap("application.title",
				(applicationTitle != null) ? applicationTitle : "");
		sources.addFirst(new MapPropertySource("title", titleMap));
		return new PropertySourcesPropertyResolver(sources);
	}

	protected String getApplicationTitle(Class<?> sourceClass) {
		Package sourcePackage = (sourceClass != null) ? sourceClass.getPackage() : null;
		return (sourcePackage != null) ? sourcePackage.getImplementationTitle() : null;
	}

}
