package com.hmy.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(scanBasePackages="com.hmy.springboot",exclude = {ReactiveCommonsClientAutoConfiguration.class})
//@SpringBootConfiguration
@ComponentScan
public class TestSpringBoot {
	public static void main(String[] args) {
		SpringApplication.run(TestSpringBoot.class, args);
	}
}
