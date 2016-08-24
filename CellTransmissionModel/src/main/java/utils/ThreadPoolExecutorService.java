package utils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * For a thread pool executor with a core pool size of as many threads as the
 * number of available processors.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class ThreadPoolExecutorService {

	// private ScheduledThreadPoolExecutor executor;
	private ThreadPoolExecutor executor;
	private static ThreadPoolExecutorService executorUtils;

	private ThreadPoolExecutorService() {

		ThreadFactory threadFactory = Executors.defaultThreadFactory();
		RejectedExecutionHandler handler = new RejectedExecutionHandler() {
			@Override
			public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
				System.out.println("Task Rejected : " + (r));
			}
		};

		executor = new ThreadPoolExecutor(6, 10, 100000, TimeUnit.SECONDS,
				new ArrayBlockingQueue<Runnable>(22000), threadFactory, handler);

		// executor = new
		// ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

			@Override
			public void run() {
				System.out.println("Shutting down executor");
				executor.shutdown();

			}
		}));

	}

	/**
	 * Returns a singleton instance.
	 * 
	 * @return
	 */
	public static ThreadPoolExecutorService getExecutorInstance() {
		if (executorUtils == null) {
			executorUtils = new ThreadPoolExecutorService();
		}

		return executorUtils;

	}

	/**
	 * @return the executor
	 */
	public ThreadPoolExecutor getExecutor() {
		return executor;
	}

}
