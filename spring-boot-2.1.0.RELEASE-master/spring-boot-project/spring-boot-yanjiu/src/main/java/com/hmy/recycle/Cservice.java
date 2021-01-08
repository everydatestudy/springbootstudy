package com.hmy.recycle;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

//@Service
public class Cservice implements InitializingBean {
	@PostConstruct
	public void init() {
		System.err.println("PostConstruct");
	}
	public void initMethod() {
		System.err.println("initMethod");
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
		// TODO Auto-generated method stub
		System.err.println("afterPropertiesSet");
	}

}
