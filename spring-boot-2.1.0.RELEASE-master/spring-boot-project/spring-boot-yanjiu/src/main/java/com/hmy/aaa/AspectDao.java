package com.hmy.aaa;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Repository;

//@Repository
public class AspectDao {
	public static void main(String[] args) throws Exception {
		   AtomicBoolean isActive = new AtomicBoolean(true);
		   if (isActive.compareAndSet(false, true)) {
			   System.out.println("ffff");
		   }
	}

}
