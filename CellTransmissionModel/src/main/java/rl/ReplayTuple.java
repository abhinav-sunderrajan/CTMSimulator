package rl;

import org.nd4j.linalg.api.ndarray.INDArray;

public class ReplayTuple {
	private INDArray oldState;
	private int action;
	private INDArray nextState;
	private double reward;

	/**
	 * @param oldState
	 * @param action
	 * @param nextState
	 * @param reward
	 */
	public ReplayTuple(INDArray oldState, int action, INDArray nextState, double reward) {
		this.oldState = oldState;
		this.action = action;
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
	public int getTrafficLights() {
		return action;
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
