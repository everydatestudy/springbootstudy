package org.spring.boot.yanjiu;

import org.springframework.util.StopWatch;

interface test {
	void onstartup(StopWatch aa);
}

public class StopWatchTest {

	public static void main(String[] args) throws InterruptedException {
		StopWatch sw = new StopWatch("test");
		sw.start("task1");
		// do something
		Thread.sleep(100);
		sw.stop();
		sw.start("task2");
		// do something
		Thread.sleep(200);
		sw.stop();
		sw.start("task3");
		// do something
		Thread.sleep(2000);
		sw.stop();
		System.out.println("sw.prettyPrint()~~~~~~~~~~~~~~~~~");
		System.out.println(sw.prettyPrint());

	}

	public test getshow() {
		//return this.init("aa");
		return null;
	}

	public void init(StopWatch aa) {
		System.out.println("fdsafdsa");
	}
}
