package com.hmy.aaa;

import org.springframework.asm.Type;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringApplication;

public class packageInfo {
	public static void main(String[] args) throws Exception {
		 System.out.println(Type.getType(BeanFactory.class));
	}

}