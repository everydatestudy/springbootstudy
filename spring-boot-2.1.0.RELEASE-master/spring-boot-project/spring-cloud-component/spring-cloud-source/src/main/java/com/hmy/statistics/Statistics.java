package com.hmy.statistics;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.netflix.stats.distribution.DataDistribution;
import com.netflix.stats.distribution.DataPublisher;

public class Statistics {
	// 新线程：监控（模拟页面监控）
	private static void monitor(DataDistribution accumulator) {
		new Thread(() -> {
			ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
			executorService.scheduleWithFixedDelay(() -> {
				System.out.println("=======时间：" + accumulator.getTimestamp() + "，统计值如下=======");
				System.out.println("统计周期：" + (accumulator.getSampleIntervalMillis() / 1000) + "s");
				System.out.println("样本数据个数：" + accumulator.getSampleSize());
				// 周期：startCollection到endCollection的时间差

				System.out.println("最大值：" + accumulator.getMaximum());
				System.out.println("最小值：" + accumulator.getMinimum());
				System.out.println("算术平均值：" + accumulator.getMean());
				System.out.println("各分位数对应值：" + Arrays.toString(accumulator.getPercentiles()));
			}, 8, 8, TimeUnit.SECONDS);
		}).start();
	}

	// 发布数据 5s发布一次数据
	private static void publishData(DataDistribution accumulator) {
		new Thread(() -> {
			new DataPublisher(accumulator, 5 * 1000).start();
		}).start();
	}

	// 新开一个线程生产数据
	private static void produceValue(DataDistribution accumulator) {
		new Thread(() -> {
			while (true) {
				accumulator.noteValue(randomValue(10, 2000));
				try {
					TimeUnit.MILLISECONDS.sleep(randomValue(10, 200));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	// 本地使用随机数模拟数据收集
	private static int randomValue(int min, int max) {
		return min + (int) (Math.random() * ((max - min) + 1));
	}

	public static void main(String[] args) throws InterruptedException {
		int bufferSize = 50; // 最大样本容量，注意是最大
		double[] percents = { 50, 80, 90, 95, 99 };
		DataDistribution accumulator = new DataDistribution(bufferSize, percents);
		// 生产数据
		produceValue(accumulator);
		// 发布数据
		publishData(accumulator);
		// 监控（模拟监控页面：数据打印到控制台）
		monitor(accumulator);

		// hold主线程
		TimeUnit.SECONDS.sleep(10000);
	}
}
