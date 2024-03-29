/*
 * Copyright 2002-2017 the original author or authors.
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

import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * A {@code ContentNegotiationStrategy} that resolves the file extension in the
 * request path to a key to be used to look up a media type.
 *
 * <p>If the file extension is not found in the explicit registrations provided
 * to the constructor, the {@link MediaTypeFactory} is used as a fallback
 * mechanism.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public class PathExtensionContentNegotiationStrategy extends AbstractMappingContentNegotiationStrategy {

	private UrlPathHelper urlPathHelper = new UrlPathHelper();


	/**
	 * Create an instance without any mappings to start with. Mappings may be added
	 * later on if any extensions are resolved through the Java Activation framework.
	 */
	public PathExtensionContentNegotiationStrategy() {
		this(null);
	}

	/**
	 * Create an instance with the given map of file extensions and media types.
	 */
	public PathExtensionContentNegotiationStrategy(@Nullable Map<String, MediaType> mediaTypes) {
		super(mediaTypes);
		setUseRegisteredExtensionsOnly(false);
		 // 注意：这个值设置为了true
		setIgnoreUnknownExtensions(true);
		// 不需要解码（url请勿有中文）
		this.urlPathHelper.setUrlDecode(false);
	}


	/**
	 * Configure a {@code UrlPathHelper} to use in {@link #getMediaTypeKey}
	 * in order to derive the lookup path for a target request URL path.
	 * @since 4.2.8
	 */
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		this.urlPathHelper = urlPathHelper;
	}

	/**
	 * @deprecated as of 5.0, in favor of {@link #setUseRegisteredExtensionsOnly(boolean)}.
	 */
	@Deprecated
	public void setUseJaf(boolean useJaf) {
		setUseRegisteredExtensionsOnly(!useJaf);
	}
	// 子类ServletPathExtensionContentNegotiationStrategy有使用和复写
		// 它的作用是面向Resource找到这个资源对应的MediaType ~
	@Override
	@Nullable
	protected String getMediaTypeKey(NativeWebRequest webRequest) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		if (request == null) {
			logger.warn("An HttpServletRequest is required to determine the media type key");
			return null;
		}
		String path = this.urlPathHelper.getLookupPathForRequest(request);
		String extension = UriUtils.extractFileExtension(path);
		return (StringUtils.hasText(extension) ? extension.toLowerCase(Locale.ENGLISH) : null);
	}

	/**
	 * // 子类ServletPathExtensionContentNegotiationStrategy有使用和复写
	// 它的作用是面向Resource找到这个资源对应的MediaType ~
	 * A public method exposing the knowledge of the path extension strategy to
	 * resolve file extensions to a {@link MediaType} in this case for a given
	 * {@link Resource}. The method first looks up any explicitly registered
	 * file extensions first and then falls back on {@link MediaTypeFactory} if available.
	 * @param resource the resource to look up
	 * @return the MediaType for the extension, or {@code null} if none found
	 * @since 4.3
	 */
	@Nullable
	public MediaType getMediaTypeForResource(Resource resource) {
		Assert.notNull(resource, "Resource must not be null");
		MediaType mediaType = null;
		String filename = resource.getFilename();
		String extension = StringUtils.getFilenameExtension(filename);
		if (extension != null) {
			mediaType = lookupMediaType(extension);
		}
		if (mediaType == null) {
			mediaType = MediaTypeFactory.getMediaType(filename).orElse(null);
		}
		return mediaType;
	}

}
