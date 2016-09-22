package rl;

import java.util.List;

import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.accum.Max;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

/**
 * A simple version of deep q learning.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class SimpleQLearning extends DeepQLearning {

	public SimpleQLearning(int numOfCells, int numOfActions, double learningRate,
			double regularization) {
		super(numOfCells, numOfActions, learningRate, regularization);
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
		INDArray actions = model.output(nextState, true);
		double maxQ = Nd4j.getExecutioner().execAndReturn(new Max(actions)).getFinalResult()
				.doubleValue();
		double update = 0.0;
		if (isTerminalState)
			update = reward;
		else
			update = reward + maxQ * DISCOUNT;

		actions = actions.putScalar(0, action, update);

		DataSet dataSet = new DataSet(prevState, actions);
		List<DataSet> listDs = dataSet.asList();
		DataSetIterator iterator = new ListDataSetIterator(listDs, 1);

		model.fit(iterator);
		return nextState;
	}
}
