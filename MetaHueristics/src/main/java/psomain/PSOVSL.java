package psomain;

import java.util.Random;

import main.SimulatorCore;

import org.la4j.vector.DenseVector;

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

	public static void main(String args[]) {
		ParticleSwarmOptimization pso = new ParticleSwarmOptimization(resolve);
		core = SimulatorCore.getInstance(1);
		pso.setnSeeds(NSEEDS);

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
					DenseVector.fromArray(speedLimit), POPULATION_SIZE, 30.0, 80.0);
			pso.getPopulation().put(id, particle);
		}

	}
}
