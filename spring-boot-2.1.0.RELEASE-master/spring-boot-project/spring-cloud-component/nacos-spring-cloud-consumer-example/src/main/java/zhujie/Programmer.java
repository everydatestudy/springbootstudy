package zhujie;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;

public class Programmer {
	@LoadBalanced
	@Autowired(required = false)
	private Friend friends;

	public Friend getFriends() {
		return friends;
	}

	public void setFriends(Friend friends) {
		this.friends = friends;
	}

}
