package cn.edu.tsinghua.iotdb.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class IoTThreadFactory implements ThreadFactory {

	private static final AtomicInteger poolNumber = new AtomicInteger(1);
	private final ThreadGroup group;
	private final AtomicInteger threadNumber = new AtomicInteger(1);
	private final String namePrefix;
	private Thread.UncaughtExceptionHandler handler = new IoTDBDefaultThreadExceptionHandler();

	public IoTThreadFactory(String poolName) {
		SecurityManager s = System.getSecurityManager();
		group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
		// thread pool name format : pool-number-IoTDB-poolName-thread-
		this.namePrefix = "pool-" + poolNumber.getAndIncrement() + "-IoTDB" + "-" + poolName + "-thread-";
	}

	public IoTThreadFactory(String poolName, Thread.UncaughtExceptionHandler handler) {
		this(poolName);
		this.handler = handler;
	}

	@Override
	public Thread newThread(Runnable r) {
		// thread name format : pool-number-IoTDB-poolName-thread-threadnum
		Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
		if (t.isDaemon())
			t.setDaemon(false);
		if (t.getPriority() != Thread.NORM_PRIORITY)
			t.setPriority(Thread.NORM_PRIORITY);
		t.setUncaughtExceptionHandler(handler);
		return t;
	}
}
