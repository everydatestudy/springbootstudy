package com.hmy.test.service;

import javax.annotation.PostConstruct;

import org.springframework.context.annotation.Import;

import com.hmy.aaa.AspectDao;

public class TestImport {
	@PostConstruct
	public void init() {
		System.err.println("TestImport");
	}
}
