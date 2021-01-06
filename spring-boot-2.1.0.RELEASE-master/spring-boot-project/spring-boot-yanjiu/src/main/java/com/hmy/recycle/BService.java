package com.hmy.recycle;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(value = "prototype")
public class BService {
	@Autowired
	AService a;

	@PostConstruct
	public void init() {
		System.out.println("BService");
	}
}
