package psomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import main.SimulatorCore;

import org.la4j.Vector;
import org.la4j.vector.DenseVector;
import org.la4j.vector.functor.VectorFunction;

import simulator.CellTransmissionModel;
import strategy.WarmupCTM;
import ctm.Cell;

/**
 * Particle Swarm optimization for variable speed limits.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class PSOVSL {

	private static SimulatorCore core;
	private static final int NSEEDS = 3;
	private static final int MAX_ITERS = 10;
	private static final int POPULATION_SIZE = 10;
	private static final int PIE[] = { 30633, 30634, 30635, 30636, 30637, 30638, 30639, 30640,
			30641, 37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650,
			30651, 30580, 30581 };
	private static Resolve resolve;
	private static final double FREE_FLOW = 80.0;

	public static void main(String args[]) throws InterruptedException, ExecutionException {
		resolve = new Resolve() {

			@Override
			public double evaluate(int i, double value) {
				double temp = value * 18.0 / 5.0;
				if (temp > max * 18.0 / 5.0) {
					temp = max * 18.0 / 5.0;
				}
				if (temp < min * 18.0 / 5.0) {
					temp = min * 18.0 / 5.0;
				}
				double mod = temp % 10;
				temp = mod > 5 ? temp + 10 - mod : temp - mod;
				return temp * 5.0 / 18.0;
			}
		};

		ParticleSwarmOptimization pso = new ParticleSwarmOptimization(resolve);
		core = SimulatorCore.getInstance(1);
		pso.setnSeeds(NSEEDS);

		Random random = pso.getRandom();

		// Initialize the a population
		for (int id = 0; id < POPULATION_SIZE; id++) {
			double[] speedLimit = new double[PIE.length];
			if (id == 0) {
				for (int sl = 0; sl < speedLimit.length; sl++) {
					speedLimit[sl] = FREE_FLOW * 5.0 / 18.0;
				}
			} else {
				int prev = -1;
				for (int sl = 0; sl < speedLimit.length; sl++) {
					Double freeFlowSpeed = FREE_FLOW;
					int max = freeFlowSpeed.intValue() / 10;
					int min = 3;
					int temp = -1;
					if (sl == 0) {
						temp = min + random.nextInt(max - min + 1);
					} else {
						temp = random.nextDouble() < 0.5 ? (prev - 1) : (prev + 1);
						if (temp < min)
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
		Set<String> cellState = WarmupCTM.initializeCellState(core, 5);
		int iter = 1;
		do {
			// Analyze the fitness the of the population
			for (SwarmParticle particle : pso.getPopulation().values()) {
				Vector speedLimits = particle.getParameters();

				List<Future<Double>> futureSeeds = new ArrayList<Future<Double>>();
				for (int seed = 0; seed < NSEEDS; seed++) {
					SimulatorCore.SIMCORE_RANDOM.setSeed(random.nextLong());
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
			pso.updateParticles();
			for (SwarmParticle particle : pso.getPopulation().values()) {
				System.out.println(particle.getParameters());
			}

			iter++;

		} while (iter < MAX_ITERS);

		System.out.println("Execution time:" + (System.currentTimeMillis() - tStart));

		System.out.println("\n");
		for (SwarmParticle particle : pso.getPopulation().values()) {
			for (double speedLimit : particle.getParameters())
				System.out.print(Math.round(speedLimit * 18.0 / 5.0) + "\t");
			System.out.println("");
		}

		Vector bestParam = pso.getgBestParameters();
		bestParam = bestParam.transform(new VectorFunction() {

			@Override
			public double evaluate(int i, double value) {
				return value * 18.0 / 5.0;
			}
		});

		System.out.println(bestParam + " fitness:" + pso.getgBest());
		pso.getExecutor().shutdown();

	}
}
