package psomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import main.SimulatorCore;

import org.la4j.Vector;
import org.la4j.vector.DenseVector;
import org.la4j.vector.functor.VectorFunction;

import simulator.CellTransmissionModel;
import strategy.RampMeterQueueThreshhold;
import strategy.WarmupCTM;

/**
 * A particle swarm optimization for optimal ramp metering.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class PSORampMetering {

	private static SimulatorCore core;
	private static final int NUM_OF_RAMPS = 11;
	private static final int NSEEDS = 1;
	private static final int MAX_ITERS = 25;
	private static final int POPULATION_SIZE = 15;

	public static void main(String args[]) throws InterruptedException, ExecutionException {

		ParticleSwarmOptimization pso = new ParticleSwarmOptimization(new Resolve() {

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
		});
		core = SimulatorCore.getInstance(1);
		pso.setnSeeds(NSEEDS);

		// Initialize the a population
		for (int id = 0; id < POPULATION_SIZE; id++) {
			Vector queueThreshold = null;
			if (id == -1)
				queueThreshold = DenseVector.constant(NUM_OF_RAMPS, 0.0);
			else {
				queueThreshold = DenseVector.random(NUM_OF_RAMPS, pso.getRandom());
				queueThreshold = queueThreshold.transform(new VectorFunction() {

					@Override
					public double evaluate(int i, double value) {
						return Math.round(value * 0.8 * 100.0) / 100.0;
					}
				});
			}

			SwarmParticle particle = new SwarmParticle(id, Integer.MAX_VALUE, Integer.MAX_VALUE,
					queueThreshold, POPULATION_SIZE, 0.0, 0.8);
			pso.getPopulation().put(id, particle);
		}

		System.out.println("Generation\tbest fitness\tmean fitness");
		long tStart = System.currentTimeMillis();
		Set<String> cellState = WarmupCTM.initializeCellState(core, 3);
		int iter = 1;
		do {
			// Analyze the fitness the of the population
			for (SwarmParticle particle : pso.getPopulation().values()) {
				Vector queueThreshold = particle.getParameters();

				List<Future<Double>> futureSeeds = new ArrayList<Future<Double>>();
				for (int seed = 0; seed < NSEEDS; seed++) {
					SimulatorCore.SIMCORE_RANDOM.setSeed(pso.getRandom().nextLong());
					CellTransmissionModel ctm = new CellTransmissionModel(core, false, true, false,
							1800);
					ctm.intializeTrafficState(cellState);
					int index = 0;
					for (RampMeterQueueThreshhold meter : ctm.getMeteredRamps().values())
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
			pso.updateParticles();

			iter++;

		} while (iter < MAX_ITERS);

		System.out.println("Execution time:" + (System.currentTimeMillis() - tStart));

		System.out.println("\n");
		for (SwarmParticle particle : pso.getPopulation().values()) {
			for (double qp : particle.getParameters())
				System.out.print(qp + "\t");
			System.out.println("");
		}

		Vector bestParam = pso.getgBestParameters();

		System.out.println(bestParam + " fitness:" + pso.getgBest());
		pso.getExecutor().shutdown();

	}

}
