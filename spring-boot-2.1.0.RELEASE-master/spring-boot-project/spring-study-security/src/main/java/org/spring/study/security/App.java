package org.spring.study.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootApplication
public class App {

	@GetMapping("/hello")
	public String hello() {
		return "hello";
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(App.class, args);
	}
}
