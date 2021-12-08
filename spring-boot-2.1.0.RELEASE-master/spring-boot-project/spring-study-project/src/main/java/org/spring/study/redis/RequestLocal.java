package org.spring.study.redis;

public class RequestLocal {
	private static ThreadLocal<RequestLocal> requestVariables = new ThreadLocal<RequestLocal>();
	public User user;

	public static RequestLocal getContextForCurrentThread() {
		RequestLocal context = requestVariables.get();
		if (context != null && context.user != null) {
			// context.state can be null when context is not null
			// if a thread is being re-used and held a context previously, the context was
			// shut down
			// but the thread was not cleared
			return context;
		} else {
			return null;
		}
	}

	public static void setContextOnCurrentThread(RequestLocal state) {
		requestVariables.set(state);
	}

	/**
	 * Call this at the beginning of each request (from parent thread) to initialize
	 * the underlying context so that {@link HystrixRequestVariableDefault} can be
	 * used on any children threads and be accessible from the parent thread.
	 * <p>
	 * <b>NOTE: If this method is called then <code>shutdown()</code> must also be
	 * called or a memory leak will occur.</b>
	 * <p>
	 * See class header JavaDoc for example Servlet Filter implementation that
	 * initializes and shuts down the context.
	 */
	public static RequestLocal initializeContext() {
		RequestLocal state = new RequestLocal();
		state.user = new User();
		state.user.var = "bbbb";
		requestVariables.set(state);
		return state;
	}
}

class User {
	String var;
}
