package org.spring.boot.event;

public class MockSpringEventTest {

	public static void main(String[] args) throws InterruptedException {
		// 新建SimpleApplicationEventMulticaster对象，并添加容器生命周期监听器
		DemoApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
		eventMulticaster.addContextListener(new ContextStartEventListener());
		eventMulticaster.addContextListener(new ContextRunningEventListener());
		eventMulticaster.addContextListener(new ContextDestroyEventListener());

		((SimpleApplicationEventMulticaster) eventMulticaster).setAsync(true);

		// 发射容器启动事件ContextStartEvent
		eventMulticaster.multicastEvent(new ContextStartEvent());
		// 发射容器正在运行事件ContextRunningEvent
		eventMulticaster.multicastEvent(new ContextRunningEvent());
		// 发射容器正在运行事件ContextDestroyEvent
		eventMulticaster.multicastEvent(new ContextDestroyEvent());

	}

}
