package com.hmy.circular.dependency;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;

@ComponentScan
@Configuration
public class TestHmy {

	// 早期引用
	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestHmy.class);
		Aservice aservice = context.getBean(Aservice.class);

		System.out.println("\n========================\n" + aservice);
		((AbstractApplicationContext) context).close();
	}

}