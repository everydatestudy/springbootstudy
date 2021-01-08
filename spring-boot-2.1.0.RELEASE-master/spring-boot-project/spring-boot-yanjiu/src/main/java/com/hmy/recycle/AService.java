package com.hmy.recycle;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.hmy.test.service.TestImport;

@Service
//@Scope(value = "prototype") 
public class AService {
//	@Autowired
	Cservice b;

	public AService(Cservice str) {
		this.b = str;
	}

}
