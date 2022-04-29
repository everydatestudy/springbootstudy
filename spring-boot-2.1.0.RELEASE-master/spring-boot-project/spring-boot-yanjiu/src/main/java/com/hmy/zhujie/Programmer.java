package com.hmy.zhujie;

import org.springframework.beans.factory.annotation.Autowired;


public class Programmer {
	
	@Autowired(required = false)
	private Friend friends;

	public Friend getFriends() {
		return friends;
	}

	public void setFriends(Friend friends) {
		this.friends = friends;
	}

}
