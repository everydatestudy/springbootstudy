package com.hmy.recycle;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BService {
	@Autowired
	AService a;

	@PostConstruct
	public void init() {
		System.out.println("BService");
	}
}
