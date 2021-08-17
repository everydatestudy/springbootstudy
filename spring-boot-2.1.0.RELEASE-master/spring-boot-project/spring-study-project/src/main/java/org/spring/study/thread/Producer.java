package org.spring.study.thread;

public class Producer implements Runnable {
	MyQueue myQueue;

	public Producer(MyQueue myQueue) {
		this.myQueue = myQueue;
	}

	@Override
	public void run() {
		while (true) {
			synchronized (myQueue) {
				while (myQueue.isFull()) {
					myQueue.notifyAll();

					System.out.println("run: 队满！");
					try {
						myQueue.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				myQueue.enqueue(1);
				System.out.println("run: 生产1，队列长度： " + myQueue.count);
			
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
