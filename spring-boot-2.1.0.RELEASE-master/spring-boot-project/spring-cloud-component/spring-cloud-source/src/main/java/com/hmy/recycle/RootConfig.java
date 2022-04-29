package com.hmy.recycle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;

import org.springframework.beans.factory.annotation.CustomAutowireConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

//1、定义一个自定义的注解（可以不要任何属性）
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@interface MyQualifier {
	String value() default "";
}

class User {

}

//2、定义Bean CustomAutowireConfigurer
@Configuration
public class RootConfig {
	//使用CustomAutowireConfigurer自定义qualifier注解他实现了BeanFactoryPostProcessor
	//在实例化后，spring的默认工厂会调用所有的工厂后置处理器，处理实例化的对象，进行扩展
	@Bean
	public CustomAutowireConfigurer customAutowireConfigurer() {
		CustomAutowireConfigurer configurer = new CustomAutowireConfigurer();
		configurer.setOrder(Ordered.LOWEST_PRECEDENCE);
		configurer.setCustomQualifierTypes(Collections.singleton(MyQualifier.class));
		return configurer;
	}

	@Bean
	public User user() {

		return new User();
	}

}
