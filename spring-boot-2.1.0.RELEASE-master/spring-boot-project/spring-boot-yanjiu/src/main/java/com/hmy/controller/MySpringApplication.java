package com.hmy.controller;

import java.io.File;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class MySpringApplication {
	public static void run() throws Exception {
		File file = new File(System.getProperty("java.io.tmpdir"));

		// Load Spring web application configuration
		AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();
		ac.register(AppConfig.class);
		ac.refresh();

		Tomcat tomcat = new Tomcat();
		tomcat.setPort(9090);

		// 告訴tomcat你的源码在哪里
		String sourcePath = MySpringApplication.class.getResource("/").getPath();
//        Context context = tomcat.addWebapp("/", file.getAbsolutePath());

		Context context = tomcat.addContext("/", file.getAbsolutePath());

		DispatcherServlet dispatcherServlet = new DispatcherServlet(ac);
		Wrapper sw = Tomcat.addServlet(context, "testServlet", dispatcherServlet);
		sw.setLoadOnStartup(1);
		context.addServletMappingDecoded("/", "testServlet");

		tomcat.start();
		tomcat.getServer().await();

	}
}
