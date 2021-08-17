package org.spring.study.thread;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockThread {
	Lock lock = new ReentrantLock();

	public void lock(String name) {
		// 获取锁
		lock.lock();
		try {
			while(true) {
				try {
					Thread.sleep(10000l);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println(name + " get the lock");
			}
			
			// 访问此锁保护的资源
		} finally {
			// 释放锁
			lock.unlock();
			System.out.println(name + " release the lock");
		}
	}

	public static void main(String[] args) {
		LockThread lt = new LockThread();
		Thread t = new Thread(() -> lt.lock("A"));
		t.start();
		t.interrupt();
		Thread t2 = new Thread(() -> lt.lock("B"));
		t2.start();
	}
}