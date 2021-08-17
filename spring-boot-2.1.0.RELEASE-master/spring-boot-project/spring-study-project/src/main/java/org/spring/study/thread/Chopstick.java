package org.spring.study.thread;

import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("serial")
class Chopstick extends ReentrantLock {
	String name;

	public Chopstick(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "筷子{" + name + '}';
	}
}