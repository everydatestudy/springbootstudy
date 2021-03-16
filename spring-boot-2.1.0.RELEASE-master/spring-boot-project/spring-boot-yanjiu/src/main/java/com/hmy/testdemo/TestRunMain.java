package com.hmy.testdemo;

public class TestRunMain {
	public static void main(String[] args) {
		show();
	}

	public static void show() {
		StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
		System.out.println(stackTrace[0]);
		
	}
}
