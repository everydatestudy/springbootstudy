package com.hmy.constructor;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class MySmartLifecycle implements SmartLifecycle {
	private volatile boolean running = false;

	/**
	 * 如果该`Lifecycle`类所在的上下文在调用`refresh`时,希望能够自己自动进行回调，则返回`true`* ,
	 * false的值表明组件打算通过显式的start()调用来启动，类似于普通的Lifecycle实现。
	 */
	@Override
	public boolean isAutoStartup() {
		return true;
	}

	/**
	 * 很多框架中的源码中，都会把真正逻辑写在stop()方法内。 比如quartz和Redis的spring支持包
	 *
	 * @param callback
	 */
	@Override
	public void stop(Runnable callback) {
		System.out.println("smartLifecycle stop runnable 容器停止...");
		stop();
		callback.run();
	}

	@Override
	public void start() {
		System.out.println("smartLifecycle 容器启动完成 ...");
		running = true;
	}

	@Override
	public void stop() {
		System.out.println("smartLifecycle stop 容器停止 ...");
		running = false;
	}

	@Override
	public boolean isRunning() {
		System.out.println("smartLifecycle 检查运行状态 ...");
		return running;
	}

	/**
	 * 阶段值 越小越靠前执行start()方法，越靠后执行stop()方法
	 *
	 * @return
	 */
	@Override
	public int getPhase() {
		return 0;
	}
}
