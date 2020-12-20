package com.hmy.recycle;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;

import com.hmy.test.service.TestImport;

@Service
@Import(TestImport.class)
public class AService {
	@Autowired
	BService b;

	@PostConstruct
	public void init() {
		System.out.println("AService"+b);
	}
}
