package org.spring.study.thread;

public class MyQueue {
	public int size = 10; // 队列大小
	public int count = 0; // 计数
	public int datas[];
	public int front; // 头指针
	public int rear; // 尾指针

	public MyQueue() {

		datas = new int[10];
		count = 0;
		front = 0;
		rear = 0;
	}

	public boolean enqueue(int data) {
		if (isFull()) {
			return false;
		}
		datas[rear] = data;
		rear = (rear + 1) % size;
		count++;
		return true;
	}

	public int dequeue() throws Exception {
		if (isEmpty()) {
			throw new Exception("empty");
		}
		int data = datas[front];
		front = (front + 1) % size;
		count--;
		return data;
	}

	/**
	 * 循环队列空队和满队条件都是front == rear解决该问题有几种判断如下 1.循环队列损失一个存储空间
	 * 2.设计一个计数器count，统计队列中的元素个数。此时，队列满的判断条件为：count > 0 && rear == front
	 * ；队列空的判断条件为count == 0。
	 *
	 * @return
	 */
	public boolean isFull() {
		return (rear + 1) % size == front;
	}

	public boolean isEmpty() {
		return front == rear;
	}

	public int getFront() throws Exception {
		if (!isEmpty()) {
			throw new Exception("empty");
		}
		return datas[front];
	}

	public static void main(String[] args) {
		MyQueue my = new MyQueue();

		for (int i = 0; i < 5; i++) {
			new Thread(new Producer(my)).start();
		}
		for (int i = 0; i < 5; i++) {
			new Thread(new Consumer(my)).start();
		}
	}
}
