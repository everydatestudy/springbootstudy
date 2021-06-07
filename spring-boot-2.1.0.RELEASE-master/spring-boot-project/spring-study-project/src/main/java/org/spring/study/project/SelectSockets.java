package org.spring.study.project;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * 
 * @Project: concurrentnio
 *
 * @Author: chenkangxian
 *
 * @Annotation:
 *
 * @Date:2011-7-11
 *
 * @Copyright: 2011 chenkangxian, All rights reserved.
 *
 */
public class SelectSockets {

	public static int PORT_NUMBER = 1234;

	private ByteBuffer buffer = ByteBuffer.allocate(1024);

	public static void main(String[] args) throws Exception {

		new SelectSockets().go(args);

	}

	public void go(String[] args) throws Exception {

		int port = PORT_NUMBER;
//		if(args.length > 0){
//			port = Integer.parseInt(args[0]);
//		}
//		System.out.println("Listening on port " + port);

		ServerSocketChannel serverChannel = ServerSocketChannel.open();

		ServerSocket serverSocket = serverChannel.socket();

		Selector selector = Selector.open();

		serverSocket.bind(new InetSocketAddress(port));

		serverChannel.configureBlocking(false);

		serverChannel.register(selector, SelectionKey.OP_ACCEPT);

		while (true) {

			int n = selector.select(); // 没有轮询，单个selector

			if (n == 0) {
				continue;
			}

			Iterator it = selector.selectedKeys().iterator();

			while (it.hasNext()) {
				SelectionKey key = (SelectionKey) it.next();

				if (key.isAcceptable()) {
					ServerSocketChannel server = (ServerSocketChannel) key.channel();
					SocketChannel channel = server.accept();

					registerChannel(selector, channel, SelectionKey.OP_READ);

					sayHello(channel);
				}

				if (key.isReadable()) {
					readDataFromSocket(key);
				}

				it.remove();
			}
		}

	}

	/**
	 * 在selector上注册channel，并设置interest
	 * 
	 * Author: chenkangxian
	 *
	 * Last Modification Time: 2011-7-11
	 *
	 * @param selector 选择器
	 * 
	 * @param channel  通道
	 * 
	 * @param ops      interest
	 * 
	 * @throws Exception
	 */
	protected void registerChannel(Selector selector, SelectableChannel channel, int ops) throws Exception {

		if (channel == null) {
			return;
		}

		channel.configureBlocking(false);

		channel.register(selector, ops);
	}

	/**
	 * 处理有可用数据的通道
	 * 
	 * Author: chenkangxian
	 *
	 * Last Modification Time: 2011-7-11
	 *
	 * @param key 可用通道对应的key
	 * 
	 * @throws Exception
	 */
	protected void readDataFromSocket(SelectionKey key) throws Exception {

		SocketChannel socketChannel = (SocketChannel) key.channel();
		int count;

		buffer.clear(); // Empty buffer

		while ((count = socketChannel.read(buffer)) > 0) {

			buffer.flip();

			while (buffer.hasRemaining()) {
				socketChannel.write(buffer);
			}

			buffer.clear();

		}

		if (count < 0) {
			socketChannel.close();
		}

	}

	/**
	 * 打招呼
	 * 
	 * Author: chenkangxian
	 *
	 * Last Modification Time: 2011-7-11
	 *
	 * @param channel 客户端channel
	 * 
	 * @throws Exception
	 */
	private void sayHello(SocketChannel channel) throws Exception {

		buffer.clear();
		buffer.put("Hello 哈罗! \r\n".getBytes());
		buffer.flip();

		channel.write(buffer);
	}

}