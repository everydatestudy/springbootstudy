package com.hmy.constructor;

import org.springframework.stereotype.Component;

@Component
public class TestService {
	public static void main(String[] args) {
		boolean aaa = Object.class.isAssignableFrom(TestService.class);
		System.out.println(aaa);
	}
}
