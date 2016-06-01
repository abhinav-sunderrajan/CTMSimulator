package psomain;

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

import org.la4j.Vector;
import org.la4j.vector.functor.VectorFunction;

import utils.ThreadPoolExecutorService;

/**
 * The implementation of particle swarm optimization algorithm.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class ParticleSwarmOptimization {

	private ThreadPoolExecutor executor;
	private Random random;
	private Queue<List<Future<Double>>> futures;
	private Map<Integer, SwarmParticle> population;
	private Map<Integer, SwarmParticle> futuresMap;
	private double meanPopulationFitness;

	private static final double BETA = 2.0945;
	private static final double GAMMA = 2.0945;
	private static final double THETA = 2.0945;
	private static final double ALPHA = 1.0;
	private int nSeeds;
	private Vector gBestParameters;
	private double gBest = Double.MAX_VALUE;
	private Resolve resolve;

	public ParticleSwarmOptimization(Resolve resolve) {
		this.futures = new ConcurrentLinkedQueue<List<Future<Double>>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		population = new LinkedHashMap<Integer, SwarmParticle>();
		futuresMap = new HashMap<Integer, SwarmParticle>();
		random = new Random();
		executor.submit(new ComputeMeanFitness());
		this.nSeeds = 1;
		this.resolve = resolve;

	}

	/**
	 * Compute the mean fitness of each individual in the population.
	 * 
	 * @author abhinav.sunderrajan
	 * 
	 */
	public class ComputeMeanFitness implements Runnable {
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

					meanTTS = meanTTS / nSeeds;
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

	public static Map<Integer, SwarmParticle> sortByComparator(Map<Integer, SwarmParticle> unsortMap) {

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

	/**
	 * Mutate the {@link SwarmParticle}
	 * 
	 * @param particle
	 *            to be mutated
	 */
	private void update(SwarmParticle particle) {
		Vector velocityArr = particle.getVelocity();
		Vector parameters = particle.getParameters();
		resolve.setMax(particle.getParamMax());
		resolve.setMin(particle.getParamMin());

		double b = random.nextDouble() * BETA;
		double g = random.nextDouble() * GAMMA;
		double l = random.nextDouble() * THETA;

		Vector v1 = velocityArr.multiply(ALPHA);
		Vector v2 = particle.getBestParameters().subtract(parameters).multiply(b);
		Vector v3 = gBestParameters.subtract(parameters).multiply(g);
		Vector v4 = particle.getLocalBestParameters().subtract(parameters).multiply(l);

		Vector newVelocity = v1.add(v2).add(v3).add(v4);
		newVelocity = newVelocity.transform(new VectorFunction() {
			@Override
			public double evaluate(int i, double value) {
				if (value < SwarmParticle.getVmin())
					value = SwarmParticle.getVmin();
				if (value > SwarmParticle.getVmax())
					value = SwarmParticle.getVmax();
				return value;
			}
		});
		Vector sigma = newVelocity.transform(new VectorFunction() {

			@Override
			public double evaluate(int i, double value) {
				double prob = 1.0 / (1.0 + Math.exp(-value));
				if (prob < 0.5)
					return -50.0 / 18;
				else
					return 50.0 / 18;
			}
		});
		parameters = parameters.add(sigma);
		particle.setParameters(parameters.transform(resolve));
		particle.setVelocity(newVelocity);
	}

	public void setVectorFunction(Resolve resolve) {
		this.resolve = resolve;
	}

	/**
	 * Compute swarm fitness at the end of iteration.
	 */
	public void computeSwarmFitness() {
		population = sortByComparator(population);
		SwarmParticle best = population.values().iterator().next();
		if (best.getBestFitness() < gBest) {
			gBest = best.getBestFitness();
			gBestParameters = best.getParameters();
		}

		meanPopulationFitness = 0.0;
		for (SwarmParticle particle : population.values())
			meanPopulationFitness += particle.getFitness();
		meanPopulationFitness /= population.size();

		if (Double.isNaN(meanPopulationFitness))
			throw new IllegalStateException("Mean fitness of population is wrongly computed");

	}

	/**
	 * Update particles at the end of the iteration.
	 */
	public void updateParticles() {
		for (SwarmParticle particle : population.values()) {
			particle.setLocalBest(particle.getBestFitness());
			particle.setLocalBestParameters(particle.getParameters());

			for (int neighbourIndex : particle.getNeighboursIndex()) {
				if (population.get(neighbourIndex).getBestFitness() < particle.getLocalBest()) {
					particle.setLocalBest(population.get(neighbourIndex).getBestFitness());
					particle.setLocalBestParameters(population.get(neighbourIndex)
							.getBestParameters());
				}
			}
			update(particle);
			particle.updateNeighbours();
		}
	}

	/**
	 * @return the executor
	 */
	public ThreadPoolExecutor getExecutor() {
		return executor;
	}

	/**
	 * @return the random
	 */
	public Random getRandom() {
		return random;
	}

	/**
	 * @return the population
	 */
	public Map<Integer, SwarmParticle> getPopulation() {
		return population;
	}

	/**
	 * @return the meanPopulationFitness
	 */
	public double getMeanPopulationFitness() {
		return meanPopulationFitness;
	}

	/**
	 * @return the futures
	 */
	public Queue<List<Future<Double>>> getFutures() {
		return futures;
	}

	/**
	 * @return the futuresMap
	 */
	public Map<Integer, SwarmParticle> getFuturesMap() {
		return futuresMap;
	}

	/**
	 * @return the nSeeds
	 */
	public int getnSeeds() {
		return nSeeds;
	}

	/**
	 * @param nSeeds
	 *            this parameters sets the number of iterations to evaluate the
	 *            fitness of a solution to compute the mean. This parameter is
	 *            set to 1 by default and is used when the fitness function is
	 *            stochastic.
	 */
	public void setnSeeds(int nSeeds) {
		this.nSeeds = nSeeds;
	}

	/**
	 * @return the fitness of the best solution.
	 */
	public double getgBest() {
		return gBest;
	}

	/**
	 * @return the best solution ever found until now.
	 */
	public Vector getgBestParameters() {
		return gBestParameters;
	}

}
