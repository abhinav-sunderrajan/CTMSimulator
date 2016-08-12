package rl;

import java.util.List;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.accum.Max;
import org.nd4j.linalg.factory.Nd4j;

import ctm.CellNetwork;

public class SimpleQLearning extends DeepQLearning {

	public SimpleQLearning(int numOfCells, long seed, int numOfActions, CellNetwork cellNetwork) {
		super(numOfCells, seed, numOfActions, cellNetwork);
	}

	/**
	 * The Q learning algorithm which returns the next state after the neural
	 * net model update.
	 * 
	 * @param prevState
	 *            the previous state.
	 * @param action
	 *            the index of the action taken.
	 * @param reward
	 *            the reward for the action taken
	 * @param isTerminalState
	 *            is the terminal state reached?
	 * 
	 * @return
	 */
	public INDArray qLearning(INDArray prevState, int action, double reward, boolean isTerminalState) {
		INDArray nextState = getCellState();
		List<INDArray> ops = model.feedForward(nextState);
		INDArray actions = ops.get(ops.size() - 1).dup();
		double maxQ = Nd4j.getExecutioner().execAndReturn(new Max(actions)).getFinalResult()
				.doubleValue();

		if (isTerminalState)
			actions.getColumn(action).addi(reward);
		else
			actions.getColumn(action).addi(reward + maxQ * DISCOUNT);

		model.fit(prevState, actions);
		return nextState;
	}

}
