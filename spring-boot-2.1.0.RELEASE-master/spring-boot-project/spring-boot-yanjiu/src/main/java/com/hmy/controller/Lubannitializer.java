package com.hmy.controller;

import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class Lubannitializer implements ServletContainerInitializer {
	@Override
	public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
		System.out.println("+++++++++++++++++++++");

		// Load Spring web application configuration
		AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();
		ac.register(AppConfig.class);
		ac.refresh();

		// Create and register the DispatcherServlet
		DispatcherServlet servlet = new DispatcherServlet(ac);
		ServletRegistration.Dynamic registration = ctx.addServlet("app", servlet);
		registration.addMapping("/");
		registration.setLoadOnStartup(1);
	}
}
