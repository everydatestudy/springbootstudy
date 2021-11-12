package org.feign.spring.cloud.demo;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@EnableFeignClients
public class App {
	public static void main(String[] args) {
		DefaultListableBeanFactory fdsa;
		SpringApplication.run(App.class, args);
	}
}
