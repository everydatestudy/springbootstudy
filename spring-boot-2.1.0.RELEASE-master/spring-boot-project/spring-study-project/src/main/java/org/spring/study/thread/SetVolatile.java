package org.spring.study.thread;

public class SetVolatile {
	volatile boolean isflag = true;
	int i;

	public void show() {
		new Thread(() -> {
			while (isflag) {
				i++;

			}
		}).start();
	}

	public static void main(String[] args) throws InterruptedException {
		SetVolatile set = new SetVolatile();
		set.show();
		while (true) {
			Thread.sleep(1000l);
			set.isflag = false;
			System.out.println(set.i);
		}

	}
}
