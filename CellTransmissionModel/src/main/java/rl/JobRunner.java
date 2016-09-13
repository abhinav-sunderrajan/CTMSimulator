package rl;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * For parallelization.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class JobRunner implements Runnable {

	private Queue<Future<Double>> jobIdQueue;
	private Map<Integer, Double> resultMap;
	private int jobCount = 0;

	public JobRunner() {
		jobIdQueue = new ConcurrentLinkedQueue<Future<Double>>();
		resultMap = new ConcurrentHashMap<Integer, Double>();
	}

	@Override
	public void run() {
		while (true) {
			try {
				if (jobIdQueue.isEmpty()) {
					Thread.sleep(1);
					continue;
				}

				Future<Double> future = jobIdQueue.poll();
				Double delay = future.get();
				resultMap.put(future.hashCode(), delay);
				future.cancel(true);
				jobCount++;
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}

	}

	public void addJob(Future<Double> job) {
		jobIdQueue.add(job);
	}

	/**
	 * @return the resultMap
	 */
	public Map<Integer, Double> getResultMap() {
		return resultMap;
	}

	/**
	 * @return the jobIdQueue
	 */
	public Queue<Future<Double>> getJobIdQueue() {
		return jobIdQueue;
	}

	/**
	 * @return the jobCount
	 */
	public int getJobCount() {
		return jobCount;
	}

	/**
	 * @param jobCount
	 *            the jobCount to set
	 */
	public void setJobCount(int jobCount) {
		this.jobCount = jobCount;
	}

}