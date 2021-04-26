package com.hmy.zhujie;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QualifierConfig {
	@LoadBalanced
	@Bean
	public Friend friend1() {
		return new Friend("丽丽-女");
	}

//	
//	@Bean
//	public Friend friend2() {
//		return new Friend("杰克-男");
//	}

	@Bean
	public Programmer programmer() {
		return new Programmer();
	}
}
