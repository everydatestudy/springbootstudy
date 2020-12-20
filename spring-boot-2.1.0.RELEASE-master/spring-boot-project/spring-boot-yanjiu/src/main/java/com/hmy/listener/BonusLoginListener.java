package com.hmy.listener;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * @description: BonusServerListener 积分处理，当用户注册时，给当前用户增加初始化积分
 * @email: <a href="glmapper_2018@163.com"></a>
 * @author: guolei.sgl
 * @date: 18/7/25
 */
@Component
public class BonusLoginListener implements ApplicationListener<UserRegisterEvent> {
	public void onApplicationEvent(UserRegisterEvent event) {
		System.out.println("积分服务接到通知，给 " + event.getSource() + " 增加积分...");
	}
}
