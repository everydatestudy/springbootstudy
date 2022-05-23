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
package org.springframework.security.config.annotation.web.configurers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.access.expression.ExpressionBasedFilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Adds URL based authorization based upon SpEL expressions to an application.
 * At least one {@link org.springframework.web.bind.annotation.RequestMapping}
 * needs to be mapped to {@link ConfigAttribute}'s for this
 * {@link SecurityContextConfigurer} to have meaning.
 * <h2>Security Filters</h2>
 *
 * The following Filters are populated
 *
 * <ul>
 * <li>{@link org.springframework.security.web.access.intercept.FilterSecurityInterceptor}
 * </li>
 * </ul>
 *
 * <h2>Shared Objects Created</h2>
 *
 * The following shared objects are populated to allow other
 * {@link org.springframework.security.config.annotation.SecurityConfigurer}'s
 * to customize:
 * <ul>
 * <li>{@link org.springframework.security.web.access.intercept.FilterSecurityInterceptor}
 * </li>
 * </ul>
 *
 * <h2>Shared Objects Used</h2> 配置如下安全过滤器Filter FilterSecurityInterceptor
 * 作为一个配置HttpSecurity的SecurityConfigurer,ExpressionUrlAuthorizationConfigurer的配置任务如下
 * :
 * 
 * 配置如下安全过滤器Filter FilterSecurityInterceptor
 * ExpressionUrlAuthorizationConfigurer可以为多组 RequestMatcher
 * 分别配置不同的权限属性。这里每组RequestMatcher表示一组调用者想设定成相同权限控制的Http method/URL
 * pattern,这里所设置的权限属性其实是基于SpEL的权限表达式。ExpressionUrlAuthorizationConfigurer可以接收一个调用者指定的安全表达式处理器来理解这些权限表达式。如果调用者不指定，则使用缺省的安全表达式处理器DefaultWebSecurityExpressionHandler来理解这些权限表达式。
 * 
 * ExpressionUrlAuthorizationConfigurer继承自AbstractInterceptUrlConfigurer。
 * 作为一个安全配置器，它们对目标安全构建器HttpSecurity的主要配置逻辑实现在AbstractInterceptUrlConfigurer#configure。
 * ExpressionUrlAuthorizationConfigurer主要是根据基类AbstractInterceptUrlConfigurer的定义，提供相应的抽象方法的实现。
 * ———————————————— 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0
 * BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/93376098
 * <ul>
 * <li>{@link AuthenticationTrustResolver} is optionally used to populate the
 * {@link DefaultWebSecurityExpressionHandler}</li>
 * </ul>
 *
 * @param <H> the type of {@link HttpSecurityBuilder} that is being configured
 *
 * @author Rob Winch
 * @since 3.2
 * @see org.springframework.security.config.annotation.web.builders.HttpSecurity#authorizeRequests()
 */
public final class ExpressionUrlAuthorizationConfigurer<H extends HttpSecurityBuilder<H>>
		extends AbstractInterceptUrlConfigurer<ExpressionUrlAuthorizationConfigurer<H>, H> {
	static final String permitAll = "permitAll";
	private static final String denyAll = "denyAll";
	private static final String anonymous = "anonymous";
	private static final String authenticated = "authenticated";
	private static final String fullyAuthenticated = "fullyAuthenticated";
	private static final String rememberMe = "rememberMe";
	// 本安全配置器所使用的 URL pattern 和 所需权限 的注册表，也是映射表，
	// 使用实现类为 ExpressionInterceptUrlRegistry， 一个本类的内部实现类，
	// 继承自基类的 AbstractInterceptUrlRegistry ，通过该注册表类可以为当前
	// 安全配置器指定一个安全表达式处理器 SecurityExpressionHandler<FilterInvocation>，
	// 也就是指定下面的属性变量 expressionHandler

	private final ExpressionInterceptUrlRegistry REGISTRY;

	private SecurityExpressionHandler<FilterInvocation> expressionHandler;

	/**
	 * Creates a new instance
	 * 
	 * @see HttpSecurity#authorizeRequests()
	 */
	public ExpressionUrlAuthorizationConfigurer(ApplicationContext context) {
		// 在构造函数中创建 REGISTRY 为一个 ExpressionInterceptUrlRegistry 实例
		this.REGISTRY = new ExpressionInterceptUrlRegistry(context);
	}

	public ExpressionInterceptUrlRegistry getRegistry() {
		return REGISTRY;
	}

	// 内部嵌套类，非静态类，本安全配置器所使用的 URL pattern 和 所需权限 的注册表，也是映射表，
	// 的实现类，继承自基类的 AbstractInterceptUrlRegistry ，通过该注册表类可以为当前
	// 安全配置器指定一个安全表达式处理器 SecurityExpressionHandler<FilterInvocation>

	public class ExpressionInterceptUrlRegistry extends
			ExpressionUrlAuthorizationConfigurer<H>.AbstractInterceptUrlRegistry<ExpressionInterceptUrlRegistry, AuthorizedUrl> {

		/**
		 * @param context
		 */
		private ExpressionInterceptUrlRegistry(ApplicationContext context) {
			setApplicationContext(context);
		}

		// 针对指定的 HTTP method, 和 mvcPatterns 构造一组 RequestMatcher, 包装在一个
		// MvcMatchersAuthorizedUrl 对象中，最终这组 RequestMatcher 上会应用相同的
		// 权限设置
//	————————————————
//	版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/andy_zhang2007/article/details/93376098
		@Override
		public MvcMatchersAuthorizedUrl mvcMatchers(HttpMethod method, String... mvcPatterns) {
			return new MvcMatchersAuthorizedUrl(createMvcMatchers(method, mvcPatterns));
		}

		// 针对指定的 patterns 构造一组 RequestMatcher, 包装在一个
		// MvcMatchersAuthorizedUrl 对象中，最终这组 RequestMatcher 上会应用相同的
		// 权限设置
		// 本方法其实是使用了上面的方法
		// MvcMatchersAuthorizedUrl mvcMatchers(HttpMethod method, String...
		// mvcPatterns)
//	————————————————
//	版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/andy_zhang2007/article/details/93376098
		@Override
		public MvcMatchersAuthorizedUrl mvcMatchers(String... patterns) {
			return mvcMatchers(null, patterns);
		}

		@Override
		protected final AuthorizedUrl chainRequestMatchersInternal(List<RequestMatcher> requestMatchers) {
			return new AuthorizedUrl(requestMatchers);
		}

		/**
		 * Allows customization of the {@link SecurityExpressionHandler} to be used. The
		 * default is {@link DefaultWebSecurityExpressionHandler}
		 *
		 * @param expressionHandler the {@link SecurityExpressionHandler} to be used
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization.
		 */
		public ExpressionInterceptUrlRegistry expressionHandler(
				SecurityExpressionHandler<FilterInvocation> expressionHandler) {
			ExpressionUrlAuthorizationConfigurer.this.expressionHandler = expressionHandler;
			return this;
		}

		/**
		 * Adds an {@link ObjectPostProcessor} for this class.
		 *
		 * @param objectPostProcessor
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customizations
		 */
		public ExpressionInterceptUrlRegistry withObjectPostProcessor(ObjectPostProcessor<?> objectPostProcessor) {
			addObjectPostProcessor(objectPostProcessor);
			return this;
		}

		public H and() {
			return ExpressionUrlAuthorizationConfigurer.this.and();
		}

	}

	/**
	 * 注册一组 RequestMatcher requestMatchers，这组 requestMatchers 中每一个 RequestMatcher
	 * 都会映射到所需权限 configAttributes Allows registering multiple {@link RequestMatcher}
	 * instances to a collection of {@link ConfigAttribute} instances
	 *
	 * @param requestMatchers  the {@link RequestMatcher} instances to register to
	 *                         the {@link ConfigAttribute} instances
	 * @param configAttributes the {@link ConfigAttribute} to be mapped by the
	 *                         {@link RequestMatcher} instances
	 */
	private void interceptUrl(Iterable<? extends RequestMatcher> requestMatchers,
			Collection<ConfigAttribute> configAttributes) {
		for (RequestMatcher requestMatcher : requestMatchers) {
			// 将 requestMatchers 中每一个RequestMatcher 和 configAttributes 构造一个
			// UrlMapping 对象，也就是 <URL pattern,所需权限>对儿，添加到注册表 REGISTRY
			REGISTRY.addMapping(
					new AbstractConfigAttributeRequestMatcherRegistry.UrlMapping(requestMatcher, configAttributes));
		}
	}
	 // 基类定义的抽象方法，要求实现类必须提供实现。
    // 这里提供实现，创建缺省 AccessDecisionManager 时要用。
    // 这里的实现其实提供了一个 WebExpressionVoter， 使用设置给本类的安全表达式处理器
    // 或者缺省的安全表达式处理器，具体由方法 getExpressionHandler 决定
//————————————————
//版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/andy_zhang2007/article/details/93376098
	@Override
	@SuppressWarnings("rawtypes")
	final List<AccessDecisionVoter<? extends Object>> getDecisionVoters(H http) {
		List<AccessDecisionVoter<? extends Object>> decisionVoters = new ArrayList<AccessDecisionVoter<? extends Object>>();
		WebExpressionVoter expressionVoter = new WebExpressionVoter();
		expressionVoter.setExpressionHandler(getExpressionHandler(http));
		decisionVoters.add(expressionVoter);
		return decisionVoters;
	}
	 // 基类定义的抽象方法，要求实现类必须提供实现。
    // 这里提供实现，创建目标安全拦截过滤器 FilterSecurityInterceptor 时要用。
    // 这里的实现基于调用者提供的配置所形成的 <URL pattern,所需权限>映射信息，也就是 
    // REGISTRY 生成一个 ExpressionBasedFilterInvocationSecurityMetadataSource 对象，
    // 该对象会最终被设置到目标安全拦截过滤器 FilterSecurityInterceptor 上。
    // FilterSecurityInterceptor 会在处理一个请求时，通过该 
    // ExpressionBasedFilterInvocationSecurityMetadataSource 对象获取被请求URL所需的权限，
    // 另外获取请求者所拥有的的权限，从而判断请求这是否可以访问相应资源。


	@Override
	final ExpressionBasedFilterInvocationSecurityMetadataSource createMetadataSource(H http) {
		LinkedHashMap<RequestMatcher, Collection<ConfigAttribute>> requestMap = REGISTRY.createRequestMap();
		if (requestMap.isEmpty()) {
			throw new IllegalStateException(
					"At least one mapping is required (i.e. authorizeRequests().anyRequest().authenticated())");
		}
		return new ExpressionBasedFilterInvocationSecurityMetadataSource(requestMap, getExpressionHandler(http));
	}

	private SecurityExpressionHandler<FilterInvocation> getExpressionHandler(H http) {
		if (expressionHandler == null) {
			DefaultWebSecurityExpressionHandler defaultHandler = new DefaultWebSecurityExpressionHandler();
			AuthenticationTrustResolver trustResolver = http.getSharedObject(AuthenticationTrustResolver.class);
			if (trustResolver != null) {
				defaultHandler.setTrustResolver(trustResolver);
			}
			ApplicationContext context = http.getSharedObject(ApplicationContext.class);
			if (context != null) {
				   // 检查是否有 RoleHierarchy bean 可以应用，若检测到则应用
				String[] roleHiearchyBeanNames = context.getBeanNamesForType(RoleHierarchy.class);
				if (roleHiearchyBeanNames.length == 1) {
					defaultHandler.setRoleHierarchy(context.getBean(roleHiearchyBeanNames[0], RoleHierarchy.class));
				}// 检测是否有 GrantedAuthorityDefaults bean 可以应用，若检测到则应用
				String[] grantedAuthorityDefaultsBeanNames = context
						.getBeanNamesForType(GrantedAuthorityDefaults.class);
				if (grantedAuthorityDefaultsBeanNames.length == 1) {
					GrantedAuthorityDefaults grantedAuthorityDefaults = context
							.getBean(grantedAuthorityDefaultsBeanNames[0], GrantedAuthorityDefaults.class);
					defaultHandler.setDefaultRolePrefix(grantedAuthorityDefaults.getRolePrefix());
				}
				String[] permissionEvaluatorBeanNames = context.getBeanNamesForType(PermissionEvaluator.class);
				if (permissionEvaluatorBeanNames.length == 1) {
					PermissionEvaluator permissionEvaluator = context.getBean(permissionEvaluatorBeanNames[0],
							PermissionEvaluator.class);
					defaultHandler.setPermissionEvaluator(permissionEvaluator);
				}
			}

			expressionHandler = postProcess(defaultHandler);
		}

		return expressionHandler;
	}

	private static String hasAnyRole(String... authorities) {
		String anyAuthorities = StringUtils.arrayToDelimitedString(authorities, "','ROLE_");
		return "hasAnyRole('ROLE_" + anyAuthorities + "')";
	}

	private static String hasRole(String role) {
		Assert.notNull(role, "role cannot be null");
		if (role.startsWith("ROLE_")) {
			throw new IllegalArgumentException(
					"role should not start with 'ROLE_' since it is automatically inserted. Got '" + role + "'");
		}
		return "hasRole('ROLE_" + role + "')";
	}

	private static String hasAuthority(String authority) {
		return "hasAuthority('" + authority + "')";
	}

	private static String hasAnyAuthority(String... authorities) {
		String anyAuthorities = StringUtils.arrayToDelimitedString(authorities, "','");
		return "hasAnyAuthority('" + anyAuthorities + "')";
	}

	private static String hasIpAddress(String ipAddressExpression) {
		return "hasIpAddress('" + ipAddressExpression + "')";
	}

	/**
	 * An {@link AuthorizedUrl} that allows optionally configuring the
	 * {@link MvcRequestMatcher#setMethod(HttpMethod)}
	 *
	 * @author Rob Winch
	 */
	public class MvcMatchersAuthorizedUrl extends AuthorizedUrl {
		/**
		 * Creates a new instance
		 *
		 * @param requestMatchers the {@link RequestMatcher} instances to map
		 */
		private MvcMatchersAuthorizedUrl(List<MvcRequestMatcher> requestMatchers) {
			super(requestMatchers);
		}

		public AuthorizedUrl servletPath(String servletPath) {
			for (MvcRequestMatcher matcher : (List<MvcRequestMatcher>) getMatchers()) {
				matcher.setServletPath(servletPath);
			}
			return this;
		}
	}
	 // 非静态内部类，主要是提供一组方法，便于往当前 ExpressionUrlAuthorizationConfigurer 中
    // 添加一组需要共同权限设置的 RequestMatcher,并对这组 RequestMatcher 设置相同的权限控制。

	public class AuthorizedUrl {
		private List<? extends RequestMatcher> requestMatchers;
		private boolean not;

		/**
		 * Creates a new instance
		 *
		 * @param requestMatchers the {@link RequestMatcher} instances to map
		 */
		private AuthorizedUrl(List<? extends RequestMatcher> requestMatchers) {
			this.requestMatchers = requestMatchers;
		}

		protected List<? extends RequestMatcher> getMatchers() {
			return this.requestMatchers;
		}

		/**
		 * Negates the following expression.
		 *
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public AuthorizedUrl not() {
			this.not = true;
			return this;
		}

		/**
		 * Shortcut for specifying URLs require a particular role. If you do not want to
		 * have "ROLE_" automatically inserted see {@link #hasAuthority(String)}.
		 *
		 * @param role the role to require (i.e. USER, ADMIN, etc). Note, it should not
		 *             start with "ROLE_" as this is automatically inserted.
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry hasRole(String role) {
			return access(ExpressionUrlAuthorizationConfigurer.hasRole(role));
		}

		/**
		 * Shortcut for specifying URLs require any of a number of roles. If you do not
		 * want to have "ROLE_" automatically inserted see
		 * {@link #hasAnyAuthority(String...)}
		 *
		 * @param roles the roles to require (i.e. USER, ADMIN, etc). Note, it should
		 *              not start with "ROLE_" as this is automatically inserted.
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry hasAnyRole(String... roles) {
			return access(ExpressionUrlAuthorizationConfigurer.hasAnyRole(roles));
		}

		/**
		 * Specify that URLs require a particular authority.
		 *
		 * @param authority the authority to require (i.e. ROLE_USER, ROLE_ADMIN, etc).
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry hasAuthority(String authority) {
			return access(ExpressionUrlAuthorizationConfigurer.hasAuthority(authority));
		}

		/**
		 * Specify that URLs requires any of a number authorities.
		 *
		 * @param authorities the requests require at least one of the authorities (i.e.
		 *                    "ROLE_USER","ROLE_ADMIN" would mean either "ROLE_USER" or
		 *                    "ROLE_ADMIN" is required).
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry hasAnyAuthority(String... authorities) {
			return access(ExpressionUrlAuthorizationConfigurer.hasAnyAuthority(authorities));
		}

		/**
		 * Specify that URLs requires a specific IP Address or <a href=
		 * "http://forum.springsource.org/showthread.php?102783-How-to-use-hasIpAddress&p=343971#post343971"
		 * >subnet</a>.
		 *
		 * @param ipaddressExpression the ipaddress (i.e. 192.168.1.79) or local subnet
		 *                            (i.e. 192.168.0/24)
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry hasIpAddress(String ipaddressExpression) {
			return access(ExpressionUrlAuthorizationConfigurer.hasIpAddress(ipaddressExpression));
		}

		/**
		 * Specify that URLs are allowed by anyone.
		 *
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry permitAll() {
			return access(permitAll);
		}

		/**
		 * Specify that URLs are allowed by anonymous users.
		 *
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry anonymous() {
			return access(anonymous);
		}

		/**
		 * Specify that URLs are allowed by users that have been remembered.
		 *
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 * @see RememberMeConfigurer
		 */
		public ExpressionInterceptUrlRegistry rememberMe() {
			return access(rememberMe);
		}

		/**
		 * Specify that URLs are not allowed by anyone.
		 *
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry denyAll() {
			return access(denyAll);
		}

		/**
		 * Specify that URLs are allowed by any authenticated user.
		 *
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry authenticated() {
			return access(authenticated);
		}

		/**
		 * Specify that URLs are allowed by users who have authenticated and were not
		 * "remembered".
		 *
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 * @see RememberMeConfigurer
		 */
		public ExpressionInterceptUrlRegistry fullyAuthenticated() {
			return access(fullyAuthenticated);
		}

		/**
		 * Allows specifying that URLs are secured by an arbitrary expression
		 *
		 * @param attribute the expression to secure the URLs (i.e.
		 *                  "hasRole('ROLE_USER') and hasRole('ROLE_SUPER')")
		 * @return the {@link ExpressionUrlAuthorizationConfigurer} for further
		 *         customization
		 */
		public ExpressionInterceptUrlRegistry access(String attribute) {
			if (not) {
				attribute = "!" + attribute;
			}
			interceptUrl(requestMatchers, SecurityConfig.createList(attribute));
			return ExpressionUrlAuthorizationConfigurer.this.REGISTRY;
		}
	}
}
