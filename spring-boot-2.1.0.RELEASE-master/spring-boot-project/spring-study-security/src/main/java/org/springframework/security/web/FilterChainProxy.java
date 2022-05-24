/*
 * Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
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

package org.springframework.security.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * FilterChainProxy是Spring Security Web添加到Servlet容器用于安全控制的一个Filter。 Delegates
 * {@code Filter} requests to a list of Spring-managed filter beans. As of
 * version 2.0, you shouldn't need to explicitly configure a
 * {@code FilterChainProxy} bean in your application context unless you need
 * very fine control over the filter chain contents. Most cases should be
 * adequately covered by the default {@code <security:http />} namespace
 * configuration options.
 * <p>
 * The {@code FilterChainProxy} is linked into the servlet container filter
 * chain by adding a standard Spring {@link DelegatingFilterProxy} declaration
 * in the application {@code web.xml} file.
 *
 * <h2>Configuration</h2>
 * <p>
 * As of version 3.1, {@code FilterChainProxy} is configured using a list of
 * {@link SecurityFilterChain} instances, each of which contains a
 * {@link RequestMatcher} and a list of filters which should be applied to
 * matching requests. Most applications will only contain a single filter chain,
 * and if you are using the namespace, you don't have to set the chains
 * explicitly. If you require finer-grained control, you can make use of the
 * {@code <filter-chain>} namespace element. This defines a URI pattern and the
 * list of filters (as comma-separated bean names) which should be applied to
 * requests which match the pattern. An example configuration might look like
 * this:
 *
 * <pre>
 *  &lt;bean id="myfilterChainProxy" class="org.springframework.security.util.FilterChainProxy"&gt;
 *      &lt;constructor-arg&gt;
 *          &lt;util:list&gt;
 *              &lt;security:filter-chain pattern="/do/not/filter*" filters="none"/&gt;
 *              &lt;security:filter-chain pattern="/**" filters="filter1,filter2,filter3"/&gt;
 *          &lt;/util:list&gt;
 *      &lt;/constructor-arg&gt;
 *  &lt;/bean&gt;
 * </pre>
 *
 * The names "filter1", "filter2", "filter3" should be the bean names of
 * {@code Filter} instances defined in the application context. The order of the
 * names defines the order in which the filters will be applied. As shown above,
 * use of the value "none" for the "filters" can be used to exclude a request
 * pattern from the security filter chain entirely. Please consult the security
 * namespace schema file for a full list of available configuration options.
 *
 * <h2>Request Handling</h2>
 * <p>
 * Each possible pattern that the {@code FilterChainProxy} should service must
 * be entered. The first match for a given request will be used to define all of
 * the {@code Filter}s that apply to that request. This means you must put most
 * specific matches at the top of the list, and ensure all {@code Filter}s that
 * should apply for a given matcher are entered against the respective entry.
 * The {@code FilterChainProxy} will not iterate through the remainder of the
 * map entries to locate additional {@code Filter}s.
 * <p>
 * {@code FilterChainProxy} respects normal handling of {@code Filter}s that
 * elect not to call
 * {@link javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)}
 * , in that the remainder of the original or {@code FilterChainProxy}-declared
 * filter chain will not be called.
 *
 * <h3>Request Firewalling</h3>
 *
 * An {@link HttpFirewall} instance is used to validate incoming requests and
 * create a wrapped request which provides consistent path values for matching
 * against. See {@link StrictHttpFirewall}, for more information on the type of
 * attacks which the default implementation protects against. A custom
 * implementation can be injected to provide stricter control over the request
 * contents or if an application needs to support certain types of request which
 * are rejected by default.
 * <p>
 * Note that this means that you must use the Spring Security filters in
 * combination with a {@code FilterChainProxy} if you want this protection.
 * Don't define them explicitly in your {@code web.xml} file.
 * <p>
 * {@code FilterChainProxy} will use the firewall instance to obtain both
 * request and response objects which will be fed down the filter chain, so it
 * is also possible to use this functionality to control the functionality of
 * the response. When the request has passed through the security filter chain,
 * the {@code reset} method will be called. With the default implementation this
 * means that the original values of {@code servletPath} and {@code pathInfo}
 * will be returned thereafter, instead of the modified ones used for security
 * pattern matching.
 * <p>
 * Since this additional wrapping functionality is performed by the
 * {@code FilterChainProxy}, we don't recommend that you use multiple instances
 * in the same filter chain. It shouldn't be considered purely as a utility for
 * wrapping filter beans in a single {@code Filter} instance.
 *
 * <h2>Filter Lifecycle</h2>
 * <p>
 * Note the {@code Filter} lifecycle mismatch between the servlet container and
 * IoC container. As described in the {@link DelegatingFilterProxy} Javadocs, we
 * recommend you allow the IoC container to manage the lifecycle instead of the
 * servlet container. {@code FilterChainProxy} does not invoke the standard
 * filter lifecycle methods on any filter beans that you add to the application
 * context.
 *
 * @author Carlos Sanchez
 * @author Ben Alex
 * @author Luke Taylor
 * @author Rob Winch
 */
//FilterChainProxy是Spring Security Web添加到Servlet容器用于安全控制的一个Filter。
//从Servlet容器的角度来看，Spring Security Web所提供的安全逻辑就是一个Filter,实现类为FilterChainProxy。
//实际上FilterChainProxy是一个代理对象，FilterChainProxy内部组合了多个SecurityFilterChain,
//每个SecurityFilterChain组合了一组Filter,这组Filter虽然也实现了Servlet Filter接口，
//但它们对于整个Servlet容器来讲是不可见的。对于Servlet容器来讲，
//Spring Secrutiy Web添加进来的用于安全的过滤器就是FilterChainProxy这一个过滤器，
//真正对请求的安全处理逻辑，最终由匹配该请求的某个SecurityFilterChain中的多个Filter来完成。
//
//在Spring Security Web框架中，FilterChainProxy由Web安全构建器WebSecurity构建而来。
//而该构建动作在应用启动过程中Web安全配置阶段执行，
//具体可以参考Web安全配置类WebSecurityConfiguration。
//而WebSecurityConfiguration则又由注解@EnableWebSecurity引起。这个触发关系，可以这么理解 :
//
//@EnableWebSecurity => WebSecurityConfiguration => WebSecurity 构建 => FilterChainProxy
//1
//缺省情况下FilterChainProxy安全过滤器的名字总是springSecurityFilterChain。
//————————————————
//版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/andy_zhang2007/article/details/90349988
public class FilterChainProxy extends GenericFilterBean {
	// ~ Static fields/initializers
	// =====================================================================================

	private static final Log logger = LogFactory.getLog(FilterChainProxy.class);

	// ~ Instance fields
	// ================================================================================================

	private final static String FILTER_APPLIED = FilterChainProxy.class.getName().concat(".APPLIED");

	private List<SecurityFilterChain> filterChains;

	private FilterChainValidator filterChainValidator = new NullFilterChainValidator();

	private HttpFirewall firewall = new StrictHttpFirewall();

	// ~ Methods
	// ========================================================================================================

	public FilterChainProxy() {
	}

	// 构造函数 ：基于一组过滤器链 SecurityFilterChain 构造一个 FilterChainProxy 对象
	public FilterChainProxy(SecurityFilterChain chain) {
		this(Arrays.asList(chain));
	}

	// 构造函数 ：基于一组过滤器链 SecurityFilterChain 构造一个 FilterChainProxy 对象
	public FilterChainProxy(List<SecurityFilterChain> filterChains) {
		this.filterChains = filterChains;
	}

//  InitializingBean 接口定义的当前 bean 的初始化方法
	// 使用 filterChainValidator 验证当前对象
	@Override
	public void afterPropertiesSet() {
		filterChainValidator.validate(this);
	}

	// Filter 接口定义的过滤器主方法 :
	// 如果针对该请求尚未应用该过滤器则应用该过滤器
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		boolean clearContext = request.getAttribute(FILTER_APPLIED) == null;
		if (clearContext) {
			try {
				request.setAttribute(FILTER_APPLIED, Boolean.TRUE);
				doFilterInternal(request, response, chain);
			} finally {
				SecurityContextHolder.clearContext();
				request.removeAttribute(FILTER_APPLIED);
			}
		} else {
			doFilterInternal(request, response, chain);
		}
	}

	// 应用当前过滤器到请求的具体逻辑实现
	private void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		FirewalledRequest fwRequest = firewall.getFirewalledRequest((HttpServletRequest) request);
		HttpServletResponse fwResponse = firewall.getFirewalledResponse((HttpServletResponse) response);
		// 当前过滤器其实一个组合了多个过滤器链 SecurityFilterChain 的代理对象,
		// 现在找到匹配当前请求的那个 SecurityFilterChain 中的所有安全过滤器
		List<Filter> filters = getFilters(fwRequest);

		if (filters == null || filters.size() == 0) {
			if (logger.isDebugEnabled()) {
				logger.debug(UrlUtils.buildRequestUrl(fwRequest)
						+ (filters == null ? " has no matching filters" : " has an empty filter list"));
			}

			fwRequest.reset();
			// 如果针对当前请求没有匹配的安全过滤器，则继续执行过滤器链 chain
			chain.doFilter(fwRequest, fwResponse);

			return;
		}
		// 如果针对当前请求有相应的安全过滤器，则将这些安全过滤器组成一个 VirtualFilterChain,
		// 虚拟过滤器链，将各个安全过滤器应用到该 请求上，这些安全过滤器应用完之后，在继续
		// 应用 chain 上的其他过滤器到该请求
		VirtualFilterChain vfc = new VirtualFilterChain(fwRequest, chain, filters);
		vfc.doFilter(fwRequest, fwResponse);
	}

	/**
	 * Returns the first filter chain matching the supplied URL.
	 *
	 * @param request the request to match
	 * @return an ordered array of Filters defining the filter chain
	 */
	private List<Filter> getFilters(HttpServletRequest request) {
		for (SecurityFilterChain chain : filterChains) {
			if (chain.matches(request)) {
				return chain.getFilters();
			}
		}

		return null;
	}

	/**
	 * Convenience method, mainly for testing.
	 *
	 * @param url the URL
	 * @return matching filter list
	 */
	public List<Filter> getFilters(String url) {
		return getFilters(firewall.getFirewalledRequest((new FilterInvocation(url, "GET").getRequest())));
	}

	/**
	 * @return the list of {@code SecurityFilterChain}s which will be matched
	 *         against and applied to incoming requests.
	 */
	public List<SecurityFilterChain> getFilterChains() {
		return Collections.unmodifiableList(filterChains);
	}

	/**
	 * Used (internally) to specify a validation strategy for the filters in each
	 * configured chain.
	 *
	 * @param filterChainValidator the validator instance which will be invoked on
	 *                             during initialization to check the
	 *                             {@code FilterChainProxy} instance.
	 */
	public void setFilterChainValidator(FilterChainValidator filterChainValidator) {
		this.filterChainValidator = filterChainValidator;
	}

	/**
	 * Sets the "firewall" implementation which will be used to validate and wrap
	 * (or potentially reject) the incoming requests. The default implementation
	 * should be satisfactory for most requirements.
	 *
	 * @param firewall
	 */
	public void setFirewall(HttpFirewall firewall) {
		this.firewall = firewall;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("FilterChainProxy[");
		sb.append("Filter Chains: ");
		sb.append(filterChains);
		sb.append("]");

		return sb.toString();
	}

	// ~ Inner Classes
	// ==================================================================================================

	/**
	 * Internal {@code FilterChain} implementation that is used to pass a request
	 * through the additional internal list of filters which match the request.
	 */
	private static class VirtualFilterChain implements FilterChain {
		private final FilterChain originalChain;
		private final List<Filter> additionalFilters;
		private final FirewalledRequest firewalledRequest;
		private final int size;
		private int currentPosition = 0;

		private VirtualFilterChain(FirewalledRequest firewalledRequest, FilterChain chain,
				List<Filter> additionalFilters) {
			this.originalChain = chain;
			this.additionalFilters = additionalFilters;
			this.size = additionalFilters.size();
			this.firewalledRequest = firewalledRequest;
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
			if (currentPosition == size) {
				if (logger.isDebugEnabled()) {
					logger.debug(UrlUtils.buildRequestUrl(firewalledRequest)
							+ " reached end of additional filter chain; proceeding with original chain");
				}

				// Deactivate path stripping as we exit the security filter chain
				this.firewalledRequest.reset();

				originalChain.doFilter(request, response);
			} else {
				currentPosition++;

				Filter nextFilter = additionalFilters.get(currentPosition - 1);

				if (logger.isDebugEnabled()) {
					logger.debug(UrlUtils.buildRequestUrl(firewalledRequest) + " at position " + currentPosition
							+ " of " + size + " in additional filter chain; firing Filter: '"
							+ nextFilter.getClass().getSimpleName() + "'");
				}

				nextFilter.doFilter(request, response, this);
			}
		}
	}

	public interface FilterChainValidator {
		void validate(FilterChainProxy filterChainProxy);
	}

	private static class NullFilterChainValidator implements FilterChainValidator {
		@Override
		public void validate(FilterChainProxy filterChainProxy) {
		}
	}

}
