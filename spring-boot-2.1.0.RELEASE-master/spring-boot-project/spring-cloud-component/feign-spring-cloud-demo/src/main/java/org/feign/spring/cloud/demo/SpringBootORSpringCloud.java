package org.feign.spring.cloud.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

//Spring Boot容器 vs Spring Cloud容器
//我们知道，Spring Cloud容器是Spring Boot容器的父容器。为了让你更直观的看到父子容器内的Bean情况（个数 + 详情），了解其区别，此处A哥写个最简案例比较一波：
public class SpringBootORSpringCloud {
	public static void main(String[] args) throws Exception {
		ConfigurableApplicationContext bootContext = SpringApplication.run(SpringBootORSpringCloud.class, args);
		System.out.println("boot容器类型" + bootContext.getClass());
		System.out.println("boot容器Bean定义总数：" + bootContext.getBeanFactory().getBeanDefinitionCount());
		System.out.println("boot容器Bean实例总数：" + bootContext.getBeanFactory().getSingletonCount());

		ConfigurableApplicationContext cloudContext = (ConfigurableApplicationContext) bootContext.getParent();
		System.out.println("cloud容器类型" + cloudContext.getClass());
		System.out.println("cloud容器Bean定义总数：" + cloudContext.getBeanFactory().getBeanDefinitionCount());
		System.out.println("cloud容器Bean实例总数：" + cloudContext.getBeanFactory().getSingletonCount());
	}
}
