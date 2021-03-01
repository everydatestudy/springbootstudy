package com.hmy.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(scanBasePackages="com.hmy.springboot")

public class TestSpringBoot {
   public static void main(String[] args) {
	   SpringApplication.run(TestSpringBoot.class, args);
}
}
