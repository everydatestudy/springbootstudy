package org.spring.study.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.DefaultLoginPageConfigurer;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;

//@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
	/**
	 * 对于不需要授权的静态文件放行
	 * 
	 * @param web
	 * @throws Exception
	 */
	@Override
	public void configure(WebSecurity web) throws Exception {
		web.ignoring().antMatchers("/js/**", "/css/**", "/images/**");
	}
	//这是系统
	// @formatter:off
//	http
//		.csrf().and()
//		.addFilter(new WebAsyncManagerIntegrationFilter())
//		.exceptionHandling().and()
//		.headers().and()
//		.sessionManagement().and()
//		.securityContext().and()
//		.requestCache().and()
//		.anonymous().and()
//		.servletApi().and()
//		.apply(new DefaultLoginPageConfigurer<>()).and()
//		.logout();
//	// @formatter:on
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.authorizeRequests().anyRequest()
		    .authenticated()
		    .and().formLogin()// form表单的方式
				.loginPage("/login.html")// 登录页面路径
				.permitAll()// 不拦截
				.and().csrf()// 记得关闭
				.disable();
	}
}
