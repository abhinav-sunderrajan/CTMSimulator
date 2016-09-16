package rl;

import java.util.ArrayList;
import java.util.List;

import main.SimulatorCore;

import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
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
	private static final int bufferSize = 500000;
	private static final int batchSize = 32;
	private List<Integer> batchList;
	private int sucessCount = 0;
	private MultiLayerNetwork targetModel;
	private long step = 0;
	private boolean beginTraining;

	public ExperienceReplay(int numOfCells, int numOfActions, double learningRate) {
		super(numOfCells, numOfActions, learningRate);
		replayList = new ArrayList<ReplayTuple>();
		batchList = new ArrayList<Integer>();
		targetModel = model.clone();
		beginTraining = false;
	}

	@Override
	public INDArray qLearning(INDArray prevState, int action, double reward, boolean isTerminalState) {
		INDArray nextState = getCellState();
		ReplayTuple replay = new ReplayTuple(prevState.dup(), action, nextState.dup(), reward,
				isTerminalState);
		if (replayList.size() < bufferSize || !beginTraining) {
			if (isTerminalState && reward > 50.0)
				sucessCount++;
			replayList.add(replay);
		} else {
			if (h < (replayList.size() - 1))
				h += 1;
			else
				h = 0;
			replayList.set(h, replay);
			List<ReplayTuple> miniBatch = getMiniBatch();

			List<INDArray> x = new ArrayList<>();
			List<INDArray> y = new ArrayList<>();
			for (ReplayTuple memory : miniBatch) {
				INDArray oldQVal = targetModel.output(memory.getOldState(), true);
				INDArray nextQVal = targetModel.output(memory.getNextState(), true);
				double maxQ = Nd4j.getExecutioner().execAndReturn(new Max(nextQVal))
						.getFinalResult().doubleValue();

				double update = 0.0;
				if (memory.isTerminalState())
					update = memory.getReward();
				else
					update = memory.getReward() + maxQ * DISCOUNT;
				INDArray nextQval = oldQVal.putScalar(0, memory.getAction(), update);
				x.add(memory.getOldState());
				y.add(nextQval);
			}
			INDArray oldStates = Nd4j.vstack(x);
			INDArray oldQvals = Nd4j.vstack(y);

			DataSet dataSet = new DataSet(oldStates, oldQvals);
			List<DataSet> listDs = dataSet.asList();
			DataSetIterator iterator = new ListDataSetIterator(listDs, batchSize);
			model.fit(iterator);
			step++;
			if (step % 100 == 0) {
				targetModel = model.clone();
			}
		}
		return nextState;
	}

	private List<ReplayTuple> getMiniBatch() {
		List<ReplayTuple> targetList = new ArrayList<>();
		while (targetList.size() < batchSize) {
			int j = SimulatorCore.SIMCORE_RANDOM.nextInt(bufferSize);
			if (!batchList.contains(j)) {
				targetList.add(replayList.get(j));
				batchList.add(j);
			}
		}
		batchList.clear();
		return targetList;
	}

	/**
	 * @return the buffersize
	 */
	public static int getBuffersize() {
		return bufferSize;
	}

	/**
	 * @return the replayList
	 */
	public List<ReplayTuple> getReplayList() {
		return replayList;
	}

	/**
	 * @return the sucessCount
	 */
	public int getSucessCount() {
		return sucessCount;
	}

	/**
	 * @return the target
	 */
	public MultiLayerNetwork getTargetModel() {
		return targetModel;
	}

	/**
	 * @return the beginTraining
	 */
	public boolean isBeginTraining() {
		return beginTraining;
	}

	/**
	 * @param beginTraining
	 *            the beginTraining to set
	 */
	public void setBeginTraining(boolean beginTraining) {
		this.beginTraining = beginTraining;
	}

}
