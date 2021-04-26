package zhujie;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.utils.ScheduledThreadPoolExectuorWithDynamicSize;

public class a {
	public static void main(String[] args) throws InterruptedException {
		// =========准备一个动态属性==========
		DynamicIntProperty poolCoreSize = DynamicPropertyFactory.getInstance().getIntProperty("myThreadPoolCoreSize",
				2);
		ThreadFactory threadFactory = new ThreadFactory() {
			private AtomicInteger count = new AtomicInteger();

			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r);
				thread.setName("myThreadPrefix-" + count.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}
		};

		ScheduledThreadPoolExectuorWithDynamicSize exectuor = new ScheduledThreadPoolExectuorWithDynamicSize(
				poolCoreSize, threadFactory);

		// 启动3个定时任务（默认coreSize是2哦）
		for (int i = 1; i <= 3; i++) {
			int index = i;
			exectuor.scheduleAtFixedRate(() -> {
				String currThreadName = Thread.currentThread().getName();
				int corePoolSize = exectuor.getCorePoolSize();
				System.out.printf("我是%s号任务，线程名是[%s]，线程池核心数是：%s\n", index, currThreadName, corePoolSize);
			}, index, 3, TimeUnit.SECONDS);
		}

		// 阻塞主线程
		TimeUnit.MINUTES.sleep(100);
	}
}
