package psomain;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import main.SimulatorCore;
import simulator.CellTransmissionModel;
import simulator.SimulationConstants;
import utils.ThreadPoolExecutorService;

/**
 * Optimization of Simulation parameters using PSO.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class OptimizeSimulationParametersPSO {

	static Random random;
	private Queue<Future<Double>> futures;
	private ThreadPoolExecutor executor;
	private SwarmParticle[] population;
	private Map<Integer, SwarmParticle> futuresMap;

	private static final double ramp_min = 0.1;
	private static final double ramp_max = 0.35;
	// private static final double pie_min = 0.85;
	// private static final double pie_max = 1.0;
	// private static final double phi_min = 1.8;
	// private static final double phi_max = 3.6;
	// private static final double delta_min = 0.02;
	// private static final double delta_max = 0.6;
	// private static final double v_out_min = 3.0;
	// private static final double v_out_max = 6.0;
	private static final double timeGap_min = 1.2;
	private static final double timeGap_max = 1.5;

	private static final int NUM_OF_PARAM = 2;

	// Particle Swarm optimization parameters
	private static final double ALPHA = 0.8;
	private static final int MAX_ITERS = 75;
	static final int POPULATION_SIZE = 18;

	private static final double BETA = 1.4945;
	private static final double GAMMA = 0.2;
	private static final double THETA = 1.4945;
	private static final double CONSTRICTION = 0.8;

	private static final int[] roadArr = { 30634, 30635, 30636, 30637, 30638, 30639, 30640, 30641,
			37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650, 30651,
			30580, 30581 };
	private static final DecimalFormat df = new DecimalFormat("#.###");

	public OptimizeSimulationParametersPSO() {
		this.futures = new ConcurrentLinkedQueue<Future<Double>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		population = new SwarmParticle[POPULATION_SIZE];
		futuresMap = new HashMap<Integer, SwarmParticle>();
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
					Double tts = future.get();
					SwarmParticle particle = futuresMap.get(future.hashCode());
					particle.setFitness(Double.parseDouble(df.format(tts)));
					if (particle.getBestFitness() > tts) {
						particle.setBestFitness(tts);
						particle.setBestParameters(particle.getParameters());
					}

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
		SimulatorCore core = SimulatorCore.getInstance(1);
		random = new Random();
		df.setRoundingMode(RoundingMode.DOWN);

		OptimizeSimulationParametersPSO pso = new OptimizeSimulationParametersPSO();
		List<Integer> pieList = new ArrayList<>();
		for (Integer roadId : roadArr)
			pieList.add(roadId);

		pso.executor.submit(pso.new CTMSEMSimSimilarity());

		// Initialize the a population
		for (int i = 0; i < POPULATION_SIZE; i++) {
			Double[] parameters = new Double[NUM_OF_PARAM];
			for (int j = 0; j < parameters.length; j++) {
				double val = -1;
				double min = -1;
				double max = -1;

				if (j == 0) {
					min = ramp_min;
					max = ramp_max;
				} else {
					min = timeGap_min;
					max = timeGap_max;
				}
				val = min + random.nextDouble() * (max - min);
				parameters[j] = Double.parseDouble(df.format(val));
				if (val > max || val < min)
					throw new IllegalStateException("wrong not in range. Min:" + min + " max:"
							+ max + " param:" + parameters[j]);

			}
			pso.population[i] = new SwarmParticle(Integer.MAX_VALUE, Integer.MAX_VALUE, parameters);

		}

		double gBest = Integer.MAX_VALUE;
		Double[] gBestParameters = null;

		int iter = 1;
		do {
			// Analyze the fitness the of the population
			for (SwarmParticle particle : pso.population) {
				Double[] simParams = particle.getParameters();
				for (Integer roadId : core.getMergePriorities().keySet()) {
					if (!pieList.contains(roadId))
						core.getMergePriorities().put(roadId, simParams[0]);
					// else
					// SimulatorCore.mergePriorities.put(roadId, simParams[0]);

				}
				// SimulationConstants.PHI = simParams[2];
				// SimulationConstants.RAMP_DELTA = simParams[3];
				// SimulationConstants.V_OUT_MIN = simParams[4];
				SimulationConstants.TIME_GAP = simParams[1];

				CellTransmissionModel ctm = new CellTransmissionModel(core, false, false, false,
						false, 2100);
				Future<Double> future = pso.executor.submit(ctm);
				pso.futures.add(future);
				pso.futuresMap.put(future.hashCode(), particle);

			}

			while (true) {
				if (pso.futuresMap.size() == 0)
					break;
				else
					Thread.sleep(10);
			}

			Arrays.sort(pso.population);

			// Tie breaking
			int tieBreak = 1;
			while (tieBreak < pso.population.length) {
				if (pso.population[tieBreak].getFitness() != pso.population[0].getFitness())
					break;
				tieBreak++;
			}

			if (tieBreak > 1) {
				System.out.println("Broke a tie break");
				tieBreak = random.nextInt(tieBreak);
			}

			if (pso.population[tieBreak].getBestFitness() < gBest) {
				gBest = pso.population[tieBreak].getBestFitness();
				gBestParameters = pso.population[tieBreak].getParameters();
			}

			System.out.println("Evaluated generation " + iter + ", best fitness:" + gBest);

			// Updating particle velocities and positions

			for (SwarmParticle particle : pso.population) {
				particle.setLocalBest(particle.getBestFitness());
				for (int neighbourIndex : particle.getNeighboursIndex()) {
					if (pso.population[neighbourIndex].getBestFitness() < particle.getLocalBest())
						particle.setLocalBest(pso.population[neighbourIndex].getBestFitness());
				}
				pso.mutate(particle, gBest);
			}

			iter++;

		} while (iter < MAX_ITERS);

		System.out.println("Finished all iterations..");

		StringBuffer buffer = new StringBuffer("");
		for (double param : gBestParameters)
			buffer.append(param + ", ");

		System.out.println(buffer.toString() + " fitness:" + gBest);

	}

	/**
	 * Mutate the {@link SwarmParticle}
	 * 
	 * @param particle
	 *            to be mutated
	 */
	private void mutate(SwarmParticle particle, double gBest) {
		double velocityArr[] = particle.getVelocity();
		Double[] parameters = particle.getParameters();
		int index = 0;
		for (double velocity : velocityArr) {
			double max = 0.0;
			double min = 0.0;
			if (index == 0) {
				min = ramp_min;
				max = ramp_max;
			} else {
				min = timeGap_min;
				max = timeGap_max;
			}

			double b = random.nextDouble() * BETA;
			double g = random.nextDouble() * GAMMA;
			double l = random.nextDouble() * THETA;
			double newVelocity = ALPHA * velocity + b
					* (particle.getBestFitness() - particle.getFitness()) + g
					* (gBest - particle.getFitness()) + l
					* ((particle.getLocalBest() - particle.getFitness()));

			double vMax = (max - min) * CONSTRICTION / 2.0;
			if (newVelocity < -vMax)
				newVelocity = -vMax;

			if (newVelocity > vMax)
				newVelocity = vMax;

			velocityArr[index] = newVelocity;
			double temp = parameters[index] + newVelocity;

			if (temp > max) {
				temp = min + ((temp - max) % (max - min));
			}
			if (temp < min) {
				temp = max - ((min - temp) % (max - min));
			}
			parameters[index] = Double.parseDouble(df.format(temp));

			index++;

		}
	}
}
