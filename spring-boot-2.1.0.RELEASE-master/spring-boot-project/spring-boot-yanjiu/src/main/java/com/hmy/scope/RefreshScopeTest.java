package com.hmy.scope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@EnableFeignClients
public class RefreshScopeTest {
	public static void main(String[] args) {

		SpringApplication.run(RefreshScopeTest.class, args);
	}
}
