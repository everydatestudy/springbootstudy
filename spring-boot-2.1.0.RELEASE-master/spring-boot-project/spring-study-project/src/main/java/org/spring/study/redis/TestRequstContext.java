package org.spring.study.redis;

public class TestRequstContext {
	public static void main(String[] args) {
		RequestLocal state = new RequestLocal();
		state.user = new User();
		state.user.var = "ccaf";
		RequestLocal.setContextOnCurrentThread(state);

		Runnable run = () -> {
			RequestLocal bbb = RequestLocal.getContextForCurrentThread();
			System.out.println(bbb.user.var);
		};
		RequstContext context = new RequstContext(run);
		new Thread(context).start();
	}

}
