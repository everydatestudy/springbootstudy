package com.hmy.test.service;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.hmy.aaa.AspectDao;

@Service
//@PropertySource
public class AspectService {

//	@named
	AspectDao aspect;

	@PostConstruct
	public void init() {
		System.out.println("aaaaaaaa");
	}

	public String sayHi(String name) {
		System.out.println("方法：sayHi 执行中 ....");
		return "Hello, " + name;
	}

	public String excuteException() {
		System.out.println("方法：excuteException 执行中 ....");
		int n = 1;
		if (n > 0) {
			throw new RuntimeException("数据异常");
		}
		return "";
	}

	public static void main(String[] args) {
		AspectService aa = new AspectService();

	}

}