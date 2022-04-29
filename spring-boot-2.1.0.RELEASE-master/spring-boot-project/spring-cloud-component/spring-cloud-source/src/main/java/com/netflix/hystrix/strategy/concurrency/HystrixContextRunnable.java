/**
 * Copyright 2012 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.strategy.concurrency;

import java.util.concurrent.Callable;

import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Wrapper around {@link Runnable} that manages the
 * {@link HystrixRequestContext} initialization and cleanup for the execution of
 * the {@link Runnable}T
 * 
 * @ExcludeFromJavadoc
 */
//请注意：这块代码并没有显示的将 YourBatman 从 main线程传递到子线程，也没有利用InheritableThreadLocal哦。它的执行步骤可描述如下：
//
//main初始化HystrixRequestContext，并且和此Context上下文完成绑定
//NAME_VARIABLE.set("YoutBatman")设置变量值，请注意：此变量值是处在HystrixRequestContext这个上下文里的哦，属于main线程的内容
//main线程初始化任务：使用的HystrixContextRunnable，所以该任务是和main线程的上下文绑定的
//执行任务时，先用main线程的Context来初始化上下文（所以它绑定的上下文和main线程的是同一个上下文）
//任务里使用NAME_VARIABLE.get()实际上是从main线程的上下文里拿数据，那必然可以取到呀
public class HystrixContextRunnable implements Runnable {

	private final Callable<Void> actual;
	// 父线程的上下文
	private final HystrixRequestContext parentThreadState;

	public HystrixContextRunnable(Runnable actual) {
		this(HystrixPlugins.getInstance().getConcurrencyStrategy(), actual);
	}

	// 这里使用了HystrixConcurrencyStrategy，它是一个SPI哦
	// 你还可以通过该SPI，自定义你的执行机制，非常靠谱有木有
	public HystrixContextRunnable(HystrixConcurrencyStrategy concurrencyStrategy, final Runnable actual) {
		this.actual = concurrencyStrategy.wrapCallable(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				// 实际执行的任务
				actual.run();
				return null;
			}

		});
		 // 父线程奶你**构造时**所处在的线程
		this.parentThreadState = HystrixRequestContext.getContextForCurrentThread();
	}

	@Override
	public void run() {
		HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
		try {
			// set the state of this thread to that of its parent
			HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
			// execute actual Callable with the state of the parent
			try {
				actual.call();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} finally {
			// restore this thread back to its original state
			HystrixRequestContext.setContextOnCurrentThread(existingState);
		}
	}

}
