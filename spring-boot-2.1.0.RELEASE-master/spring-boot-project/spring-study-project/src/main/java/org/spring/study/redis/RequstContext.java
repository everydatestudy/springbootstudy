package org.spring.study.redis;

public class RequstContext implements Runnable {
	private RequestLocal parent;
	Runnable actual;

	public RequstContext(Runnable run) {
		this.parent = RequestLocal.getContextForCurrentThread();
		this.actual = run;
	}

	@Override
	public void run() {

		RequestLocal existingState = RequestLocal.getContextForCurrentThread();
		try {
			// set the state of this thread to that of its parent
			RequestLocal.setContextOnCurrentThread(parent);
			// execute actual Callable with the state of the parent
			try {
				actual.run();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} finally {
			// restore this thread back to its original state
			RequestLocal.setContextOnCurrentThread(existingState);
		}

	}

}
