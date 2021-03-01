package com.hmy.listener;

import org.springframework.context.ApplicationEvent;

/**
 * @description: 用户注册事件
 * @email: <a href="glmapper_2018@163.com"></a>
 * @author: guolei.sgl
 * @date: 18/7/25
 */
//@Component
public class UserLoginEvent extends ApplicationEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8990254490629572993L;
	public String name;

	public UserLoginEvent(Object o) {
		super(o);
	}

	public UserLoginEvent(Object o, String name) {
		super(o);
		this.name = name;
	}
}
