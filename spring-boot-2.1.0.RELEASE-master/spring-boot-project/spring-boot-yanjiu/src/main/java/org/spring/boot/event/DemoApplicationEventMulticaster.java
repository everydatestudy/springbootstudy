package org.spring.boot.event;

public interface DemoApplicationEventMulticaster {
	void addContextListener(ContextListener<?> listener);

	void removeContextListener(ContextListener<?> listener);

	void removeAllListeners();

	void multicastEvent(AbstractContextEvent event);

}
