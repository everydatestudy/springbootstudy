package com.hmy;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.AbstractApplicationContext;

import com.hmy.listener.UserService;
import com.hmy.test.service.TestBeanDefinitionRegistryPostProcessors;

//@EnableAspectJAutoProxy
@ComponentScan("com.hmy")
public class TestEventMain {

	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.addBeanFactoryPostProcessor(new TestBeanDefinitionRegistryPostProcessors());
		context.register(TestEventMain.class);
		// 增加这个扫描，spring会去除重复的数据
//		context.scan("com.hmy");

		context.refresh();
		UserService userService = (UserService) context.getBean("userService");
		// 注册事件触发
		userService.register("glmapper");

		// PayService payService = context.getBean(PayService.class);
//		payService.printOrder();
//		AspectService aservice = context.getBean(AspectService.class);
//		System.out.println("\n===========普通调用=============\n");
//
//		aservice.sayHi("hd");
//
//		System.out.println("\n===========异常调用=============\n");
//
//		aservice.excuteException();
//
//		System.out.println("\n========================\n");
		((AbstractApplicationContext) context).close();
	}

}