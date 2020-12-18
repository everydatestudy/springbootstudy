package org.spring.boot.event;

public class AbstractContextEvent {
	private static final long serialVersionUID = -6159391039546783871L;

	private final long timestamp = System.currentTimeMillis();

	public final long getTimestamp() {
		return this.timestamp;
	}

}

class ContextStartEvent extends AbstractContextEvent {
}

class ContextRunningEvent extends AbstractContextEvent {
}

class ContextDestroyEvent extends AbstractContextEvent {
}