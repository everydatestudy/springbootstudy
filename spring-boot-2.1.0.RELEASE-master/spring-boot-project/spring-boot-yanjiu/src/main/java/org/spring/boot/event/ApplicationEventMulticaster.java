package org.spring.boot.event;

public interface ApplicationEventMulticaster {
	void addContextListener(ContextListener<?> listener);

	void removeContextListener(ContextListener<?> listener);

	void removeAllListeners();

	void multicastEvent(AbstractContextEvent event);

}
