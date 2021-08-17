package org.spring.study.thread;

public class PhilosopherTest {

	public static void main(String[] args) {
		new Philosopher02("1", new Fork()).start();
		new Philosopher02("2", new Fork()).start();
		new Philosopher02("3", new Fork()).start();
		new Philosopher02("4", new Fork()).start();
		new Philosopher02("5", new Fork()).start();

	}

}
