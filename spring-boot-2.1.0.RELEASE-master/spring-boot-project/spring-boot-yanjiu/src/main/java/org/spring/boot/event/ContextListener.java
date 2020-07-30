package org.spring.boot.event;

public interface ContextListener<T extends AbstractContextEvent> extends EventListener {
	/**
	 * Handle an application event.
	 * 
	 * @param event the event to respond to
	 */
	void onApplicationEvent(T event);
}

class ContextStartEventListener implements ContextListener<AbstractContextEvent> {
	/**
	 * Handle an application event.
	 *
	 * @param event the event to respond to
	 */
	public void onApplicationEvent(AbstractContextEvent event) {
		if (event instanceof ContextStartEvent) {
			System.out.println("容器启动。。。，启动时间为：" + event.getTimestamp());
		}
	}
}

class ContextRunningEventListener implements ContextListener<AbstractContextEvent> {
	/**
	 * Handle an application event.
	 *
	 * @param event the event to respond to
	 */
	public void onApplicationEvent(AbstractContextEvent event) {
		if (event instanceof ContextRunningEvent) {
			System.out.println("容器开始运行。。。");
			try {
				Thread.sleep(3000);
				System.out.println("容器运行结束。。。");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}}

class ContextDestroyEventListener implements ContextListener<AbstractContextEvent>{

	/**
	 * Handle an application event.
	 *
	 * @param event the event to respond to
	 */
	public void onApplicationEvent(AbstractContextEvent event) {
		if (event instanceof ContextDestroyEvent) {
			System.out.println("容器销毁。。。，销毁时间为：" + event.getTimestamp());
		}
	}
}
