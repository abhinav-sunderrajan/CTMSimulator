package gamain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import main.SimulatorCore;

import simulator.CellTransmissionModel;
import simulator.RampMeter;
import utils.ThreadPoolExecutorService;

/**
 * A GA implementation for controlling ramp flow rates.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class PIERampMeterOptimize {

	private Queue<Future<Integer>> futures;
	private Map<Integer, List<Double>> futuresMap;
	private Map<List<Double>, Integer> populationFitnessMap;
	private ThreadPoolExecutor executor;
	private Random random;
	private double crossOverrate;
	private static final int POPULATION_SIZE = 25;
	private static final int MAX_ITERS = 40;
	private static final int NUM_OF_RAMPS = 11;
	private static final int MU = 3;
	private static final double MUTATE_PROB = 0.07;
	private static final int TOURANAMENT_SIZE = 3;

	public PIERampMeterOptimize() {
		this.futures = new ConcurrentLinkedQueue<Future<Integer>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		random = SimulatorCore.random;
		futuresMap = new ConcurrentHashMap<Integer, List<Double>>();
		populationFitnessMap = new LinkedHashMap<List<Double>, Integer>();
		this.crossOverrate = 0.5;

	}

	private class AnalyzeTotalTimeSpent implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					if (futures.isEmpty()) {
						Thread.sleep(1);
						continue;
					}

					Future<Integer> future = futures.poll();
					List<Double> queuepercentages = futuresMap.get(future.hashCode());
					Integer tts = future.get();
					boolean noRampMetering = true;
					for (double percentage : queuepercentages) {
						if (percentage > 0) {
							noRampMetering = false;
							break;
						}
					}

					if (noRampMetering)
						System.out.println("No ramp metering case TTS:" + tts);

					populationFitnessMap.put(queuepercentages, tts);
					futuresMap.remove(future.hashCode());
					future.cancel(true);

				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}

		}
	}

	public static void main(String args[]) throws InterruptedException {
		PIERampMeterOptimize pieOptimize = new PIERampMeterOptimize();
		pieOptimize.executor.submit(pieOptimize.new AnalyzeTotalTimeSpent());

		// Initialize the a population
		for (int i = 0; i < POPULATION_SIZE; i++) {
			List<Double> queuePercentageArr = new ArrayList<>();
			for (int j = 0; j < NUM_OF_RAMPS; j++) {
				if (i == 0)
					queuePercentageArr.add(0.0);
				else {
					double val = pieOptimize.random.nextDouble() * 0.8;
					queuePercentageArr.add(Math.round(val * 100.0) / 100.0);
				}
			}
			pieOptimize.populationFitnessMap.put(queuePercentageArr, Integer.MAX_VALUE);
		}

		for (int iter = 0; iter < MAX_ITERS; iter++) {
			// Analyze the fitness the of the population
			for (Entry<List<Double>, Integer> entry : pieOptimize.populationFitnessMap.entrySet()) {
				if (entry.getValue() == Integer.MAX_VALUE) {
					CellTransmissionModel ctm = new CellTransmissionModel(
							SimulatorCore.pieChangi.values(), false, true, false, false, 900);
					List<Double> queuePercentages = entry.getKey();
					int index = 0;
					for (RampMeter meter : ctm.getMeteredRamps().values()) {
						meter.setQueuePercentage(queuePercentages.get(index));
						index++;
					}
					Future<Integer> future = pieOptimize.executor.submit(ctm);
					pieOptimize.futures.add(future);
					pieOptimize.futuresMap.put(future.hashCode(), entry.getKey());

				}
			}

			while (true) {
				if (pieOptimize.futuresMap.size() == 0)
					break;
				else
					Thread.sleep(1);
			}

			pieOptimize.populationFitnessMap = sortByValue(pieOptimize.populationFitnessMap);

			if (iter == (MAX_ITERS - 1))
				break;
			// Create new generation.

			Map<List<Double>, Integer> newGen = new LinkedHashMap<List<Double>, Integer>();
			// /Retain the best MU values from the previous generation.
			int num = 0;
			for (Entry<List<Double>, Integer> entry : pieOptimize.populationFitnessMap.entrySet()) {
				newGen.put(entry.getKey(), entry.getValue());
				num++;
				if (num == MU)
					break;
			}

			while (newGen.size() < POPULATION_SIZE) {
				Map<List<Double>, Integer> newChild = pieOptimize.tournamentSelection();
				for (Entry<List<Double>, Integer> entry : newChild.entrySet())
					newGen.put(entry.getKey(), entry.getValue());
			}

			pieOptimize.populationFitnessMap = newGen;
			System.out.println("Evaluated generation " + iter);

		}

		for (Entry<List<Double>, Integer> entry : pieOptimize.populationFitnessMap.entrySet()) {
			System.out.println("\n Employing the best ramp metering strategy\n");
			System.out.println("fitness:" + entry.getValue() + " config: " + entry.getKey());
			CellTransmissionModel ctmBestConfig = new CellTransmissionModel(
					SimulatorCore.pieChangi.values(), false, true, false, true, 1000);
			List<Double> queuePercentages = entry.getKey();
			int index = 0;
			for (RampMeter meter : ctmBestConfig.getMeteredRamps().values()) {
				meter.setQueuePercentage(queuePercentages.get(index));
				index++;
			}
			Future<Integer> future = pieOptimize.executor.submit(ctmBestConfig);
			pieOptimize.futures.add(future);
			pieOptimize.futuresMap.put(future.hashCode(), entry.getKey());

			break;
		}

	}

	/**
	 * Sort a map by value
	 * 
	 * @param map
	 *            the map to be sorted by value;
	 * @return map sorted by value
	 */
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
			@Override
			public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
				return (o1.getValue()).compareTo(o2.getValue());
			}
		});

		Map<K, V> result = new LinkedHashMap<>();
		for (Map.Entry<K, V> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	/**
	 * Tournament selection for the new generation parents
	 * 
	 * @return the cross-overed and mutated children.
	 */
	private Map<List<Double>, Integer> tournamentSelection() {
		Map<List<Double>, Integer> parents = new LinkedHashMap<List<Double>, Integer>();

		while (parents.size() < 2) {
			List<Double> best = new ArrayList<Double>();
			int fitness = Integer.MAX_VALUE;

			for (int i = 0; i < TOURANAMENT_SIZE; i++) {
				int select = SimulatorCore.random.nextInt(POPULATION_SIZE);
				int k = 0;
				for (Entry<List<Double>, Integer> entry : populationFitnessMap.entrySet()) {
					if (k == select) {
						if (entry.getValue() < fitness) {
							best.clear();
							best.addAll(entry.getKey());
							fitness = entry.getValue();
						}
						break;
					}
					k++;
				}
			}
			parents.put(best, fitness);
		}

		crossOverAndMutate(parents);
		return parents;
	}

	/**
	 * Implement Uniform crossover and mutation using Gaussian convolution.
	 * 
	 * @param parents
	 */
	private void crossOverAndMutate(Map<List<Double>, Integer> parents) {
		List<Double> parent1 = null;
		List<Double> parent2 = null;
		for (List<Double> parent : parents.keySet()) {
			if (parent1 == null)
				parent1 = parent;
			else
				parent2 = parent;
			parents.put(parent, Integer.MAX_VALUE);
		}

		if (!(parent1.size() == NUM_OF_RAMPS && parent2.size() == NUM_OF_RAMPS)) {
			throw new IllegalArgumentException("Size of the chromosomes is worng");
		}

		// Uniform crossover
		for (int i = 0; i < parent1.size(); i++) {
			if (SimulatorCore.random.nextDouble() < crossOverrate) {
				double temp = parent1.get(i);
				parent1.set(i, parent2.get(i));
				parent2.set(i, temp);
			}
		}

		// Mutation of parent 1

		for (int i = 0; i < parent1.size(); i++) {
			if (SimulatorCore.random.nextDouble() < MUTATE_PROB) {
				while (true) {
					double temp = parent1.get(i) + SimulatorCore.random.nextGaussian();
					temp = Math.round(temp * 100.0) / 100.0;
					if (temp >= 0 && temp <= 0.8) {
						parent1.set(i, temp);
						break;
					}
				}
			}

		}

		// Mutation of parent 2
		for (int i = 0; i < parent2.size(); i++) {
			if (SimulatorCore.random.nextDouble() < MUTATE_PROB) {
				while (true) {
					double temp = parent2.get(i) + SimulatorCore.random.nextGaussian();
					temp = Math.round(temp * 100.0) / 100.0;
					if (temp >= 0 && temp <= 0.8) {
						parent2.set(i, temp);
						break;
					}
				}
			}

		}

	}
}
