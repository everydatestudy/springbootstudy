package org.spring.study.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
public class TestSpringBoot {
	public static void main(String[] args) {
		SpringApplication.run(TestSpringBoot.class, args);
	}
}
