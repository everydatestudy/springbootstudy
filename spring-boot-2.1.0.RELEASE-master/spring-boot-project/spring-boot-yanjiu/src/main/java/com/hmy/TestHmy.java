package com.hmy;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import com.hmy.test.service.AspectService;
import com.hmy.test.service.TestBeanDefinitionRegistryPostProcessors;

//https://www.shangyang.me/2017/04/05/spring-core-container-sourcecode-analysis-annotation-autowired/
//aaa
//https://blog.wangqi.love/articles/Spring/Spring%E5%90%AF%E5%8A%A8%E8%BF%87%E7%A8%8B%E5%88%86%E6%9E%904(registerBeanPostProcessors).html
//https://www.cnblogs.com/toby-xu/p/11332666.html
//https://www.cnblogs.com/binarylei/category/1159500.html
//https://www.imooc.com/article/34150 Spring IOC 容器源码分析 - 循环依赖的解决办法
//https://andyboke.blog.csdn.net/ spring source analysis
@EnableAspectJAutoProxy
@ComponentScan("com.hmy")
@Configuration
public class TestHmy {
	
	//AbstractBeanDefinition是BeanDefinition的一个抽象实现，他包含了很多熟悉，定时一个Bean的属性的类，
	//如	
//	setParentName(original.getParentName());
//	setBeanClassName(original.getBeanClassName());
//	setScope(original.getScope());
//	setAbstract(original.isAbstract());
//	setLazyInit(original.isLazyInit());
//	setFactoryBeanName(original.getFactoryBeanName());
//	setFactoryMethodName(original.getFactoryMethodName());
//	setRole(original.getRole());
//	setSource(original.getSource());
//	copyAttributesFrom(original);
//	AnnotationConfigApplicationContext extends GenericApplicationContext extends AbstractApplicationContext
//GenericApplicationContext这个类实例化了超级工厂方法 DefaultListableBeanFactory	
	
	//早期引用

 
	
	public static void main(String[] args) {

		// ClassPathXmlApplicationContext默认是加载src目录下的xml文件
		//第一步会实例化工厂，
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.addBeanFactoryPostProcessor(new TestBeanDefinitionRegistryPostProcessors());
		context.register(TestMain.class);
		context.refresh();
		AspectService aservice = context.getBean(AspectService.class);
		System.out.println("\n===========普通调用=============\n");

		aservice.sayHi("hd");

		System.out.println("\n===========异常调用=============\n");

		aservice.excuteException();

		System.out.println("\n========================\n");
		((AbstractApplicationContext) context).close();
	}

}