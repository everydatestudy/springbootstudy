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

package org.springframework.web.cors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * A container for CORS configuration along with methods to check against the
 * actual origin, HTTP methods, and headers of a given request.
 *
 * <p>By default a newly created {@code CorsConfiguration} does not permit any
 * cross-origin requests and must be configured explicitly to indicate what
 * should be allowed. Use {@link #applyPermitDefaultValues()} to flip the
 * initialization model to start with open defaults that permit all cross-origin
 * requests for GET, HEAD, and POST requests.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.2
 * @see <a href="http://www.w3.org/TR/cors/">CORS spec</a>
 */
//它代表一个cors配置，记录着各种配置项。它还提供了检查给定请求的实际来源、http方法和头的方法供以调用。用人话说：它就是具体封装跨域配置信息的pojo。
//默认情况下新创建的CorsConfiguration它是不允许任何跨域请求的，需要你手动去配置，或者调用applyPermitDefaultValues()开启GET、POST、Head的支持~
//
//几乎所有场景，创建完CorsConfiguration最后都调用了applyPermitDefaultValues()方法。也就是说你不干预的情况下，一个CorsConfiguration配置一般都是支持GET、POST、Head的
//————————————————
//版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/f641385712/article/details/101036506
public class CorsConfiguration {

	/** Wildcard representing <em>all</em> origins, methods, or headers. */
	// public的通配符：代表所有的源、方法、headers...
	// 若你需要使用通配符，可以使用此静态常量
	public static final String ALL = "*";

	private static final List<HttpMethod> DEFAULT_METHODS =
			Collections.unmodifiableList(Arrays.asList(HttpMethod.GET, HttpMethod.HEAD));
	// 默认许可所有方法
	private static final List<String> DEFAULT_PERMIT_ALL =
			Collections.unmodifiableList(Arrays.asList(ALL));
	// 默认许可这三个方法
	private static final List<String> DEFAULT_PERMIT_METHODS =
			Collections.unmodifiableList(Arrays.asList(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name()));

	// ==========把这些属性对应上文讲述的响应头们对应，和W3C标注都是对应上的=========
	@Nullable
	private List<String> allowedOrigins;

	@Nullable
	private List<String> allowedMethods;

	@Nullable
	private List<HttpMethod> resolvedMethods = DEFAULT_METHODS;

	@Nullable
	private List<String> allowedHeaders;

	@Nullable
	private List<String> exposedHeaders;

	@Nullable
	private Boolean allowCredentials;

	@Nullable
	private Long maxAge;


	/**
	 * Construct a new {@code CorsConfiguration} instance with no cross-origin
	 * requests allowed for any origin by default.
	 * @see #applyPermitDefaultValues()
	 */
	public CorsConfiguration() {
	}

	/**
	 * Construct a new {@code CorsConfiguration} instance by copying all
	 * values from the supplied {@code CorsConfiguration}.
	 */
	public CorsConfiguration(CorsConfiguration other) {
		this.allowedOrigins = other.allowedOrigins;
		this.allowedMethods = other.allowedMethods;
		this.resolvedMethods = other.resolvedMethods;
		this.allowedHeaders = other.allowedHeaders;
		this.exposedHeaders = other.exposedHeaders;
		this.allowCredentials = other.allowCredentials;
		this.maxAge = other.maxAge;
	}


	/**
	 * Set the origins to allow, e.g. {@code "http://domain1.com"}.
	 * <p>The special value {@code "*"} allows all domains.
	 * <p>By default this is not set.
	 */
	public void setAllowedOrigins(@Nullable List<String> allowedOrigins) {
		this.allowedOrigins = (allowedOrigins != null ? new ArrayList<>(allowedOrigins) : null);
	}

	/**
	 * Return the configured origins to allow, or {@code null} if none.
	 * @see #addAllowedOrigin(String)
	 * @see #setAllowedOrigins(List)
	 */
	@Nullable
	public List<String> getAllowedOrigins() {
		return this.allowedOrigins;
	}

	/**
	 * Add an origin to allow.
	 */
	public void addAllowedOrigin(String origin) {
		if (this.allowedOrigins == null) {
			this.allowedOrigins = new ArrayList<>(4);
		}
		else if (this.allowedOrigins == DEFAULT_PERMIT_ALL) {
			setAllowedOrigins(DEFAULT_PERMIT_ALL);
		}
		this.allowedOrigins.add(origin);
	}

	/**
	 * Set the HTTP methods to allow, e.g. {@code "GET"}, {@code "POST"},
	 * {@code "PUT"}, etc.
	 * <p>The special value {@code "*"} allows all methods.
	 * <p>If not set, only {@code "GET"} and {@code "HEAD"} are allowed.
	 * <p>By default this is not set.
	 * <p><strong>Note:</strong> CORS checks use values from "Forwarded"
	 * (<a href="http://tools.ietf.org/html/rfc7239">RFC 7239</a>),
	 * "X-Forwarded-Host", "X-Forwarded-Port", and "X-Forwarded-Proto" headers,
	 * if present, in order to reflect the client-originated address.
	 * Consider using the {@code ForwardedHeaderFilter} in order to choose from a
	 * central place whether to extract and use, or to discard such headers.
	 * See the Spring Framework reference for more on this filter.
	 */
	public void setAllowedMethods(@Nullable List<String> allowedMethods) {
		this.allowedMethods = (allowedMethods != null ? new ArrayList<>(allowedMethods) : null);
		if (!CollectionUtils.isEmpty(allowedMethods)) {
			this.resolvedMethods = new ArrayList<>(allowedMethods.size());
			for (String method : allowedMethods) {
				if (ALL.equals(method)) {
					this.resolvedMethods = null;
					break;
				}
				this.resolvedMethods.add(HttpMethod.resolve(method));
			}
		}
		else {
			this.resolvedMethods = DEFAULT_METHODS;
		}
	}

	/**
	 * Return the allowed HTTP methods, or {@code null} in which case
	 * only {@code "GET"} and {@code "HEAD"} allowed.
	 * @see #addAllowedMethod(HttpMethod)
	 * @see #addAllowedMethod(String)
	 * @see #setAllowedMethods(List)
	 */
	@Nullable
	public List<String> getAllowedMethods() {
		return this.allowedMethods;
	}

	/**
	 * Add an HTTP method to allow.
	 */
	public void addAllowedMethod(HttpMethod method) {
		addAllowedMethod(method.name());
	}

	/**
	 * Add an HTTP method to allow.
	 */
	public void addAllowedMethod(String method) {
		if (StringUtils.hasText(method)) {
			if (this.allowedMethods == null) {
				this.allowedMethods = new ArrayList<>(4);
				this.resolvedMethods = new ArrayList<>(4);
			}
			else if (this.allowedMethods == DEFAULT_PERMIT_METHODS) {
				setAllowedMethods(DEFAULT_PERMIT_METHODS);
			}
			this.allowedMethods.add(method);
			if (ALL.equals(method)) {
				this.resolvedMethods = null;
			}
			else if (this.resolvedMethods != null) {
				this.resolvedMethods.add(HttpMethod.resolve(method));
			}
		}
	}

	/**
	 * Set the list of headers that a pre-flight request can list as allowed
	 * for use during an actual request.
	 * <p>The special value {@code "*"} allows actual requests to send any
	 * header.
	 * <p>A header name is not required to be listed if it is one of:
	 * {@code Cache-Control}, {@code Content-Language}, {@code Expires},
	 * {@code Last-Modified}, or {@code Pragma}.
	 * <p>By default this is not set.
	 */
	public void setAllowedHeaders(@Nullable List<String> allowedHeaders) {
		this.allowedHeaders = (allowedHeaders != null ? new ArrayList<>(allowedHeaders) : null);
	}

	/**
	 * Return the allowed actual request headers, or {@code null} if none.
	 * @see #addAllowedHeader(String)
	 * @see #setAllowedHeaders(List)
	 */
	@Nullable
	public List<String> getAllowedHeaders() {
		return this.allowedHeaders;
	}

	/**
	 * Add an actual request header to allow.
	 */
	public void addAllowedHeader(String allowedHeader) {
		if (this.allowedHeaders == null) {
			this.allowedHeaders = new ArrayList<>(4);
		}
		else if (this.allowedHeaders == DEFAULT_PERMIT_ALL) {
			setAllowedHeaders(DEFAULT_PERMIT_ALL);
		}
		this.allowedHeaders.add(allowedHeader);
	}

	/**
	 * Set the list of response headers other than simple headers (i.e.
	 * {@code Cache-Control}, {@code Content-Language}, {@code Content-Type},
	 * {@code Expires}, {@code Last-Modified}, or {@code Pragma}) that an
	 * actual response might have and can be exposed.
	 * <p>Note that {@code "*"} is not a valid exposed header value.
	 * <p>By default this is not set.
	 */
	public void setExposedHeaders(@Nullable List<String> exposedHeaders) {
		if (exposedHeaders != null && exposedHeaders.contains(ALL)) {
			throw new IllegalArgumentException("'*' is not a valid exposed header value");
		}
		this.exposedHeaders = (exposedHeaders != null ? new ArrayList<>(exposedHeaders) : null);
	}

	/**
	 * Return the configured response headers to expose, or {@code null} if none.
	 * @see #addExposedHeader(String)
	 * @see #setExposedHeaders(List)
	 */
	@Nullable
	public List<String> getExposedHeaders() {
		return this.exposedHeaders;
	}

	/**
	 * Add a response header to expose.
	 * <p>Note that {@code "*"} is not a valid exposed header value.
	 */
	public void addExposedHeader(String exposedHeader) {
		if (ALL.equals(exposedHeader)) {
			throw new IllegalArgumentException("'*' is not a valid exposed header value");
		}
		if (this.exposedHeaders == null) {
			this.exposedHeaders = new ArrayList<>(4);
		}
		this.exposedHeaders.add(exposedHeader);
	}

	/**
	 * Whether user credentials are supported.
	 * <p>By default this is not set (i.e. user credentials are not supported).
	 */
	public void setAllowCredentials(@Nullable Boolean allowCredentials) {
		this.allowCredentials = allowCredentials;
	}

	/**
	 * Return the configured {@code allowCredentials} flag, or {@code null} if none.
	 * @see #setAllowCredentials(Boolean)
	 */
	@Nullable
	public Boolean getAllowCredentials() {
		return this.allowCredentials;
	}

	/**
	 * Configure how long, in seconds, the response from a pre-flight request
	 * can be cached by clients.
	 * <p>By default this is not set.
	 */
	public void setMaxAge(@Nullable Long maxAge) {
		this.maxAge = maxAge;
	}

	/**
	 * Return the configured {@code maxAge} value, or {@code null} if none.
	 * @see #setMaxAge(Long)
	 */
	@Nullable
	public Long getMaxAge() {
		return this.maxAge;
	}

	/**
	 * By default a newly created {@code CorsConfiguration} does not permit any
	 * cross-origin requests and must be configured explicitly to indicate what
	 * should be allowed.
	 *
	 * <p>Use this method to flip the initialization model to start with open
	 * defaults that permit all cross-origin requests for GET, HEAD, and POST
	 * requests. Note however that this method will not override any existing
	 * values already set.
	 *
	 * <p>The following defaults are applied if not already set:
	 * <ul>
	 *     <li>Allow all origins.</li>
	 *     <li>Allow "simple" methods {@code GET}, {@code HEAD} and {@code POST}.</li>
	 *     <li>Allow all headers.</li>
	 *     <li>Set max age to 1800 seconds (30 minutes).</li>
	 * </ul>
	 */
	// 使用此方法将初始化模型翻转为以允许get、head和post请求的所有跨源请求的打开默认值开始
	// 注意：此方法不会覆盖前面set进去的值，所以建议此方法可以作为兜底调用。实际上Spring内部也是用它兜底的
	public CorsConfiguration applyPermitDefaultValues() {
		if (this.allowedOrigins == null) {
			this.allowedOrigins = DEFAULT_PERMIT_ALL;
		}
		if (this.allowedMethods == null) {
			this.allowedMethods = DEFAULT_PERMIT_METHODS;
			this.resolvedMethods = DEFAULT_PERMIT_METHODS
					.stream().map(HttpMethod::resolve).collect(Collectors.toList());
		}
		if (this.allowedHeaders == null) {
			this.allowedHeaders = DEFAULT_PERMIT_ALL;
		}
		if (this.maxAge == null) {
			this.maxAge = 1800L;
		}
		return this;
	}

	/**
	 * Combine the non-null properties of the supplied
	 * {@code CorsConfiguration} with this one.
	 *
	 * <p>When combining single values like {@code allowCredentials} or
	 * {@code maxAge}, {@code this} properties are overridden by non-null
	 * {@code other} properties if any.
	 *
	 * <p>Combining lists like {@code allowedOrigins}, {@code allowedMethods},
	 * {@code allowedHeaders} or {@code exposedHeaders} is done in an additive
	 * way. For example, combining {@code ["GET", "POST"]} with
	 * {@code ["PATCH"]} results in {@code ["GET", "POST", "PATCH"]}, but keep
	 * in mind that combining {@code ["GET", "POST"]} with {@code ["*"]}
	 * results in {@code ["*"]}.
	 *
	 * <p>Notice that default permit values set by
	 * {@link CorsConfiguration#applyPermitDefaultValues()} are overridden by
	 * any value explicitly defined.
	 *
	 * @return the combined {@code CorsConfiguration} or {@code this}
	 * configuration if the supplied configuration is {@code null}
	 */
	@Nullable
	public CorsConfiguration combine(@Nullable CorsConfiguration other) {
		if (other == null) {
			return this;
		}
		CorsConfiguration config = new CorsConfiguration(this);
		config.setAllowedOrigins(combine(getAllowedOrigins(), other.getAllowedOrigins()));
		config.setAllowedMethods(combine(getAllowedMethods(), other.getAllowedMethods()));
		config.setAllowedHeaders(combine(getAllowedHeaders(), other.getAllowedHeaders()));
		config.setExposedHeaders(combine(getExposedHeaders(), other.getExposedHeaders()));
		Boolean allowCredentials = other.getAllowCredentials();
		if (allowCredentials != null) {
			config.setAllowCredentials(allowCredentials);
		}
		Long maxAge = other.getMaxAge();
		if (maxAge != null) {
			config.setMaxAge(maxAge);
		}
		return config;
	}

	private List<String> combine(@Nullable List<String> source, @Nullable List<String> other) {
		if (other == null) {
			return (source != null ? source : Collections.emptyList());
		}
		if (source == null) {
			return other;
		}
		if (source == DEFAULT_PERMIT_ALL || source == DEFAULT_PERMIT_METHODS) {
			return other;
		}
		if (other == DEFAULT_PERMIT_ALL || other == DEFAULT_PERMIT_METHODS) {
			return source;
		}
		if (source.contains(ALL) || other.contains(ALL)) {
			return new ArrayList<>(Collections.singletonList(ALL));
		}
		Set<String> combined = new LinkedHashSet<>(source);
		combined.addAll(other);
		return new ArrayList<>(combined);
	}
	// 根据配置的允许来源检查请求的来源
		// 返回值并不是bool值，而是字符串--> 返回可用的origin。若是null表示请求的origin不被支持
	/**
	 * Check the origin of the request against the configured allowed origins.
	 * @param requestOrigin the origin to check
	 * @return the origin to use for the response, or {@code null} which
	 * means the request origin is not allowed
	 */
	@Nullable
	public String checkOrigin(@Nullable String requestOrigin) {
		if (!StringUtils.hasText(requestOrigin)) {
			return null;
		}
		if (ObjectUtils.isEmpty(this.allowedOrigins)) {
			return null;
		}

		if (this.allowedOrigins.contains(ALL)) {
			if (this.allowCredentials != Boolean.TRUE) {
				return ALL;
			}
			else {
				return requestOrigin;
			}
		}
		for (String allowedOrigin : this.allowedOrigins) {
			if (requestOrigin.equalsIgnoreCase(allowedOrigin)) {
				return requestOrigin;
			}
		}

		return null;
	}

	/** 检查预检请求的Access-Control-Request-Method这个请求头
	 * Check the HTTP request method (or the method from the
	 * {@code Access-Control-Request-Method} header on a pre-flight request)
	 * against the configured allowed methods.
	 * @param requestMethod the HTTP request method to check
	 * @return the list of HTTP methods to list in the response of a pre-flight
	 * request, or {@code null} if the supplied {@code requestMethod} is not allowed
	 */
	@Nullable
	public List<HttpMethod> checkHttpMethod(@Nullable HttpMethod requestMethod) {
		if (requestMethod == null) {
			return null;
		}
		if (this.resolvedMethods == null) {
			return Collections.singletonList(requestMethod);
		}
		return (this.resolvedMethods.contains(requestMethod) ? this.resolvedMethods : null);
	}

	/**检查预检请求的Access-Control-Request-Headers
	 * Check the supplied request headers (or the headers listed in the
	 * {@code Access-Control-Request-Headers} of a pre-flight request) against
	 * the configured allowed headers.
	 * @param requestHeaders the request headers to check
	 * @return the list of allowed headers to list in the response of a pre-flight
	 * request, or {@code null} if none of the supplied request headers is allowed
	 */
	@Nullable
	public List<String> checkHeaders(@Nullable List<String> requestHeaders) {
		if (requestHeaders == null) {
			return null;
		}
		if (requestHeaders.isEmpty()) {
			return Collections.emptyList();
		}
		if (ObjectUtils.isEmpty(this.allowedHeaders)) {
			return null;
		}

		boolean allowAnyHeader = this.allowedHeaders.contains(ALL);
		List<String> result = new ArrayList<>(requestHeaders.size());
		for (String requestHeader : requestHeaders) {
			if (StringUtils.hasText(requestHeader)) {
				requestHeader = requestHeader.trim();
				if (allowAnyHeader) {
					result.add(requestHeader);
				}
				else {
					for (String allowedHeader : this.allowedHeaders) {
						if (requestHeader.equalsIgnoreCase(allowedHeader)) {
							result.add(requestHeader);
							break;
						}
					}
				}
			}
		}
		return (result.isEmpty() ? null : result);
	}

}
