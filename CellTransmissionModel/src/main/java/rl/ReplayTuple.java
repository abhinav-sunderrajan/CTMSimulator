package rl;

import org.nd4j.linalg.api.ndarray.INDArray;

public class ReplayTuple {
	private INDArray oldState;
	private String trafficLights;
	private INDArray nextState;
	private double reward;

	/**
	 * @param oldState
	 * @param trafficLights
	 * @param nextState
	 * @param reward
	 */
	public ReplayTuple(INDArray oldState, String trafficLights, INDArray nextState, double reward) {
		this.oldState = oldState;
		this.trafficLights = trafficLights;
		this.nextState = nextState;
		this.reward = reward;
	}

	/**
	 * @return the state
	 */
	public INDArray getOldState() {
		return oldState;
	}

	/**
	 * @return the trafficLights
	 */
	public String getTrafficLights() {
		return trafficLights;
	}

	/**
	 * @return the nextState
	 */
	public INDArray getNextState() {
		return nextState;
	}

	/**
	 * @return the reward
	 */
	public double getReward() {
		return reward;
	}

}
