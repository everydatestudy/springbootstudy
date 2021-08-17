package org.spring.study.thread;

public class VolatileTest extends Thread {

	boolean flag = false;
	int i = 0;

	public void run() {
		while (!flag) {
			i++;
		}
	}

	public static void main(String[] args) throws Exception {
		VolatileTest vt = new VolatileTest();
		vt.start();
		Thread.sleep(2000);
		vt.flag = true;
		System.out.println("stope" + vt.i);
	}
}