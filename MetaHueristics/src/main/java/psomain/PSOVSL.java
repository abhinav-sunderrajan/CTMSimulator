package psomain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;

import main.SimulatorCore;

import org.la4j.Vector;
import org.la4j.vector.DenseVector;

import simulator.CellTransmissionModel;
import strategy.RampMeter;

/**
 * Particle Swarm optimization for variable speed limits.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class PSOVSL {

	private static SimulatorCore core;
	private static final int NSEEDS = 3;
	private static final int MAX_ITERS = 25;
	private static final int POPULATION_SIZE = 15;
	private static final int PIE[] = { 30632, 30633, 30634, 30635, 30636, 30637, 30638, 30639,
			30640, 30641, 37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649,
			30650, 30651, 30580, 30581 };
	private static Resolve resolve;
	private static Map<Integer, Double> speedLimitMap;

	public static void main(String args[]) throws InterruptedException {
		ParticleSwarmOptimization pso = new ParticleSwarmOptimization(resolve);
		core = SimulatorCore.getInstance(1);
		pso.setnSeeds(NSEEDS);
		speedLimitMap = new HashMap<Integer, Double>();

		Random random = pso.getRandom();

		// Initialize the a population
		for (int id = 0; id < POPULATION_SIZE; id++) {
			double[] speedLimit = new double[PIE.length];
			if (id == 0) {
				for (int sl = 0; sl < speedLimit.length; sl++) {
					double freeFlowSpeed = core.getRoadNetwork().getAllRoadsMap().get(PIE[sl])
							.getSpeedLimit()[1];
					speedLimit[sl] = freeFlowSpeed * 5.0 / 18.0;
				}
			} else {
				int prev = -1;
				for (int sl = 0; sl < speedLimit.length; sl++) {
					Double freeFlowSpeed = core.getRoadNetwork().getAllRoadsMap().get(PIE[sl])
							.getSpeedLimit()[1];
					int max = freeFlowSpeed.intValue() / 10;
					int temp = -1;
					if (sl == 0) {
						temp = 3 + random.nextInt(max - 2);
					} else {
						temp = random.nextDouble() < 0.5 ? (prev - 1) : (prev + 1);
						if (temp < 3)
							temp = temp + 2;
						if (temp > max)
							temp = temp - 2;
					}
					prev = temp;
					speedLimit[sl] = temp * 10 * 5.0 / 18.0;
				}

			}

			SwarmParticle particle = new SwarmParticle(id, Integer.MAX_VALUE, Integer.MAX_VALUE,
					DenseVector.fromArray(speedLimit), POPULATION_SIZE, (30.0 * 5.0 / 18.0),
					(80 * 5.0 / 18.0));
			pso.getPopulation().put(id, particle);
		}

		System.out.println("Generation\tbest fitness\tmean fitness");
		long tStart = System.currentTimeMillis();
		int iter = 1;
		do {
			// Analyze the fitness the of the population
			for (SwarmParticle particle : pso.getPopulation().values()) {
				Vector queueThreshold = particle.getParameters();

				List<Future<Double>> futureSeeds = new ArrayList<Future<Double>>();
				for (int seed = 0; seed < NSEEDS; seed++) {
					// core.getRandom().setSeed(randomGA.nextLong());
					core.getRandom().setSeed(1);
					CellTransmissionModel ctm = new CellTransmissionModel(core, false, false,
							false, 2100);
					int index = 0;
					for (RampMeter meter : ctm.getMeteredRamps().values())
						meter.setQueuePercentage(queueThreshold.get(index++));

					futureSeeds.add(pso.getExecutor().submit(ctm));
				}
				pso.getFutures().add(futureSeeds);
				pso.getFuturesMap().put(futureSeeds.hashCode(), particle);

			}

			while (true) {
				if (pso.getFuturesMap().size() == 0)
					break;
				else
					Thread.sleep(10);
			}

			pso.computeSwarmFitness();

			System.out
					.println(iter + "\t" + pso.getgBest() + "\t" + pso.getMeanPopulationFitness());

			// Updating particle velocities and positions and neighbors
			pso.tweakParticles();

			iter++;

		} while (iter < MAX_ITERS);

		System.out.println("Execution time:" + (System.currentTimeMillis() - tStart));

		System.out.println("\n");
		for (SwarmParticle particle : pso.getPopulation().values()) {
			for (double qp : particle.getParameters())
				System.out.print(qp + "\t");
			System.out.println("");
		}

		StringBuffer buffer = new StringBuffer("");
		for (double param : pso.getgBestParameters())
			buffer.append(param + ", ");

		System.out.println(buffer.toString() + " fitness:" + pso.getgBest());
		pso.getExecutor().shutdown();

	}
}
