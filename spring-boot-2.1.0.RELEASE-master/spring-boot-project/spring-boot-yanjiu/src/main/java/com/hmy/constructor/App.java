package com.hmy.constructor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * Hello world!
 *
 */
@ComponentScan
public class App {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext annotation = new AnnotationConfigApplicationContext(App.class);
		TestService testservice = annotation.getBean(TestService.class);
		System.out.println(testservice);
//		annotation.close();
	}
}
