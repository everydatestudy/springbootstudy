package com.hmy.recycle.aop;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hmy.recycle.Cservice;
import com.hmy.test.service.TestImport;

@Service
//@Scope(value = "prototype") 
public interface AService {

	public String show();
}
