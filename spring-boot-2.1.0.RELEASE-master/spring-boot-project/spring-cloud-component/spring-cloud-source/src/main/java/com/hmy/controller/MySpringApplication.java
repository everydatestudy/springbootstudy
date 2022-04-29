package com.hmy.controller;

import java.io.File;
import java.io.IOException;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class MySpringApplication {
	public static void run() throws Exception {
		File file = new File(System.getProperty("java.io.tmpdir"));

		// Load Spring web application configuration
		AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();

		ac.register(AppConfig.class);
//		ac.refresh();

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(9090);

		// 告訴tomcat你的源码在哪里
		String sourcePath = MySpringApplication.class.getResource("/").getPath();
//        Context context = tomcat.addWebapp("/", file.getAbsolutePath());

		Context context = tomcat.addContext("/", file.getAbsolutePath());

		DispatcherServlet dispatcherServlet = new DispatcherServlet(ac);
		// DispatcherServlet dispatcherServlet = new DispatcherServlet();
		Wrapper sw = Tomcat.addServlet(context, "testServlet", dispatcherServlet);
		sw.setLoadOnStartup(1);
		context.addServletMappingDecoded("/", "testServlet");
		System.out.println(ac.getParent() + "----------------------------------------------------");
		tomcat.getConnector();// Tomcat 9.0 必须调用 Tomcat#getConnector() 方法之后才会监听端口
		tomcat.start();
		tomcat.getServer().await();
		// Thread.sleep(2000000000l);
	}

	public static void tomcatrun() throws LifecycleException {

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(8080);
		tomcat.setBaseDir("/tmp/tomcat");
		String contextPath = "";
		StandardContext context = new StandardContext();
		context.setPath(contextPath);
		context.addLifecycleListener(new Tomcat.FixContextListener());
		tomcat.getHost().addChild(context);

		tomcat.addServlet(contextPath, "apiServlet", new GenericServlet() {
			@Override
			public void service(ServletRequest servletRequest, ServletResponse servletResponse)
					throws ServletException, IOException {
				HttpServletRequest request;
				HttpServletResponse response;
				try {
					request = (HttpServletRequest) servletRequest;
					response = (HttpServletResponse) servletResponse;
				} catch (ClassCastException var6) {
					throw new ServletException("non-HTTP request or response");
				}
				response.setStatus(200);
				response.getWriter().append("Hello World...");
			}
		});
		context.addServletMappingDecoded("/api", "apiServlet");
		// tomcat.addWebapp("/tomcat", "/root/apache-tomcat-8.0.53/webapps/ROOT");
		// 设置不开启keep-alive
		// AbstractHttp11Protocol abstractHttp11Protocol =
		// (AbstractHttp11Protocol)(tomcat.getConnector().getProtocolHandler());
		// abstractHttp11Protocol.setMaxKeepAliveRequests(1);
		tomcat.start();
		tomcat.getServer().await();

	}

	public static void main(String[] args) throws Exception {
		MySpringApplication.run();
	}
}
