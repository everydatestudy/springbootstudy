package com.hmy;

import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.AbstractApplicationContext;

import com.hmy.recycle.AService;

//@EnableAspectJAutoProxy
@ComponentScan("com.hmy.recycle")
//https://www.cnblogs.com/developer_chan/p/10740664.html 新的spring知识讲解了创建实例化
public class TestMain {

	public static void main(String[] args) {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "D:\\home\\cglib");
		// ClassPathXmlApplicationContext默认是加载src目录下的xml文件
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
//		context.addBeanFactoryPostProcessor(new TestBeanDefinitionRegistryPostProcessors());
		context.register(TestMain.class);
		context.refresh();
		AService aservice = context.getBean(AService.class);
//		System.out.println("\n===========普通调用=============\n");
//
		 System.out.println(aservice);
//
//		System.out.println("\n===========异常调用=============\n");
//
////		aservice.excuteException();
//
//		System.out.println("\n========================\n");
		((AbstractApplicationContext) context).close();
	}

}