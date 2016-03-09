package psomain;

/**
 * A particle in the swarm for PSO.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class SwarmParticle implements Comparable<SwarmParticle> {

	private double bestFitness;
	private double fitness;
	private Double[] parameters;
	private Double[] bestParameters;
	private double velocity[];
	private int neighboursIndex[];
	private double localBest;
	private static final int NUM_OF_NEIGHBOURS = 3;

	/**
	 * 
	 * @param fitness
	 *            fitness of this generation.
	 * @param bestFitNess
	 *            the best fitness over generations.
	 * @param parameters
	 *            the parameters belonging to this generation.
	 */
	public SwarmParticle(double fitness, double bestFitNess, Double[] parameters) {
		this.bestFitness = bestFitNess;
		this.parameters = parameters;
		this.fitness = fitness;
		this.velocity = new double[parameters.length];
		this.neighboursIndex = new int[NUM_OF_NEIGHBOURS];
		int index = 0;
		while (true) {
			int neighbour = OptimizeSimulationParametersPSO.random
					.nextInt(OptimizeSimulationParametersPSO.POPULATION_SIZE);

			if (index > 0 && neighboursIndex[index - 1] == neighbour)
				continue;
			neighboursIndex[index] = neighbour;
			index++;
			if (index == NUM_OF_NEIGHBOURS)
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
	public Double[] getParameters() {
		return parameters;
	}

	/**
	 * @param parameters
	 *            the parameters to set
	 */
	public void setParameters(Double[] parameters) {
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
	public double[] getVelocity() {
		return velocity;
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
	public Double[] getBestParameters() {
		return bestParameters;
	}

	/**
	 * @param bestParameters
	 *            the bestParameters to set
	 */
	public void setBestParameters(Double[] bestParameters) {
		this.bestParameters = bestParameters;
	}

	/**
	 * @return the neighboursIndex
	 */
	public int[] getNeighboursIndex() {
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

}
