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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import main.SimulatorCore;

import org.la4j.Vector;
import org.la4j.vector.DenseVector;
import org.la4j.vector.functor.VectorFunction;

import simulator.CellTransmissionModel;
import strategy.WarmupCTM;
import utils.ThreadPoolExecutorService;
import ctm.Cell;

/**
 * A genetic algorithm implementation top get the VSL for our expressway
 * section.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class GAVSL {

	private Queue<List<Future<Double>>> futures;
	private Map<Integer, Vector> futuresMap;
	private Map<Vector, Double> populationFitnessMap;
	private ThreadPoolExecutor executor;
	private double meanPopulationFitness;
	private static SimulatorCore core;

	private static final Random RANDOM = new Random();
	private static final double CROSSOVER_PROB = 0.3;
	private static final int POPULATION_SIZE = 12;
	private static final int MAX_ITERS = 20;
	private static final int MU = 1;
	private static final double MUTATION_PROB = 0.1;
	private static final int TOURANAMENT_SIZE = 3;
	private static final double FREE_FLOW = 80.0;
	private static final double SPEED_MAX = 80 * 5.0 / 18;
	private static final double SPEED_MIN = 40 * 5.0 / 18;
	private static final int NSEEDS = 4;

	private class MutateGA implements VectorFunction {
		double mutateProb;

		MutateGA(double mutateProb) {
			this.mutateProb = mutateProb;
		}

		@Override
		public double evaluate(int i, double value) {

			double ret = value;
			if (RANDOM.nextDouble() < mutateProb) {
				double change = 50.0 / 18.0;
				if (RANDOM.nextDouble() < 0.5)
					change = change * -1.0;
				ret += change;
				if (ret > SPEED_MAX)
					ret = ret - 2.0 * change;
				if (ret < SPEED_MIN)
					ret = ret + 2.0 * change;

			}

			return ret;
		}
	};

	public GAVSL() {
		this.futures = new ConcurrentLinkedQueue<List<Future<Double>>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		futuresMap = new ConcurrentHashMap<Integer, Vector>();
		populationFitnessMap = new LinkedHashMap<Vector, Double>();
		meanPopulationFitness = 0.0;
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
					Vector speedLimits = futuresMap.get(futureSeeds.hashCode());
					double meanTTS = 0;

					for (Future<Double> future : futureSeeds)
						meanTTS += future.get();

					meanTTS = meanTTS / NSEEDS;
					populationFitnessMap.put(speedLimits, meanTTS);
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

	public static void main(String args[]) throws InterruptedException, ExecutionException {
		GAVSL gavsl = new GAVSL();
		core = SimulatorCore.getInstance(1);
		gavsl.executor.submit(gavsl.new AnalyzeTotalTimeSpent());

		Vector bestSolution = null;
		double bestFitness = Double.MAX_VALUE;
		gavsl.populationFitnessMap.clear();
		for (int i = 0; i < POPULATION_SIZE; i++) {
			double[] speedLimit = new double[SimulatorCore.getPieMainRoads().length];
			if (i == 0) {
				for (int sl = 0; sl < speedLimit.length; sl++) {
					speedLimit[sl] = FREE_FLOW * 5.0 / 18.0;
				}
			} else {
				int prev = -1;
				for (int sl = 0; sl < speedLimit.length; sl++) {
					Double freeFlowSpeed = FREE_FLOW;
					int max = freeFlowSpeed.intValue() / 10;
					int min = 4;
					int temp = -1;
					if (sl == 0) {
						temp = min + RANDOM.nextInt(max - min + 1);
					} else {
						temp = RANDOM.nextDouble() < 0.5 ? (prev - 1) : (prev + 1);
						if (temp < min)
							temp = temp + 2;
						if (temp > max)
							temp = temp - 2;
					}
					prev = temp;
					speedLimit[sl] = temp * 10 * 5.0 / 18.0;
				}

			}

			gavsl.populationFitnessMap.put(DenseVector.fromArray(speedLimit), Double.MAX_VALUE);
		}
		System.out.println("Mean-Fitness\titeration");
		for (int iter = 0; iter < MAX_ITERS; iter++) {
			// Achieve the initial warm up state.
			Set<String> cellState = WarmupCTM.initializeCellState(core);
			// Analyze the fitness the of the population
			for (Entry<Vector, Double> entry : gavsl.populationFitnessMap.entrySet()) {
				if (entry.getValue() == Double.MAX_VALUE) {
					List<Future<Double>> futureSeeds = new ArrayList<Future<Double>>();
					for (int seed = 0; seed < NSEEDS; seed++) {
						core.getRandom().setSeed(RANDOM.nextLong());
						Vector speedLimits = entry.getKey();
						CellTransmissionModel ctm = new CellTransmissionModel(core, false, false,
								false, 1500);
						ctm.intializeTrafficState(cellState);
						int limit = 0;
						for (int roadId : SimulatorCore.PIE_MAIN_ROADS) {
							int segment = 0;
							while (true) {
								Cell cell = ctm.getCellNetwork().getCellMap()
										.get(roadId + "_" + segment);
								if (cell == null)
									break;
								cell.setFreeFlowSpeed(speedLimits.get(limit));
								segment++;
							}
							limit++;
						}

						futureSeeds.add(gavsl.executor.submit(ctm));
					}
					gavsl.futures.add(futureSeeds);
					gavsl.futuresMap.put(futureSeeds.hashCode(), entry.getKey());
				}
			}

			while (true) {
				if (gavsl.futuresMap.size() == 0)
					break;
				else
					Thread.sleep(1);
			}

			gavsl.populationFitnessMap = sortByValue(gavsl.populationFitnessMap);
			gavsl.meanPopulationFitness = 0.0;
			for (Double fitness : gavsl.populationFitnessMap.values())
				gavsl.meanPopulationFitness += fitness;
			gavsl.meanPopulationFitness /= POPULATION_SIZE;

			if (iter == (MAX_ITERS - 1))
				break;
			// Create new generation.

			Map<Vector, Double> newGen = new LinkedHashMap<Vector, Double>();
			// /Retain the best MU values from the previous generation. Also
			// remember yours is a stochastic simulation hence no point
			// retaining the old fitness value.
			int num = 1;
			for (Entry<Vector, Double> entry : gavsl.populationFitnessMap.entrySet()) {
				if (num == 1) {
					if (entry.getValue() < bestFitness) {
						bestFitness = entry.getValue();
						bestSolution = entry.getKey();
					}
				}

				if (num == MU)
					break;
				newGen.put(entry.getKey(), entry.getValue());
				num++;
			}

			while (newGen.size() < POPULATION_SIZE) {
				Map<Vector, Double> newChild = gavsl.tournamentSelection(bestFitness);
				for (Entry<Vector, Double> entry : newChild.entrySet()) {
					if (!newGen.containsKey(entry.getKey()))
						newGen.put(entry.getKey(), Double.MAX_VALUE);
				}
			}

			System.out.println(gavsl.meanPopulationFitness + "\t" + iter);
			gavsl.populationFitnessMap = newGen;
		}

		System.out.println("fitness:" + bestFitness + " config: " + bestSolution.toCSV());
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
	 * @param bestFitness
	 * 
	 * @return the cross-overed and mutated children.
	 */
	private Map<Vector, Double> tournamentSelection(double bestFitness) {
		Map<Vector, Double> parents = new LinkedHashMap<Vector, Double>();

		while (parents.size() < 2) {
			Vector best = null;
			double fitness = Double.MAX_VALUE;

			for (int i = 0; i < TOURANAMENT_SIZE; i++) {
				int select = RANDOM.nextInt(POPULATION_SIZE);
				int k = 0;
				for (Entry<Vector, Double> entry : populationFitnessMap.entrySet()) {
					if (k == select) {
						if (entry.getValue() < fitness) {
							Vector key = entry.getKey();
							best = DenseVector.fromCSV(key.toCSV());
							fitness = entry.getValue();
						}
						break;
					}
					k++;
				}
			}
			parents.put(best, fitness);
		}

		crossOverAndMutate(parents, bestFitness);
		return parents;
	}

	/**
	 * Implement Uniform crossover and mutation using Gaussian convolution.
	 * 
	 * @param parents
	 * @param bestFitness
	 */
	private void crossOverAndMutate(Map<Vector, Double> parents, double bestFitness) {
		Vector parent1 = null;
		Vector parent2 = null;
		for (Vector parent : parents.keySet()) {
			if (parent1 == null)
				parent1 = parent;
			else
				parent2 = parent;
		}

		if (parent1.length() != parent2.length()) {
			throw new IllegalArgumentException("Size of the chromosomes is worng");
		}

		// crossover probability
		double cop = CROSSOVER_PROB * (parents.get(parent1) - bestFitness)
				/ (meanPopulationFitness - bestFitness);

		// Mutation probability of parent 2
		final double mup2 = MUTATION_PROB * (parents.get(parent2) - bestFitness)
				/ (meanPopulationFitness - bestFitness);

		// Mutation probability of parent 1
		final double mup1 = MUTATION_PROB * (parents.get(parent1) - bestFitness)
				/ (meanPopulationFitness - bestFitness);

		// cross over
		for (int i = 0; i < parent1.length(); i++) {
			if (RANDOM.nextDouble() < cop) {
				double temp = parent1.get(i);
				parent1.set(i, parent2.get(i));
				parent2.set(i, temp);
			}
		}

		// Actual mutation

		parent1 = parent1.transform(new MutateGA(mup1));
		parent2 = parent2.transform(new MutateGA(mup2));

	}

}
