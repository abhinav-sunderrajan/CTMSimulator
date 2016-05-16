package psomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.la4j.Vector;
import org.la4j.vector.DenseVector;
import org.la4j.vector.functor.VectorFunction;

/**
 * A particle in the swarm for PSO.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class SwarmParticle implements Comparable<SwarmParticle> {

	private double bestFitness;
	private double fitness;
	private Vector parameters;
	private Vector bestParameters;
	private Vector velocity;
	private List<Integer> neighboursIndex;
	private double localBest;
	private Vector localBestParameters;
	private static final int NUM_OF_NEIGHBOURS = 3;
	private static Random random = new Random();
	private int id;

	/**
	 * @param id
	 *            the id of the swarm particle.
	 * @param fitness
	 * @param bestFitNess
	 * @param parameters
	 * @param populationSize
	 * @param paramRange
	 *            Range of the parameters.
	 */
	public SwarmParticle(int id, double fitness, double bestFitNess, Vector parameters,
			int populationSize, final double paramRange) {
		this.bestFitness = bestFitNess;
		this.parameters = parameters;
		this.bestParameters = DenseVector.constant(parameters.length(), 0.0);
		this.localBestParameters = DenseVector.constant(parameters.length(), 0.0);
		this.fitness = fitness;
		this.velocity = DenseVector.random(parameters.length(), random);
		this.velocity = velocity.transform(new VectorFunction() {

			@Override
			public double evaluate(int i, double value) {
				return value * paramRange;
			}
		});
		this.id = id;
		this.neighboursIndex = new ArrayList<Integer>();

		while (true) {
			int neighbour = (int) Math.floor(Math.random() * populationSize);
			if (neighbour == id || neighboursIndex.contains(neighbour))
				continue;
			neighboursIndex.add(neighbour);
			if (neighboursIndex.size() == NUM_OF_NEIGHBOURS)
				break;
		}

		localBest = bestFitNess;

	}

	/**
	 * @return the bestFitness
	 */
	public double getBestFitness() {
		return bestFitness;
	}

	/**
	 * @param bestFitness
	 *            the bestFitness to set
	 */
	public void setBestFitness(double bestFitness) {
		this.bestFitness = bestFitness;
	}

	/**
	 * @return the parameters
	 */
	public Vector getParameters() {
		return parameters;
	}

	/**
	 * @param parameters
	 *            the parameters to set
	 */
	public void setParameters(Vector parameters) {
		this.parameters = parameters;
	}

	@Override
	public String toString() {

		StringBuffer buffer = new StringBuffer("");
		for (double param : parameters)
			buffer.append(param + ", ");
		return buffer.toString() + " fitness:" + fitness;
	}

	@Override
	public int compareTo(SwarmParticle particle) {
		double otherFitness = ((SwarmParticle) particle).getBestFitness();
		// ascending order
		return Double.compare(this.bestFitness, otherFitness);
	}

	/**
	 * @return the velocity
	 */
	public Vector getVelocity() {
		return velocity;
	}

	/**
	 * @param velocity
	 *            the velocity to set
	 */
	public void setVelocity(Vector velocity) {
		this.velocity = velocity;
	}

	/**
	 * @return the fitness
	 */
	public double getFitness() {
		return fitness;
	}

	/**
	 * @param tts
	 *            the fitness to set
	 */
	public void setFitness(Double tts) {
		this.fitness = tts;
	}

	/**
	 * @return the bestParameters
	 */
	public Vector getBestParameters() {
		return bestParameters;
	}

	/**
	 * @param bestParameters
	 *            the bestParameters to set
	 */
	public void setBestParameters(Vector bestParameters) {
		this.bestParameters = bestParameters;
	}

	/**
	 * @return the localBestParameters
	 */
	public Vector getLocalBestParameters() {
		return localBestParameters;
	}

	/**
	 * @param localBestParameters
	 *            the localBestParameters to set
	 */
	public void setLocalBestParameters(Vector localBestParameters) {
		this.localBestParameters = localBestParameters;
	}

	/**
	 * @return the neighboursIndex
	 */
	public List<Integer> getNeighboursIndex() {
		return neighboursIndex;
	}

	/**
	 * @return the localBest
	 */
	public double getLocalBest() {
		return localBest;
	}

	/**
	 * @param localBest
	 *            the localBest to set
	 */
	public void setLocalBest(double localBest) {
		this.localBest = localBest;
	}

	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}

	@Override
	public int hashCode() {
		return this.id;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof SwarmParticle) {
			if (this.id == ((SwarmParticle) o).id)
				return true;
			else
				return false;
		} else {
			return false;
		}
	}
}
