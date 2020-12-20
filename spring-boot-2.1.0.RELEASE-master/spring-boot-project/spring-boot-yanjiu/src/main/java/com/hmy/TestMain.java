package com.hmy;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.AbstractApplicationContext;

import com.hmy.aaa.AspectDao;
import com.hmy.test.service.AspectService;
import com.hmy.test.service.TestBeanDefinitionRegistryPostProcessors;
import com.hmy.test.service.TestImport;

//@EnableAspectJAutoProxy
@ComponentScan("com.hmy")
public class TestMain {

	public static void main(String[] args) {
	 
		// ClassPathXmlApplicationContext默认是加载src目录下的xml文件
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.addBeanFactoryPostProcessor(new TestBeanDefinitionRegistryPostProcessors());
		context.register(TestMain.class);
		context.refresh();
		AspectService aservice = context.getBean(AspectService.class);
//		System.out.println("\n===========普通调用=============\n");
//
		aservice.sayHi("hd");
//
//		System.out.println("\n===========异常调用=============\n");
//
////		aservice.excuteException();
//
//		System.out.println("\n========================\n");
		((AbstractApplicationContext) context).close();
	}

}