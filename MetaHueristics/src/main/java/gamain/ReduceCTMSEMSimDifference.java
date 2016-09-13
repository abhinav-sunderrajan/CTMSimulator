package gamain;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import simulator.SimulationConstants;
import utils.ThreadPoolExecutorService;

/**
 * Uses G.A to reduce the difference between SEMSim and CTM by varying the CTM
 * parameters namely, merge-priorities, lane drop and ramp merging terms.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class ReduceCTMSEMSimDifference {
	private Queue<Future<Double>> futures;
	private Map<Integer, List<Double>> futuresMap;
	private Map<List<Double>, Integer> populationFitnessMap;
	private ThreadPoolExecutor executor;
	private static Random random;
	private double crossOverrate;
	private static final int POPULATION_SIZE = 25;
	private static final int MAX_ITERS = 35;
	private static final int MU = 3;
	private static final double MUTATE_PROB = 0.1;
	private static final int TOURANAMENT_SIZE = 3;
	private static final double ramp_min = 0.1;
	private static final double ramp_max = 1.0;
	private static final double pie_min = 0.7;
	private static final double pie_max = 1.0;
	private static final double phi_min = 1.8;
	private static final double phi_max = 3.6;
	private static final double delta_min = 0.02;
	private static final double delta_max = 1.0;
	private static final DecimalFormat df = new DecimalFormat("#.##");

	private static final int[] roadArr = { 30634, 30635, 30636, 30637, 30638, 30639, 30640, 30641,
			37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650, 30651,
			30580, 30581 };

	public ReduceCTMSEMSimDifference() {
		this.futures = new ConcurrentLinkedQueue<Future<Double>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		random = new Random();
		futuresMap = new ConcurrentHashMap<Integer, List<Double>>();
		populationFitnessMap = new LinkedHashMap<List<Double>, Integer>();
		this.crossOverrate = 0.5;

	}

	/**
	 * 
	 * @author abhinav.sunderrajan
	 * 
	 */
	private class CTMSEMSimSimilarity implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					if (futures.isEmpty()) {
						Thread.sleep(1);
						continue;
					}

					Future<Double> future = futures.poll();
					List<Double> mergepriorities = futuresMap.get(future.hashCode());
					Integer tts = future.get().intValue();

					populationFitnessMap.put(mergepriorities, tts);
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
		ReduceCTMSEMSimDifference ga = new ReduceCTMSEMSimDifference();
		SimulatorCore core = SimulatorCore.getInstance(1);

		List<Integer> pieList = new ArrayList<>();
		for (Integer roadId : roadArr)
			pieList.add(roadId);

		ga.executor.submit(ga.new CTMSEMSimSimilarity());

		// Initialize the a population
		for (int i = 0; i < POPULATION_SIZE; i++) {
			List<Double> parameters = new ArrayList<>();
			for (int j = 0; j < 4; j++) {
				double val = -1;
				double min = -1;
				double max = -1;

				if (j == 0) {
					min = ramp_min;
					max = ramp_max;
				} else if (j == 1) {
					min = pie_min;
					max = pie_max;
				} else if (j == 2) {
					min = phi_min;
					max = phi_max;
				} else {
					min = delta_min;
					max = delta_max;
				}

				val = min + random.nextDouble() * (max - min);
				parameters.add(Double.parseDouble(df.format(val)));

			}
			ga.populationFitnessMap.put(parameters, Integer.MAX_VALUE);

		}

		for (int iter = 0; iter < MAX_ITERS; iter++) {
			// Analyze the fitness the of the population
			for (Entry<List<Double>, Integer> entry : ga.populationFitnessMap.entrySet()) {
				if (entry.getValue() == Integer.MAX_VALUE) {
					List<Double> mergePriorities = entry.getKey();
					for (Integer roadId : core.getMergePriorities().keySet()) {
						if (pieList.contains(roadId))
							core.getMergePriorities().put(roadId, mergePriorities.get(1));
						else
							core.getMergePriorities().put(roadId, mergePriorities.get(0));

					}

					SimulationConstants.PHI = mergePriorities.get(2);
					SimulationConstants.RAMP_DELTA = mergePriorities.get(3);

					CellTransmissionModel ctm = new CellTransmissionModel(core, false, false,
							false, 2200);
					Future<Double> future = ga.executor.submit(ctm);
					ga.futures.add(future);
					ga.futuresMap.put(future.hashCode(), entry.getKey());

				}
			}

			while (true) {
				if (ga.futuresMap.size() == 0)
					break;
				else
					Thread.sleep(10);
			}

			ga.populationFitnessMap = sortByValue(ga.populationFitnessMap);

			if (iter == (MAX_ITERS - 1))
				break;
			// Create new generation.

			Map<List<Double>, Integer> newGen = new HashMap<List<Double>, Integer>();
			// /Retain the best MU values from the previous generation.
			int num = 0;
			for (Entry<List<Double>, Integer> entry : ga.populationFitnessMap.entrySet()) {
				newGen.put(entry.getKey(), entry.getValue());
				num++;
				if (num == MU)
					break;
			}

			while (newGen.size() < POPULATION_SIZE) {
				Map<List<Double>, Integer> newChild = ga
						.tournamentSelection(SimulatorCore.SIMCORE_RANDOM);
				for (Entry<List<Double>, Integer> entry : newChild.entrySet())
					newGen.put(entry.getKey(), entry.getValue());
			}

			ga.populationFitnessMap = newGen;
			System.out.println("Evaluated generation " + iter);

		}

		System.out.println("\n Last generation\n");
		for (Entry<List<Double>, Integer> entry : ga.populationFitnessMap.entrySet()) {
			System.out.println("fitness:" + entry.getValue() + " config: " + entry.getKey());
		}

		ga.executor.shutdown();

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
	private Map<List<Double>, Integer> tournamentSelection(Random rand) {
		Map<List<Double>, Integer> parents = new LinkedHashMap<List<Double>, Integer>();

		while (parents.size() < 2) {
			List<Double> best = new ArrayList<Double>();
			int fitness = Integer.MAX_VALUE;

			for (int i = 0; i < TOURANAMENT_SIZE; i++) {
				int select = rand.nextInt(POPULATION_SIZE);
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

		crossOverAndMutate(parents, rand);
		return parents;
	}

	/**
	 * Implement Uniform crossover and mutation using Gaussian convolution.
	 * 
	 * @param parents
	 */
	private void crossOverAndMutate(Map<List<Double>, Integer> parents, Random rand) {
		List<Double> parent1 = null;
		List<Double> parent2 = null;
		for (List<Double> parent : parents.keySet()) {
			if (parent1 == null)
				parent1 = parent;
			else
				parent2 = parent;
			parents.put(parent, Integer.MAX_VALUE);
		}

		if (!(parent1.size() == 4 && parent2.size() == 4)) {
			throw new IllegalArgumentException("Size of the chromosomes is worng");
		}

		// Uniform crossover
		for (int i = 0; i < parent1.size(); i++) {
			if (rand.nextDouble() < crossOverrate) {
				double temp = parent1.get(i);
				parent1.set(i, parent2.get(i));
				parent2.set(i, temp);
			}
		}

		if (random.nextDouble() < MUTATE_PROB)
			mutate(parent1);
		if (random.nextDouble() < MUTATE_PROB)
			mutate(parent2);

	}

	/**
	 * Mutate the chromosome
	 * 
	 * @param chromosome
	 *            to be mutated
	 */
	private void mutate(List<Double> parent) {
		for (int i = 0; i < parent.size(); i++) {
			double max = 0.0;
			double min = 0.0;
			if (i == 0) {
				min = ramp_min;
				max = ramp_max;

			} else if (i == 1) {
				min = pie_min;
				max = pie_max;

			} else if (i == 2) {
				min = phi_min;
				max = phi_max;

			} else {
				min = delta_min;
				max = delta_max;
			}

			while (true) {
				double temp = parent.get(i) + random.nextGaussian();
				temp = Math.round(temp * 100.0) / 100.0;
				if (temp >= min && temp <= max) {
					parent.set(i, temp);
					break;
				}
			}

		}
	}

}
