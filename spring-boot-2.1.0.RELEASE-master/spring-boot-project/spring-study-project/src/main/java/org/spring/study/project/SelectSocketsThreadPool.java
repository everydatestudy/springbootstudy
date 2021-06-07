package org.spring.study.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * @Project: testNio
 * 
 * @Author: chenkangxian
 * 
 * @Annotation: 使用线程池来处理大量channel并发
 * 
 * @Date:2011-7-5
 * 
 * @Copyright: 2011 chenkangxian, All rights reserved.
 * 
 */
public class SelectSocketsThreadPool extends SelectSockets {

	private static final int MAX_THREADS = 5;
	private ThreadPool pool = new ThreadPool(MAX_THREADS);

	/**
	 * 从socket中读数据
	 */
	protected void readDataFromSocket(SelectionKey key) throws Exception {

		WorkerThread worker = pool.getWorker();
		if (worker == null) {
			return;
		}

		worker.serviceChannel(key);
	}

	/**
	 * 
	 * @Project: concurrentnio
	 *
	 * @Author: chenkangxian
	 *
	 * @Annotation:线程池
	 *
	 * @Date:2011-7-20
	 *
	 * @Copyright: 2011 chenkangxian, All rights reserved.
	 *
	 */
	private class ThreadPool {

		List idle = new LinkedList();

		/**
		 * 线程池初始化
		 * 
		 * @param poolSize 线程池大小
		 */
		ThreadPool(int poolSize) {
			for (int i = 0; i < poolSize; i++) {
				WorkerThread thread = new WorkerThread(this);

				thread.setName("Worker" + (i + 1));
				thread.start();
				idle.add(thread);
			}
		}

		/**
		 * 获得工作线程
		 * 
		 * Author: chenkangxian
		 *
		 * Last Modification Time: 2011-7-20
		 *
		 * @return
		 */
		WorkerThread getWorker() {
			WorkerThread worker = null;

			synchronized (idle) {
				if (idle.size() > 0) {
					worker = (WorkerThread) idle.remove(0);
				}
			}

			return (worker);
		}

		/**
		 * 送回工作线程
		 * 
		 * Author: chenkangxian
		 *
		 * Last Modification Time: 2011-7-20
		 *
		 * @param worker
		 */
		void returnWorker(WorkerThread worker) {
			synchronized (idle) {
				idle.add(worker);
			}
		}
	}

	private class WorkerThread extends Thread {

		private ByteBuffer buffer = ByteBuffer.allocate(1024);
		private ThreadPool pool;
		private SelectionKey key;

		WorkerThread(ThreadPool pool) {
			this.pool = pool;
		}

		public synchronized void run() {
			System.out.println(this.getName() + " is ready");
			while (true) {
				try {
					this.wait();// 等待被notify
				} catch (InterruptedException e) {
					e.printStackTrace();
					this.interrupt();
				}

				if (key == null) {// 直到有key
					continue;
				}

				System.out.println(this.getName() + " has been awakened");

				try {
					drainChannel(key);
				} catch (Exception e) {
					System.out.println("Caught '" + e + "' closing channel");

					try {
						key.channel().close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}

					key.selector().wakeup();
				}

				key = null;

				this.pool.returnWorker(this);
			}
		}

		synchronized void serviceChannel(SelectionKey key) {
			this.key = key;

			// 消除读的关注
			key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
			this.notify();
		}

		void drainChannel(SelectionKey key) throws Exception {

			SocketChannel channel = (SocketChannel) key.channel();
			int count;

			buffer.clear();

			while ((count = channel.read(buffer)) > 0) {
				buffer.flip();

				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}

				buffer.clear();
			}

			if (count < 0) {
				channel.close();
				return;
			}

			// 重新开始关注读事件
			key.interestOps(key.interestOps() | SelectionKey.OP_READ);

			key.selector().wakeup();
		}

	}

	public static void main(String[] args) throws Exception {

		new SelectSocketsThreadPool().go(args);

	}
}