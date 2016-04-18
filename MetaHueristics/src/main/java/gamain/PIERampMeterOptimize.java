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

import org.apache.log4j.Logger;

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

	private Queue<List<Future<Double>>> futures;
	private Map<Integer, List<Double>> futuresMap;
	private Map<List<Double>, Double> populationFitnessMap;
	private ThreadPoolExecutor executor;
	private static Random randomGA;
	private double crossOverrate;
	private static final int POPULATION_SIZE = 14;
	private static final int MAX_ITERS = 72;
	private static final int NUM_OF_RAMPS = 11;
	private static final int MU = 3;
	private static double mutationprobability = 0.25;
	private static final int TOURANAMENT_SIZE = 3;
	private static final Logger LOGGER = Logger.getLogger(PIERampMeterOptimize.class);

	public PIERampMeterOptimize() {
		this.futures = new ConcurrentLinkedQueue<List<Future<Double>>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		randomGA = new Random();
		futuresMap = new ConcurrentHashMap<Integer, List<Double>>();
		populationFitnessMap = new LinkedHashMap<List<Double>, Double>();
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

					List<Future<Double>> futureSeeds = futures.poll();
					List<Double> queuepercentages = futuresMap.get(futureSeeds.hashCode());
					double meanTTS = 0;

					for (Future<Double> future : futureSeeds)
						meanTTS += future.get();

					meanTTS /= futureSeeds.size();
					boolean noRampMetering = true;
					for (double percentage : queuepercentages) {
						if (percentage > 0) {
							noRampMetering = false;
							break;
						}
					}

					if (noRampMetering)
						System.out.println("No ramp metering case TTS:" + meanTTS);

					populationFitnessMap.put(queuepercentages, meanTTS);
					futuresMap.remove(futureSeeds.hashCode());
					for (Future<Double> future : futureSeeds)
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

		List<Double> bestSolution = null;
		double bestFitness = Double.MAX_VALUE;
		pieOptimize.populationFitnessMap.clear();
		for (int i = 0; i < POPULATION_SIZE; i++) {
			List<Double> queuePercentageArr = new ArrayList<>();
			for (int j = 0; j < NUM_OF_RAMPS; j++) {
				if (i == 0)
					queuePercentageArr.add(0.0);
				else {
					double val = randomGA.nextDouble() * 0.8;
					queuePercentageArr.add(Math.round(val * 100.0) / 100.0);
				}
			}
			pieOptimize.populationFitnessMap.put(queuePercentageArr, Double.MAX_VALUE);
		}

		for (int iter = 0; iter < MAX_ITERS; iter++) {
			// Analyze the fitness the of the population
			for (Entry<List<Double>, Double> entry : pieOptimize.populationFitnessMap.entrySet()) {
				if (entry.getValue() == Double.MAX_VALUE) {
					List<Future<Double>> futureSeeds = new ArrayList<Future<Double>>();
					for (int seed = 0; seed < 8; seed++) {
						SimulatorCore.random.setSeed(randomGA.nextLong());
						CellTransmissionModel ctm = new CellTransmissionModel(
								SimulatorCore.pieChangi.values(), false, true, false, false, 1900);
						List<Double> queuePercentages = entry.getKey();
						int index = 0;
						for (RampMeter meter : ctm.getMeteredRamps().values()) {
							meter.setQueuePercentage(queuePercentages.get(index));
							index++;
						}
						futureSeeds.add(pieOptimize.executor.submit(ctm));
					}
					pieOptimize.futures.add(futureSeeds);
					pieOptimize.futuresMap.put(futureSeeds.hashCode(), entry.getKey());

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
			System.out.println("Evaluated generation " + iter);

			Map<List<Double>, Double> newGen = new LinkedHashMap<List<Double>, Double>();
			// /Retain the best MU values from the previous generation. Also
			// remember yours is a stochastic simulation hence no point
			// retaining the old fitness value.
			int num = 1;
			for (Entry<List<Double>, Double> entry : pieOptimize.populationFitnessMap.entrySet()) {
				if (num == 1) {
					if (bestFitness > entry.getValue()) {
						bestFitness = entry.getValue();
						bestSolution = entry.getKey();
					}
					if (iter % 10 == 0)
						System.out.println("fitness:" + bestFitness + " config: " + bestSolution);
				}
				newGen.put(entry.getKey(), entry.getValue());

				if (num == MU)
					break;
				num++;
			}

			while (newGen.size() < POPULATION_SIZE) {
				Map<List<Double>, Double> newChild = pieOptimize.tournamentSelection();
				for (Entry<List<Double>, Double> entry : newChild.entrySet()) {
					if (!newGen.containsKey(entry.getKey()))
						newGen.put(entry.getKey(), entry.getValue());
				}
			}

			pieOptimize.populationFitnessMap = newGen;
			mutationprobability = mutationprobability - (0.12) / (MAX_ITERS - 1);

		}

		LOGGER.info("fitness:" + bestFitness + " config: " + bestSolution);
		// CellTransmissionModel ctmBestConfig = new CellTransmissionModel(
		// SimulatorCore.pieChangi.values(), false, true, false, true,
		// 2000);
		// int index = 0;
		// for (RampMeter meter : ctmBestConfig.getMeteredRamps().values())
		// {
		// meter.setQueuePercentage(pieOptimize.bestSolution.get(index));
		// index++;
		// }
		// Future<Double> future =
		// pieOptimize.executor.submit(ctmBestConfig);
		// pieOptimize.futures.add(future);
		// pieOptimize.futuresMap.put(future.hashCode(),
		// pieOptimize.bestSolution);

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
	private Map<List<Double>, Double> tournamentSelection() {
		Map<List<Double>, Double> parents = new LinkedHashMap<List<Double>, Double>();

		while (parents.size() < 2) {
			List<Double> best = new ArrayList<Double>();
			double fitness = Double.MAX_VALUE;

			for (int i = 0; i < TOURANAMENT_SIZE; i++) {
				int select = SimulatorCore.random.nextInt(POPULATION_SIZE);
				int k = 0;
				for (Entry<List<Double>, Double> entry : populationFitnessMap.entrySet()) {
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
	private void crossOverAndMutate(Map<List<Double>, Double> parents) {
		List<Double> parent1 = null;
		List<Double> parent2 = null;
		for (List<Double> parent : parents.keySet()) {
			if (parent1 == null)
				parent1 = parent;
			else
				parent2 = parent;
			parents.put(parent, Double.MAX_VALUE);
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
			if (SimulatorCore.random.nextDouble() < mutationprobability) {
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
			if (SimulatorCore.random.nextDouble() < mutationprobability) {
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
