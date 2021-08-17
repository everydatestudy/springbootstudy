package org.spring.study.thread;

class Philosopher extends Thread {
	Chopstick left;
	Chopstick right;
	String name;
	public Philosopher(String name, Chopstick left, Chopstick right) {
		super(name);
		this.name=name;
		this.left = left;
		this.right = right;
	}

	@Override
	public void run() {
		while (true) {
			// 尝试获得左手筷子
			if (left.tryLock()) {
				try {
					// 尝试获得右手筷子
					if (right.tryLock()) {
						try {
							eat();
						} finally {
							right.unlock();
						}
					}
				} finally {
					left.unlock();
				}
			}
		}
	}

	private void eat() {
		try {
			 System.out.println("I am Eating:"+name);
			Thread.sleep(1000l);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Chopstick c1 = new Chopstick("1");
		Chopstick c2 = new Chopstick("2");
		Chopstick c3 = new Chopstick("3");
		Chopstick c4 = new Chopstick("4");
		Chopstick c5 = new Chopstick("5");
		new Philosopher("苏格拉底", c1, c2).start();
		new Philosopher("柏拉图", c2, c3).start();
		new Philosopher("亚里士多德", c3, c4).start();
		new Philosopher("赫拉克利特", c4, c5).start();
		new Philosopher("阿基米德", c5, c1).start();
	}
}