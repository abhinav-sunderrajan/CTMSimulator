package psomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import main.SimulatorCore;

import org.la4j.Vector;
import org.la4j.vector.DenseVector;
import org.la4j.vector.functor.VectorFunction;

import simulator.CellTransmissionModel;
import simulator.RampMeter;
import utils.ThreadPoolExecutorService;

/**
 * A particle swarm optimization for optimal ramp metering.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class PSORampMetering {

	private ThreadPoolExecutor executor;
	private static SimulatorCore core;
	private static Random random;
	private Queue<List<Future<Double>>> futures;
	private Map<Integer, SwarmParticle> population;
	private Map<Integer, SwarmParticle> futuresMap;
	private double meanPopulationFitness;

	private static final double PENALTY = 125.0;
	private static final int NUM_OF_RAMPS = 11;
	private static final int NSEEDS = 3;
	private static final double ALPHA = 0.2;
	private static final int MAX_ITERS = 45;
	private static final double BETA = 2.0945;
	private static final double GAMMA = 2.0945;
	private static final double THETA = 2.0945;
	private static final int POPULATION_SIZE = 10;

	public PSORampMetering() {
		this.futures = new ConcurrentLinkedQueue<List<Future<Double>>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		population = new LinkedHashMap<Integer, SwarmParticle>();
		futuresMap = new HashMap<Integer, SwarmParticle>();
	}

	private class ComputeMeanTTS implements Runnable {
		@Override
		public void run() {
			while (true) {
				try {
					if (futures.isEmpty()) {
						Thread.sleep(1);
						continue;
					}

					List<Future<Double>> futureSeeds = futures.poll();
					SwarmParticle particle = futuresMap.get(futureSeeds.hashCode());
					double meanTTS = 0;

					for (Future<Double> future : futureSeeds)
						meanTTS += future.get();

					meanTTS = meanTTS / NSEEDS;
					for (double qp : particle.getParameters())
						meanTTS += qp * PENALTY;

					particle.setFitness(meanTTS);
					if (particle.getBestFitness() > meanTTS) {
						particle.setBestFitness(meanTTS);
						particle.setBestParameters(particle.getParameters());
					}

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

		PSORampMetering pso = new PSORampMetering();
		pso.executor.submit(pso.new ComputeMeanTTS());
		random = new Random();
		core = SimulatorCore.getInstance(1);

		// Initialize the a population
		for (int id = 0; id < POPULATION_SIZE; id++) {
			Vector queueThreshold = null;
			if (id == -1)
				queueThreshold = DenseVector.constant(NUM_OF_RAMPS, 0.0);
			else {
				queueThreshold = DenseVector.random(NUM_OF_RAMPS, random);
				queueThreshold = queueThreshold.transform(new VectorFunction() {

					@Override
					public double evaluate(int i, double value) {
						return Math.round(value * 0.8 * 100.0) / 100.0;
					}
				});
			}

			SwarmParticle particle = new SwarmParticle(id, Integer.MAX_VALUE, Integer.MAX_VALUE,
					queueThreshold, POPULATION_SIZE, 0.8);
			pso.population.put(id, particle);
		}

		double gBest = Double.MAX_VALUE;
		Vector gBestParameters = null;

		System.out.println("Generation\tbest fitness\tmean fitness");

		int iter = 1;
		do {
			// Analyze the fitness the of the population
			for (SwarmParticle particle : pso.population.values()) {
				Vector queueThreshold = particle.getParameters();

				List<Future<Double>> futureSeeds = new ArrayList<Future<Double>>();
				for (int seed = 0; seed < NSEEDS; seed++) {
					// core.getRandom().setSeed(randomGA.nextLong());
					core.getRandom().setSeed(1);
					CellTransmissionModel ctm = new CellTransmissionModel(core, false, true, false,
							false, 2100);
					int index = 0;
					for (RampMeter meter : ctm.getMeteredRamps().values())
						meter.setQueuePercentage(queueThreshold.get(index++));

					futureSeeds.add(pso.executor.submit(ctm));
				}
				pso.futures.add(futureSeeds);
				pso.futuresMap.put(futureSeeds.hashCode(), particle);

			}

			while (true) {
				if (pso.futuresMap.size() == 0)
					break;
				else
					Thread.sleep(10);
			}

			pso.population = sortByComparator(pso.population);
			SwarmParticle best = pso.population.values().iterator().next();
			if (best.getBestFitness() < gBest) {
				gBest = best.getBestFitness();
				gBestParameters = best.getParameters();
			}

			pso.meanPopulationFitness = 0.0;
			for (SwarmParticle particle : pso.population.values())
				pso.meanPopulationFitness += particle.getFitness();
			pso.meanPopulationFitness /= POPULATION_SIZE;

			if (Double.isNaN(pso.meanPopulationFitness))
				new IllegalStateException("wtf!! mean fitness cannot be a NAN your code sucks");

			System.out.println(iter + "\t" + gBest + "\t" + pso.meanPopulationFitness);

			// Updating particle velocities and positions
			for (SwarmParticle particle : pso.population.values()) {
				particle.setLocalBest(particle.getBestFitness());
				particle.setLocalBestParameters(particle.getParameters());
				for (int neighbourIndex : particle.getNeighboursIndex()) {
					if (pso.population.get(neighbourIndex).getBestFitness() < particle
							.getLocalBest()) {
						particle.setLocalBest(pso.population.get(neighbourIndex).getBestFitness());
						particle.setLocalBestParameters(pso.population.get(neighbourIndex)
								.getBestParameters());
					}
				}
				pso.mutate(particle, gBestParameters);
			}

			iter++;

		} while (iter < MAX_ITERS);

		System.out.println("Finished all iterations..");
		for (SwarmParticle particle : pso.population.values()) {
			for (double qp : particle.getParameters())
				System.out.print(qp + "\t");
			System.out.println("");
		}

		StringBuffer buffer = new StringBuffer("");
		for (double param : gBestParameters)
			buffer.append(param + ", ");

		System.out.println(buffer.toString() + " fitness:" + gBest);
		pso.executor.shutdown();

	}

	/**
	 * Mutate the {@link SwarmParticle}
	 * 
	 * @param particle
	 *            to be mutated
	 */
	private void mutate(SwarmParticle particle, Vector gBestParameters) {
		Vector velocityArr = particle.getVelocity();
		Vector parameters = particle.getParameters();
		final double max = 0.8;
		final double min = 0.0;

		double b = random.nextDouble() * BETA;
		double g = random.nextDouble() * GAMMA;
		double l = random.nextDouble() * THETA;

		Vector v1 = velocityArr.multiply(ALPHA);
		Vector v2 = particle.getBestParameters().subtract(parameters).multiply(b);
		Vector v3 = gBestParameters.subtract(parameters).multiply(g);
		Vector v4 = particle.getLocalBestParameters().subtract(parameters).multiply(l);

		Vector newVelocity = v1.add(v2).add(v3).add(v4);
		parameters = parameters.add(newVelocity);
		particle.setParameters(parameters.transform(new VectorFunction() {
			@Override
			public double evaluate(int i, double value) {
				double temp = value;
				if (temp > max) {
					temp = max;
				}
				if (temp < min) {
					temp = min;
				}
				return Math.round(temp * 100.0) / 100.0;
			}
		}));

		particle.setVelocity(newVelocity);
	}

	private static Map<Integer, SwarmParticle> sortByComparator(
			Map<Integer, SwarmParticle> unsortMap) {

		// Convert Map to List
		List<Map.Entry<Integer, SwarmParticle>> list = new LinkedList<Map.Entry<Integer, SwarmParticle>>(
				unsortMap.entrySet());

		// Sort list with comparator, to compare the Map values
		Collections.sort(list, new Comparator<Map.Entry<Integer, SwarmParticle>>() {
			public int compare(Map.Entry<Integer, SwarmParticle> o1,
					Map.Entry<Integer, SwarmParticle> o2) {
				return (o1.getValue()).compareTo(o2.getValue());
			}
		});

		// Convert sorted map back to a Map
		Map<Integer, SwarmParticle> sortedMap = new LinkedHashMap<Integer, SwarmParticle>();
		for (Iterator<Map.Entry<Integer, SwarmParticle>> it = list.iterator(); it.hasNext();) {
			Map.Entry<Integer, SwarmParticle> entry = it.next();
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}

}
