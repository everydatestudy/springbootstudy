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

package org.springframework.web.accept;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import javax.servlet.ServletContext;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.ServletContextAware;

/**顾名思义，它是专门用于来创建一个ContentNegotiationManager的FactoryBean
 * 后缀 > 请求参数 > HTTP首部Accept
 * 
 * Factory to create a {@code ContentNegotiationManager} and configure it with
 * one or more {@link ContentNegotiationStrategy} instances.
 *
 * <p>As of 5.0 you can set the exact strategies to use via
 * {@link #setStrategies(List)}.
 *
 * <p>As an alternative you can also rely on the set of defaults described below
 * which can be turned on or off or customized through the methods of this
 * builder:
 *
 * <table>
 * <tr>
 * <th>Property Setter</th>
 * <th>Underlying Strategy</th>
 * <th>Default Setting</th>
 * </tr>
 * <tr>
 * <td>{@link #setFavorPathExtension}</td>
 * <td>{@link PathExtensionContentNegotiationStrategy Path Extension strategy}</td>
 * <td>On</td>
 * </tr>
 * <tr>
 * <td>{@link #setFavorParameter favorParameter}</td>
 * <td>{@link ParameterContentNegotiationStrategy Parameter strategy}</td>
 * <td>Off</td>
 * </tr>
 * <tr>
 * <td>{@link #setIgnoreAcceptHeader ignoreAcceptHeader}</td>
 * <td>{@link HeaderContentNegotiationStrategy Header strategy}</td>
 * <td>On</td>
 * </tr>
 * <tr>
 * <td>{@link #setDefaultContentType defaultContentType}</td>
 * <td>{@link FixedContentNegotiationStrategy Fixed content strategy}</td>
 * <td>Not set</td>
 * </tr>
 * <tr>
 * <td>{@link #setDefaultContentTypeStrategy defaultContentTypeStrategy}</td>
 * <td>{@link ContentNegotiationStrategy}</td>
 * <td>Not set</td>
 * </tr>
 * </table>
 *
 * <strong>Note:</strong> if you must use URL-based content type resolution,
 * the use of a query parameter is simpler and preferable to the use of a path
 * extension since the latter can cause issues with URI variables, path
 * parameters, and URI decoding. Consider setting {@link #setFavorPathExtension}
 * to {@literal false} or otherwise set the strategies to use explicitly via
 * {@link #setStrategies(List)}.
   *  还实现了ServletContextAware，可以得到当前servlet容器上下文
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.2
 */
public class ContentNegotiationManagerFactoryBean
		implements FactoryBean<ContentNegotiationManager>, ServletContextAware, InitializingBean {

	@Nullable
	private List<ContentNegotiationStrategy> strategies;

	// 默认就是开启了对后缀的支持的
	private boolean favorPathExtension = true;
	// 默认没有开启对param的支持
	private boolean favorParameter = false;
	// 默认也是开启了对Accept的支持的
	private boolean ignoreAcceptHeader = false;

	private Map<String, MediaType> mediaTypes = new HashMap<>();

	private boolean ignoreUnknownPathExtensions = true;

	@Nullable
	private Boolean useRegisteredExtensionsOnly;

	private String parameterName = "format";

	@Nullable
	private ContentNegotiationStrategy defaultNegotiationStrategy;

	@Nullable
	private ContentNegotiationManager contentNegotiationManager;

	@Nullable
	private ServletContext servletContext;


	/**
	 * Set the exact list of strategies to use.
	 * <p><strong>Note:</strong> use of this method is mutually exclusive with
	 * use of all other setters in this class which customize a default, fixed
	 * set of strategies. See class level doc for more details.
	 * @param strategies the strategies to use
	 * @since 5.0
	 */
	public void setStrategies(@Nullable List<ContentNegotiationStrategy> strategies) {
		this.strategies = (strategies != null ? new ArrayList<>(strategies) : null);
	}

	/**
	 * Whether the path extension in the URL path should be used to determine
	 * the requested media type.
	 * <p>By default this is set to {@code true} in which case a request
	 * for {@code /hotels.pdf} will be interpreted as a request for
	 * {@code "application/pdf"} regardless of the 'Accept' header.
	 */
	public void setFavorPathExtension(boolean favorPathExtension) {
		this.favorPathExtension = favorPathExtension;
	}

	/**注意这里传入的是：Properties  表示后缀和MediaType的对应关系
	 * Add a mapping from a key, extracted from a path extension or a query
	 * parameter, to a MediaType. This is required in order for the parameter
	 * strategy to work. Any extensions explicitly registered here are also
	 * whitelisted for the purpose of Reflected File Download attack detection
	 * (see Spring Framework reference documentation for more details on RFD
	 * attack protection).
	 * <p>The path extension strategy will also try to use
	 * {@link ServletContext#getMimeType} and
	 * {@link org.springframework.http.MediaTypeFactory} to resolve path extensions.
	 * @param mediaTypes media type mappings
	 * @see #addMediaType(String, MediaType)
	 * @see #addMediaTypes(Map)
	 */
	public void setMediaTypes(Properties mediaTypes) {
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			mediaTypes.forEach((key, value) -> {
				String extension = ((String) key).toLowerCase(Locale.ENGLISH);
				MediaType mediaType = MediaType.valueOf((String) value);
				this.mediaTypes.put(extension, mediaType);
			});
		}
	}

	/**
	 * An alternative to {@link #setMediaTypes} for use in Java code.
	 * @see #setMediaTypes
	 * @see #addMediaTypes
	 */
	public void addMediaType(String fileExtension, MediaType mediaType) {
		this.mediaTypes.put(fileExtension, mediaType);
	}

	/**
	 * An alternative to {@link #setMediaTypes} for use in Java code.
	 * @see #setMediaTypes
	 * @see #addMediaType
	 */
	public void addMediaTypes(@Nullable Map<String, MediaType> mediaTypes) {
		if (mediaTypes != null) {
			this.mediaTypes.putAll(mediaTypes);
		}
	}

	/**
	 * Whether to ignore requests with path extension that cannot be resolved
	 * to any media type. Setting this to {@code false} will result in an
	 * {@code HttpMediaTypeNotAcceptableException} if there is no match.
	 * <p>By default this is set to {@code true}.
	 */
	public void setIgnoreUnknownPathExtensions(boolean ignore) {
		this.ignoreUnknownPathExtensions = ignore;
	}

	/**
	 * @deprecated as of 5.0, in favor of {@link #setUseRegisteredExtensionsOnly(boolean)}, which
	 * has reverse behavior.
	 */
	@Deprecated
	public void setUseJaf(boolean useJaf) {
		setUseRegisteredExtensionsOnly(!useJaf);
	}

	/**
	 * When {@link #setFavorPathExtension favorPathExtension} or
	 * {@link #setFavorParameter(boolean)} is set, this property determines
	 * whether to use only registered {@code MediaType} mappings or to allow
	 * dynamic resolution, e.g. via {@link MediaTypeFactory}.
	 * <p>By default this is not set in which case dynamic resolution is on.
	 */
	public void setUseRegisteredExtensionsOnly(boolean useRegisteredExtensionsOnly) {
		this.useRegisteredExtensionsOnly = useRegisteredExtensionsOnly;
	}

	private boolean useRegisteredExtensionsOnly() {
		return (this.useRegisteredExtensionsOnly != null && this.useRegisteredExtensionsOnly);
	}

	/**
	 * Whether a request parameter ("format" by default) should be used to
	 * determine the requested media type. For this option to work you must
	 * register {@link #setMediaTypes media type mappings}.
	 * <p>By default this is set to {@code false}.
	 * @see #setParameterName
	 */
	public void setFavorParameter(boolean favorParameter) {
		this.favorParameter = favorParameter;
	}

	/**
	 * Set the query parameter name to use when {@link #setFavorParameter} is on.
	 * <p>The default parameter name is {@code "format"}.
	 */
	public void setParameterName(String parameterName) {
		Assert.notNull(parameterName, "parameterName is required");
		this.parameterName = parameterName;
	}

	/**
	 * Whether to disable checking the 'Accept' request header.
	 * <p>By default this value is set to {@code false}.
	 */
	public void setIgnoreAcceptHeader(boolean ignoreAcceptHeader) {
		this.ignoreAcceptHeader = ignoreAcceptHeader;
	}

	/**
	 * Set the default content type to use when no content type is requested.
	 * <p>By default this is not set.
	 * @see #setDefaultContentTypeStrategy
	 */
	public void setDefaultContentType(MediaType contentType) {
		this.defaultNegotiationStrategy = new FixedContentNegotiationStrategy(contentType);
	}

	/**
	 * Set the default content types to use when no content type is requested.
	 * <p>By default this is not set.
	 * @see #setDefaultContentTypeStrategy
	 * @since 5.0
	 */
	public void setDefaultContentTypes(List<MediaType> contentTypes) {
		this.defaultNegotiationStrategy = new FixedContentNegotiationStrategy(contentTypes);
	}

	/**
	 * Set a custom {@link ContentNegotiationStrategy} to use to determine
	 * the content type to use when no content type is requested.
	 * <p>By default this is not set.
	 * @see #setDefaultContentType
	 * @since 4.1.2
	 */
	public void setDefaultContentTypeStrategy(ContentNegotiationStrategy strategy) {
		this.defaultNegotiationStrategy = strategy;
	}

	/**
	 * Invoked by Spring to inject the ServletContext.
	 */
	@Override
	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	// 这里面处理了很多默认逻辑
	@Override
	public void afterPropertiesSet() {
		build();
	}

	/**
	 * Actually build the {@link ContentNegotiationManager}.
	 * @since 5.0
	 */
	public ContentNegotiationManager build() {
		List<ContentNegotiationStrategy> strategies = new ArrayList<>();

		if (this.strategies != null) {
			strategies.addAll(this.strategies);
		}
		else {
			// 默认favorPathExtension=true，所以是支持path后缀模式的
			// servlet环境使用的是ServletPathExtensionContentNegotiationStrategy，否则使用的是PathExtensionContentNegotiationStrategy
			if (this.favorPathExtension) {
				PathExtensionContentNegotiationStrategy strategy;
				if (this.servletContext != null && !useRegisteredExtensionsOnly()) {
					strategy = new ServletPathExtensionContentNegotiationStrategy(this.servletContext, this.mediaTypes);
				}
				else {
					strategy = new PathExtensionContentNegotiationStrategy(this.mediaTypes);
				}
				strategy.setIgnoreUnknownExtensions(this.ignoreUnknownPathExtensions);
				if (this.useRegisteredExtensionsOnly != null) {
					strategy.setUseRegisteredExtensionsOnly(this.useRegisteredExtensionsOnly);
				}
				strategies.add(strategy);
			}
			// 默认favorParameter=false 木有开启滴
			if (this.favorParameter) {
				ParameterContentNegotiationStrategy strategy = new ParameterContentNegotiationStrategy(this.mediaTypes);
				strategy.setParameterName(this.parameterName);
				if (this.useRegisteredExtensionsOnly != null) {
					strategy.setUseRegisteredExtensionsOnly(this.useRegisteredExtensionsOnly);
				}
				else {
					strategy.setUseRegisteredExtensionsOnly(true);  // backwards compatibility
				}
				strategies.add(strategy);
			}
			// 注意这前面有个!，所以默认Accept也是支持的
			if (!this.ignoreAcceptHeader) {
				strategies.add(new HeaderContentNegotiationStrategy());
			}
			// 若你喜欢，你可以设置一个defaultNegotiationStrategy  最终也会被add进去
			if (this.defaultNegotiationStrategy != null) {
				strategies.add(this.defaultNegotiationStrategy);
			}
		}
		// 这部分我需要提醒注意的是：这里使用的是ArrayList，所以你add的顺序就是u最后的执行顺序
		// 所以若你指定了defaultNegotiationStrategy，它也是放到最后的
		this.contentNegotiationManager = new ContentNegotiationManager(strategies);
		return this.contentNegotiationManager;
	}

	// 三个接口方法
	@Override
	@Nullable
	public ContentNegotiationManager getObject() {
		return this.contentNegotiationManager;
	}

	@Override
	public Class<?> getObjectType() {
		return ContentNegotiationManager.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
