package com.hmy.constructor;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Component;

@Component("orderService")
 class OrderService {
	private OrderService(MySmartLifecycle my,List user) {
		System.out.println(2);
	}
	private OrderService(MySmartLifecycle my) {
		System.out.println(1);
	}
//	public OrderService() {
//		System.out.println(0);
//	}
}
