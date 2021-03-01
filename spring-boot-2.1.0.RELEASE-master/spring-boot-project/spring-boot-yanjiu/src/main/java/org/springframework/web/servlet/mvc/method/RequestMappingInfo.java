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

package org.springframework.web.servlet.mvc.method;

import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.ConsumesRequestCondition;
import org.springframework.web.servlet.mvc.condition.HeadersRequestCondition;
import org.springframework.web.servlet.mvc.condition.ParamsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.ProducesRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestConditionHolder;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.util.UrlPathHelper;

/**
 *  一共有六项配置，可以看出这些配置项是用于限定被注解的方法对象可以处理哪些类型的request请求。
 * 当spring启动过程中创建处理器映射对象时，会寻找所有被@Controller注解的类中被@RequestMapping注解的方法对象，
 * 然后解析方法对象的@RequestMapping注解，把解析结果封装成RequestMappingInfo对象，
 * 也就是说RequestMappingInfo对象是用来装载请求处理方法的配置信息的，
 * 每个请求处理方法对象都会对应一个RequestMappingInfo对象。现在大家应该能明白RequestMappingInfo的作用了吧
 
 1. PatternsRequestCondition(模式请求路径过滤器)

a. 创建跟请求匹配的过滤器对象

       从持有的模式请求路径列表中筛选出同request请求匹配的模式请求路径，可能会有多个，对筛选后的模式请求路径列表执行排序，最详细最具体的路径排在前面，然后使用过滤后的模式请求列表创建一个新的过滤器并返回。多个模式请求路径之间是或关系，只要有一个模式请求路径跟request请求匹配，就认为过滤对象跟request请求匹配。

b.组合两个过滤条件对象 

       对两个模式请求路径列表中的元素进行自然连接后，再执行简单拼接, 像这样， 


Pattern 1	Pattern 2	Result
/hotels	null	/hotels
null	/hotels	/hotels
/hotels	/bookings	/hotels/bookings
/hotels	bookings	/hotels/bookings
/hotels/*	/bookings	/hotels/bookings
/hotels/**	/bookings	"/hotels/**\/bookings"
然后创建新的模式请求路径过滤条件对象, 并返回.
c.比较过滤条件对象优先级规则是谁的模式请求路径跟请求路径匹配度更高.
————————————————
2. ParamsRequestCondition(请求参数过滤器)

a. 创建跟请求匹配的过滤条件对象

       判断所有的参数表达式是否都匹配request请求对象, 是则返回本过滤对象, 否则返回null，认为过滤对象不匹配request请求。

b.组合两个过滤条件对象

  把两个过滤对象的参数表达式集合累加起来,  然后创建新的请求参数过滤对象, 并返回。

c.比较过滤条件对象优先级

  参数表达式个数多的那个过滤器优先级高。

3. HeadersRequestCondition(头字段过滤器)
a. 创建跟请求匹配的过滤条件对象

       判断所有的头字段表达式是否都匹配请求对象, 是则返回本过滤器，否则返回null，认为过滤器不匹配request请求。

b.组合两个过滤条件对象

把两个过滤对象的头字段表达式集合累加起来,  然后创建新的过滤对象, 并返回。

c.比较过滤条件对象优先级

头字段表达式个数多的过滤器优先级高。

4.RequestMethodsRequestCondition (请求方法过滤器)

a. 创建跟请求匹配的过滤条件对象

筛选出同request请求匹配的方法名称, 然后创建新的过滤条件对象，并返回，如果返回null，则认为过滤器不匹配请求。

b.组合两个过滤条件对象

把两个过滤对象的方法名称集合累加起来,  然后创建新的过滤对象, 并返回。

c.比较过滤条件对象优先级

请求方法名称个数多的那个过滤器优先级高。

5.ProducesRequestCondition(应答媒体类型过滤器)

a. 创建跟请求匹配的过滤条件对象

筛选出同请求Content-Type匹配的媒体类型表达式列表, 然后创建新的过滤条件对象, 并返回。

b.组合两个过滤条件对象

如果传入的过滤器的媒体类型表达式列表不为空, 则优先使用，这样处理的目的是方法的匹配覆盖类的配置。

c.比较过滤条件对象优先级

同请求Content-Type匹配度高的过滤对象优先级高。

6.ConsumesRequestCondition(请求媒体类型过滤器)

a. 创建跟请求匹配的过滤条件对象

筛选出同请求Accpet匹配的媒体类型表达式列表, 然后创建新的过滤条件对象, 并返回。
b.组合两个过滤条件对象
如果传入的过滤器的媒体类型表达式列表不为空, 则优先使用，这样处理的目的是方法的匹配覆盖类的配置。
c.比较过滤条件对象优先级
      同请求Accpet匹配度高的过滤对象优先级高。
————————————————
版权声明：本文为CSDN博主「叶琎宇」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/roberts939299/article/details/73260485
 
 
原文链接：https://blog.csdn.net/roberts939299/article/details/73260485
 * A {@link RequestCondition} that consists of the following other conditions:
 * <ol>
 * <li>{@link PatternsRequestCondition}
 * <li>{@link RequestMethodsRequestCondition}
 * <li>{@link ParamsRequestCondition}
 * <li>{@link HeadersRequestCondition}
 * <li>{@link ConsumesRequestCondition}
 * <li>{@link ProducesRequestCondition}
 * <li>{@code RequestCondition} (optional, custom request condition)
 * </ol>
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class RequestMappingInfo implements RequestCondition<RequestMappingInfo> {

	@Nullable
	private final String name;

	private final PatternsRequestCondition patternsCondition;

	private final RequestMethodsRequestCondition methodsCondition;

	private final ParamsRequestCondition paramsCondition;

	private final HeadersRequestCondition headersCondition;

	private final ConsumesRequestCondition consumesCondition;

	private final ProducesRequestCondition producesCondition;

	private final RequestConditionHolder customConditionHolder;


	public RequestMappingInfo(@Nullable String name, @Nullable PatternsRequestCondition patterns,
			@Nullable RequestMethodsRequestCondition methods, @Nullable ParamsRequestCondition params,
			@Nullable HeadersRequestCondition headers, @Nullable ConsumesRequestCondition consumes,
			@Nullable ProducesRequestCondition produces, @Nullable RequestCondition<?> custom) {

		this.name = (StringUtils.hasText(name) ? name : null);
		this.patternsCondition = (patterns != null ? patterns : new PatternsRequestCondition());
		this.methodsCondition = (methods != null ? methods : new RequestMethodsRequestCondition());
		this.paramsCondition = (params != null ? params : new ParamsRequestCondition());
		this.headersCondition = (headers != null ? headers : new HeadersRequestCondition());
		this.consumesCondition = (consumes != null ? consumes : new ConsumesRequestCondition());
		this.producesCondition = (produces != null ? produces : new ProducesRequestCondition());
		this.customConditionHolder = new RequestConditionHolder(custom);
	}

	/**
	 * Creates a new instance with the given request conditions.
	 */
	public RequestMappingInfo(@Nullable PatternsRequestCondition patterns,
			@Nullable RequestMethodsRequestCondition methods, @Nullable ParamsRequestCondition params,
			@Nullable HeadersRequestCondition headers, @Nullable ConsumesRequestCondition consumes,
			@Nullable ProducesRequestCondition produces, @Nullable RequestCondition<?> custom) {

		this(null, patterns, methods, params, headers, consumes, produces, custom);
	}

	/**
	 * Re-create a RequestMappingInfo with the given custom request condition.
	 */
	public RequestMappingInfo(RequestMappingInfo info, @Nullable RequestCondition<?> customRequestCondition) {
		this(info.name, info.patternsCondition, info.methodsCondition, info.paramsCondition, info.headersCondition,
				info.consumesCondition, info.producesCondition, customRequestCondition);
	}


	/**
	 * Return the name for this mapping, or {@code null}.
	 */
	@Nullable
	public String getName() {
		return this.name;
	}

	/**
	 * Return the URL patterns of this {@link RequestMappingInfo};
	 * or instance with 0 patterns (never {@code null}).
	 */
	public PatternsRequestCondition getPatternsCondition() {
		return this.patternsCondition;
	}

	/**
	 * Return the HTTP request methods of this {@link RequestMappingInfo};
	 * or instance with 0 request methods (never {@code null}).
	 */
	public RequestMethodsRequestCondition getMethodsCondition() {
		return this.methodsCondition;
	}

	/**
	 * Return the "parameters" condition of this {@link RequestMappingInfo};
	 * or instance with 0 parameter expressions (never {@code null}).
	 */
	public ParamsRequestCondition getParamsCondition() {
		return this.paramsCondition;
	}

	/**
	 * Return the "headers" condition of this {@link RequestMappingInfo};
	 * or instance with 0 header expressions (never {@code null}).
	 */
	public HeadersRequestCondition getHeadersCondition() {
		return this.headersCondition;
	}

	/**
	 * Return the "consumes" condition of this {@link RequestMappingInfo};
	 * or instance with 0 consumes expressions (never {@code null}).
	 */
	public ConsumesRequestCondition getConsumesCondition() {
		return this.consumesCondition;
	}

	/**
	 * Return the "produces" condition of this {@link RequestMappingInfo};
	 * or instance with 0 produces expressions (never {@code null}).
	 */
	public ProducesRequestCondition getProducesCondition() {
		return this.producesCondition;
	}

	/**
	 * Return the "custom" condition of this {@link RequestMappingInfo}, or {@code null}.
	 */
	@Nullable
	public RequestCondition<?> getCustomCondition() {
		return this.customConditionHolder.getCondition();
	}


	/**
	 * Combine "this" request mapping info (i.e. the current instance) with another request mapping info instance.
	 * <p>Example: combine type- and method-level request mappings.
	 * @return a new request mapping info instance; never {@code null}
	 */
	@Override
	public RequestMappingInfo combine(RequestMappingInfo other) {
		String name = combineNames(other);
		PatternsRequestCondition patterns = this.patternsCondition.combine(other.patternsCondition);
		RequestMethodsRequestCondition methods = this.methodsCondition.combine(other.methodsCondition);
		ParamsRequestCondition params = this.paramsCondition.combine(other.paramsCondition);
		HeadersRequestCondition headers = this.headersCondition.combine(other.headersCondition);
		ConsumesRequestCondition consumes = this.consumesCondition.combine(other.consumesCondition);
		ProducesRequestCondition produces = this.producesCondition.combine(other.producesCondition);
		RequestConditionHolder custom = this.customConditionHolder.combine(other.customConditionHolder);

		return new RequestMappingInfo(name, patterns,
				methods, params, headers, consumes, produces, custom.getCondition());
	}

	@Nullable
	private String combineNames(RequestMappingInfo other) {
		if (this.name != null && other.name != null) {
			String separator = RequestMappingInfoHandlerMethodMappingNamingStrategy.SEPARATOR;
			return this.name + separator + other.name;
		}
		else if (this.name != null) {
			return this.name;
		}
		else {
			return other.name;
		}
	}

	/**
	 * Checks if all conditions in this request mapping info match the provided request and returns
	 * a potentially new request mapping info with conditions tailored to the current request.
	 * <p>For example the returned instance may contain the subset of URL patterns that match to
	 * the current request, sorted with best matching patterns on top.
	 * @return a new instance in case all conditions match; or {@code null} otherwise
	 */
	@Override
	@Nullable
	public RequestMappingInfo getMatchingCondition(HttpServletRequest request) {
		RequestMethodsRequestCondition methods = this.methodsCondition.getMatchingCondition(request);
		ParamsRequestCondition params = this.paramsCondition.getMatchingCondition(request);
		HeadersRequestCondition headers = this.headersCondition.getMatchingCondition(request);
		ConsumesRequestCondition consumes = this.consumesCondition.getMatchingCondition(request);
		ProducesRequestCondition produces = this.producesCondition.getMatchingCondition(request);

		if (methods == null || params == null || headers == null || consumes == null || produces == null) {
			return null;
		}

		PatternsRequestCondition patterns = this.patternsCondition.getMatchingCondition(request);
		if (patterns == null) {
			return null;
		}

		RequestConditionHolder custom = this.customConditionHolder.getMatchingCondition(request);
		if (custom == null) {
			return null;
		}

		return new RequestMappingInfo(this.name, patterns,
				methods, params, headers, consumes, produces, custom.getCondition());
	}

	/**
	 * Compares "this" info (i.e. the current instance) with another info in the context of a request.
	 * <p>Note: It is assumed both instances have been obtained via
	 * {@link #getMatchingCondition(HttpServletRequest)} to ensure they have conditions with
	 * content relevant to current request.
	 */
	@Override
	public int compareTo(RequestMappingInfo other, HttpServletRequest request) {
		int result;
		// Automatic vs explicit HTTP HEAD mapping
		if (HttpMethod.HEAD.matches(request.getMethod())) {
			result = this.methodsCondition.compareTo(other.getMethodsCondition(), request);
			if (result != 0) {
				return result;
			}
		}
		result = this.patternsCondition.compareTo(other.getPatternsCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.paramsCondition.compareTo(other.getParamsCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.headersCondition.compareTo(other.getHeadersCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.consumesCondition.compareTo(other.getConsumesCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.producesCondition.compareTo(other.getProducesCondition(), request);
		if (result != 0) {
			return result;
		}
		// Implicit (no method) vs explicit HTTP method mappings
		result = this.methodsCondition.compareTo(other.getMethodsCondition(), request);
		if (result != 0) {
			return result;
		}
		result = this.customConditionHolder.compareTo(other.customConditionHolder, request);
		if (result != 0) {
			return result;
		}
		return 0;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof RequestMappingInfo)) {
			return false;
		}
		RequestMappingInfo otherInfo = (RequestMappingInfo) other;
		return (this.patternsCondition.equals(otherInfo.patternsCondition) &&
				this.methodsCondition.equals(otherInfo.methodsCondition) &&
				this.paramsCondition.equals(otherInfo.paramsCondition) &&
				this.headersCondition.equals(otherInfo.headersCondition) &&
				this.consumesCondition.equals(otherInfo.consumesCondition) &&
				this.producesCondition.equals(otherInfo.producesCondition) &&
				this.customConditionHolder.equals(otherInfo.customConditionHolder));
	}

	@Override
	public int hashCode() {
		return (this.patternsCondition.hashCode() * 31 +  // primary differentiation
				this.methodsCondition.hashCode() + this.paramsCondition.hashCode() +
				this.headersCondition.hashCode() + this.consumesCondition.hashCode() +
				this.producesCondition.hashCode() + this.customConditionHolder.hashCode());
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("{");
		builder.append(this.patternsCondition);
		if (!this.methodsCondition.isEmpty()) {
			builder.append(",methods=").append(this.methodsCondition);
		}
		if (!this.paramsCondition.isEmpty()) {
			builder.append(",params=").append(this.paramsCondition);
		}
		if (!this.headersCondition.isEmpty()) {
			builder.append(",headers=").append(this.headersCondition);
		}
		if (!this.consumesCondition.isEmpty()) {
			builder.append(",consumes=").append(this.consumesCondition);
		}
		if (!this.producesCondition.isEmpty()) {
			builder.append(",produces=").append(this.producesCondition);
		}
		if (!this.customConditionHolder.isEmpty()) {
			builder.append(",custom=").append(this.customConditionHolder);
		}
		builder.append('}');
		return builder.toString();
	}


	/**
	 * Create a new {@code RequestMappingInfo.Builder} with the given paths.
	 * @param paths the paths to use
	 * @since 4.2
	 */
	public static Builder paths(String... paths) {
		return new DefaultBuilder(paths);
	}


	/**
	 * Defines a builder for creating a RequestMappingInfo.
	 * @since 4.2
	 */
	public interface Builder {

		/**
		 * Set the path patterns.
		 */
		Builder paths(String... paths);

		/**
		 * Set the request method conditions.
		 */
		Builder methods(RequestMethod... methods);

		/**
		 * Set the request param conditions.
		 */
		Builder params(String... params);

		/**
		 * Set the header conditions.
		 * <p>By default this is not set.
		 */
		Builder headers(String... headers);

		/**
		 * Set the consumes conditions.
		 */
		Builder consumes(String... consumes);

		/**
		 * Set the produces conditions.
		 */
		Builder produces(String... produces);

		/**
		 * Set the mapping name.
		 */
		Builder mappingName(String name);

		/**
		 * Set a custom condition to use.
		 */
		Builder customCondition(RequestCondition<?> condition);

		/**
		 * Provide additional configuration needed for request mapping purposes.
		 */
		Builder options(BuilderConfiguration options);

		/**
		 * Build the RequestMappingInfo.
		 */
		RequestMappingInfo build();
	}


	private static class DefaultBuilder implements Builder {

		private String[] paths = new String[0];

		private RequestMethod[] methods = new RequestMethod[0];

		private String[] params = new String[0];

		private String[] headers = new String[0];

		private String[] consumes = new String[0];

		private String[] produces = new String[0];

		@Nullable
		private String mappingName;

		@Nullable
		private RequestCondition<?> customCondition;

		private BuilderConfiguration options = new BuilderConfiguration();

		public DefaultBuilder(String... paths) {
			this.paths = paths;
		}

		@Override
		public Builder paths(String... paths) {
			this.paths = paths;
			return this;
		}

		@Override
		public DefaultBuilder methods(RequestMethod... methods) {
			this.methods = methods;
			return this;
		}

		@Override
		public DefaultBuilder params(String... params) {
			this.params = params;
			return this;
		}

		@Override
		public DefaultBuilder headers(String... headers) {
			this.headers = headers;
			return this;
		}

		@Override
		public DefaultBuilder consumes(String... consumes) {
			this.consumes = consumes;
			return this;
		}

		@Override
		public DefaultBuilder produces(String... produces) {
			this.produces = produces;
			return this;
		}

		@Override
		public DefaultBuilder mappingName(String name) {
			this.mappingName = name;
			return this;
		}

		@Override
		public DefaultBuilder customCondition(RequestCondition<?> condition) {
			this.customCondition = condition;
			return this;
		}

		@Override
		public Builder options(BuilderConfiguration options) {
			this.options = options;
			return this;
		}

		@Override
		public RequestMappingInfo build() {
			ContentNegotiationManager manager = this.options.getContentNegotiationManager();

			PatternsRequestCondition patternsCondition = new PatternsRequestCondition(
					this.paths, this.options.getUrlPathHelper(), this.options.getPathMatcher(),
					this.options.useSuffixPatternMatch(), this.options.useTrailingSlashMatch(),
					this.options.getFileExtensions());

			return new RequestMappingInfo(this.mappingName, patternsCondition,
					new RequestMethodsRequestCondition(this.methods),
					new ParamsRequestCondition(this.params),
					new HeadersRequestCondition(this.headers),
					new ConsumesRequestCondition(this.consumes, this.headers),
					new ProducesRequestCondition(this.produces, this.headers, manager),
					this.customCondition);
		}
	}


	/**
	 * Container for configuration options used for request mapping purposes.
	 * Such configuration is required to create RequestMappingInfo instances but
	 * is typically used across all RequestMappingInfo instances.
	 * @since 4.2
	 * @see Builder#options
	 */
	public static class BuilderConfiguration {

		@Nullable
		private UrlPathHelper urlPathHelper;

		@Nullable
		private PathMatcher pathMatcher;

		private boolean trailingSlashMatch = true;

		private boolean suffixPatternMatch = true;

		private boolean registeredSuffixPatternMatch = false;

		@Nullable
		private ContentNegotiationManager contentNegotiationManager;

		/**
		 * Set a custom UrlPathHelper to use for the PatternsRequestCondition.
		 * <p>By default this is not set.
		 * @since 4.2.8
		 */
		public void setUrlPathHelper(@Nullable UrlPathHelper urlPathHelper) {
			this.urlPathHelper = urlPathHelper;
		}

		/**
		 * Return a custom UrlPathHelper to use for the PatternsRequestCondition, if any.
		 */
		@Nullable
		public UrlPathHelper getUrlPathHelper() {
			return this.urlPathHelper;
		}

		/**
		 * Set a custom PathMatcher to use for the PatternsRequestCondition.
		 * <p>By default this is not set.
		 */
		public void setPathMatcher(@Nullable PathMatcher pathMatcher) {
			this.pathMatcher = pathMatcher;
		}

		/**
		 * Return a custom PathMatcher to use for the PatternsRequestCondition, if any.
		 */
		@Nullable
		public PathMatcher getPathMatcher() {
			return this.pathMatcher;
		}

		/**
		 * Set whether to apply trailing slash matching in PatternsRequestCondition.
		 * <p>By default this is set to 'true'.
		 */
		public void setTrailingSlashMatch(boolean trailingSlashMatch) {
			this.trailingSlashMatch = trailingSlashMatch;
		}

		/**
		 * Return whether to apply trailing slash matching in PatternsRequestCondition.
		 */
		public boolean useTrailingSlashMatch() {
			return this.trailingSlashMatch;
		}

		/**
		 * Set whether to apply suffix pattern matching in PatternsRequestCondition.
		 * <p>By default this is set to 'true'.
		 * @see #setRegisteredSuffixPatternMatch(boolean)
		 */
		public void setSuffixPatternMatch(boolean suffixPatternMatch) {
			this.suffixPatternMatch = suffixPatternMatch;
		}

		/**
		 * Return whether to apply suffix pattern matching in PatternsRequestCondition.
		 */
		public boolean useSuffixPatternMatch() {
			return this.suffixPatternMatch;
		}

		/**
		 * Set whether suffix pattern matching should be restricted to registered
		 * file extensions only. Setting this property also sets
		 * {@code suffixPatternMatch=true} and requires that a
		 * {@link #setContentNegotiationManager} is also configured in order to
		 * obtain the registered file extensions.
		 */
		public void setRegisteredSuffixPatternMatch(boolean registeredSuffixPatternMatch) {
			this.registeredSuffixPatternMatch = registeredSuffixPatternMatch;
			this.suffixPatternMatch = (registeredSuffixPatternMatch || this.suffixPatternMatch);
		}

		/**
		 * Return whether suffix pattern matching should be restricted to registered
		 * file extensions only.
		 */
		public boolean useRegisteredSuffixPatternMatch() {
			return this.registeredSuffixPatternMatch;
		}

		/**
		 * Return the file extensions to use for suffix pattern matching. If
		 * {@code registeredSuffixPatternMatch=true}, the extensions are obtained
		 * from the configured {@code contentNegotiationManager}.
		 */
		@Nullable
		public List<String> getFileExtensions() {
			if (useRegisteredSuffixPatternMatch() && this.contentNegotiationManager != null) {
				return this.contentNegotiationManager.getAllFileExtensions();
			}
			return null;
		}

		/**
		 * Set the ContentNegotiationManager to use for the ProducesRequestCondition.
		 * <p>By default this is not set.
		 */
		public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
			this.contentNegotiationManager = contentNegotiationManager;
		}

		/**
		 * Return the ContentNegotiationManager to use for the ProducesRequestCondition,
		 * if any.
		 */
		@Nullable
		public ContentNegotiationManager getContentNegotiationManager() {
			return this.contentNegotiationManager;
		}
	}

}
