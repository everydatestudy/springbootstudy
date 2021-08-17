package org.spring.study.thread;

public class Consumer implements Runnable {
	MyQueue myQueue;

	public Consumer(MyQueue myQueue) {
		this.myQueue = myQueue;
	}

	@Override
	public void run() {
		while (true) {
			synchronized (myQueue) {
				while (myQueue.isEmpty()) {
					myQueue.notifyAll();
					System.out.println("run: 队空！");
					try {
						myQueue.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				try {
					myQueue.dequeue();
					System.out.println( "run: 消费1，队列长度： " + myQueue.count);
					Thread.sleep(500);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}