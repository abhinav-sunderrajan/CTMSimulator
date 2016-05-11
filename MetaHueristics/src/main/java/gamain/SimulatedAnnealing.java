package gamain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import main.SimulatorCore;
import simulator.CellTransmissionModel;
import simulator.RampMeter;
import utils.ThreadPoolExecutorService;

public class SimulatedAnnealing {

	private static ThreadPoolExecutor executor;
	private static Random randomGA;
	private static double temperature;
	private static final double MUTATION_PROB = 0.3;
	private static final int NUM_OF_RAMPS = 11;
	private static final int NSEEDS = 10;
	private static List<Double> tweakedCopy;
	private static SimulatorCore core;
	private static final int TEMP_MAX = 17000;
	private static final int ITER_MAX = 100;
	private static final double PENALTY = 200.0;
	private static List<Double> mutateProbList;

	public static void main(String args[]) throws InterruptedException, ExecutionException {
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		randomGA = new Random();
		tweakedCopy = new ArrayList<>();
		temperature = TEMP_MAX;

		core = SimulatorCore.getInstance(1);

		mutateProbList = new ArrayList<>();
		List<Double> queuePercentage = new ArrayList<>();

		for (int i = 0; i < NUM_OF_RAMPS; i++) {
			queuePercentage.add(0.0);
			mutateProbList.add(MUTATION_PROB);
		}

		double bestFitness = evaluateFitness(queuePercentage);
		List<Double> bestSolution = new ArrayList<>(queuePercentage);

		// System.out.println("0\t" + bestFitness + "\t" + bestSolution);
		double fitness = bestFitness;

		long t = System.currentTimeMillis();
		for (int iter = 0; iter <= ITER_MAX; iter++) {
			tweak(queuePercentage);
			double tweakFitness = evaluateFitness(tweakedCopy);
			if (tweakFitness < fitness) {
				queuePercentage = new ArrayList<>(tweakedCopy);
				fitness = tweakFitness;
			} else {
				double prob = Math.exp((fitness - tweakFitness) / temperature);
				if (randomGA.nextDouble() < prob) {
					queuePercentage = new ArrayList<>(tweakedCopy);
					fitness = tweakFitness;
				}

			}

			if (fitness < bestFitness && !bestSolution.equals(queuePercentage)) {
				double totalWeight = 0.0;
				for (int index = 0; index < bestSolution.size(); index++)
					totalWeight += Math.abs(bestSolution.get(index) - queuePercentage.get(index));

				for (int index = 0; index < bestSolution.size(); index++) {
					double weight = bestSolution.get(index) - queuePercentage.get(index);
					// double temp = (bestFitness - fitness) / bestFitness;
					double prob = MUTATION_PROB * (1 - Math.abs(weight) / totalWeight);
					prob = (prob <= 0.0) ? 0.05 : prob;
					mutateProbList.set(index, prob);
				}
				bestFitness = fitness;
				bestSolution = new ArrayList<Double>(queuePercentage);

			}

			temperature -= (TEMP_MAX / (1.3 * ITER_MAX));
			System.out.println(iter + "\t" + bestFitness + "\t" + bestSolution);
		}
		System.out.println("Exec time:" + (System.currentTimeMillis() - t));
		executor.shutdown();

	}

	private static double evaluateFitness(List<Double> queuePercentage)
			throws InterruptedException, ExecutionException {
		double meanFitness = 0.0;

		for (int i = 0; i < NSEEDS; i++) {
			core.getRandom().setSeed(randomGA.nextLong());
			CellTransmissionModel ctm = new CellTransmissionModel(core, true, true, false, false,
					2200);
			int index = 0;
			for (RampMeter meter : ctm.getMeteredRamps().values())
				meter.setQueuePercentage(queuePercentage.get(index++));
			Future<Double> future = executor.submit(ctm);
			meanFitness += Math.round(future.get());
		}

		meanFitness = meanFitness / NSEEDS;
		for (double qp : queuePercentage)
			meanFitness += qp * PENALTY;

		return meanFitness;

	}

	private static void tweak(List<Double> queuePercentage) {
		List<Double> tempList = new ArrayList<>(queuePercentage);

		for (int i = 0; i < queuePercentage.size(); i++) {
			if (randomGA.nextDouble() < mutateProbList.get(i)) {
				while (true) {
					double temp = queuePercentage.get(i) + randomGA.nextGaussian();
					temp = Math.round(temp * 100.0) / 100.0;
					if (temp >= 0 && temp <= 0.8) {
						tempList.set(i, temp);
						break;
					}
				}
			}

		}

		tweakedCopy = new ArrayList<>(tempList);
	}

}
