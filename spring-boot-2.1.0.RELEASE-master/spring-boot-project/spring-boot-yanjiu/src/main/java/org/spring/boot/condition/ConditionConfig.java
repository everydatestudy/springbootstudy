package org.spring.boot.condition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;

class LinuxEnvironment extends  AbstractEnvironment {

}

@Configuration
public class ConditionConfig {
	// 只有`@ConditionalOnLinux`的注解属性`environment`是"linux"时才会创建bean
	@Bean
	@ConditionalOnLinux(environment = "linux")
	public Environment linuxEnvironment() {
		return new LinuxEnvironment();
	}
}