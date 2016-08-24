package rl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.accum.Max;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Implements experience replay.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class ExperienceReplay extends DeepQLearning {

	private List<ReplayTuple> replayList;
	private static int h = 0;
	private static final int bufferSize = 80;
	private static final int batchSize = 40;

	public ExperienceReplay(int numOfCells, long seed, int numOfActions) {
		super(numOfCells, seed, numOfActions);
		replayList = new ArrayList<ReplayTuple>();
	}

	@Override
	public INDArray qLearning(INDArray prevState, int action, double reward, boolean isTerminalState) {
		INDArray nextState = getCellState();
		ReplayTuple replay = new ReplayTuple(prevState.dup(), action, nextState.dup(), reward);
		if (replayList.size() < bufferSize)
			replayList.add(replay);
		else {
			h = h % bufferSize;
			replayList.set(h++, replay);
			List<ReplayTuple> miniBatch = getMiniBatch(batchSize, replayList);

			List<INDArray> oldstatesList = new ArrayList<>();
			List<INDArray> oldQValsList = new ArrayList<>();

			for (ReplayTuple memory : miniBatch) {
				INDArray oldQVal = model.output(memory.getOldState(), true);
				INDArray newQVal = model.output(nextState, true);
				double maxQ = Nd4j.getExecutioner().execAndReturn(new Max(newQVal))
						.getFinalResult().doubleValue();

				double update = 0.0;
				if (isTerminalState)
					update = reward;
				else
					update = reward + maxQ * DISCOUNT;

				oldQVal = oldQVal.putScalar(0, action, update);

				oldstatesList.add(memory.getOldState());
				oldQValsList.add(oldQVal);
				// data.add(new DataSet(memory.getOldState(), oldQVal));
			}
			INDArray oldStates = Nd4j.vstack(oldstatesList);
			INDArray oldQvals = Nd4j.vstack(oldQValsList);

			DataSet dataSet = new DataSet(oldStates, oldQvals);
			List<DataSet> listDs = dataSet.asList();
			DataSetIterator iterator = new ListDataSetIterator(listDs, batchSize);

			model.fit(iterator);

		}
		return nextState;
	}

	private static <T> List<T> getMiniBatch(int size, List<T> all) {
		Collections.shuffle(all);
		List<T> targetList = new ArrayList<>();
		for (int j = 0; j < size; j++) {
			targetList.add(all.get(j));
		}
		return targetList;
	}

}
