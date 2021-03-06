package rl;

import main.SimulatorCore;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.indexaccum.IAMax;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction;

import ctm.Cell;
import ctm.CellNetwork;
import ctm.SinkCell;
import ctm.SourceCell;

/**
 * Neural network based reinforcement learning.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public abstract class DeepQLearning {

	protected MultiLayerNetwork model;
	protected CellNetwork cellNetwork;
	protected double epsilon;
	protected int numOfCells;
	protected int numOfActions;
	protected static final double DISCOUNT = 0.99;

	/**
	 * Initialize the Neural Network for deep q-learning.
	 * 
	 * @param numOfCells
	 *            the number of cells in the cell network.
	 * 
	 * @param numOfActions
	 *            the number of possible actions
	 * @param learningRate
	 *            learning rate for the neural network.
	 * 
	 * @param instance
	 *            of random.
	 */
	public DeepQLearning(int numOfCells, int numOfActions, double learningRate,
			double regularization) {
		this.numOfActions = numOfActions;
		this.numOfCells = numOfCells;
		// Create neural network
		MultiLayerConfiguration conf = getNNConfig(learningRate, regularization);
		model = new MultiLayerNetwork(conf);
		model.init();
		// IterationListener it = new HistogramIterationListener(1);
		// model.setListeners(it);
		epsilon = 1.0;
	}

	/**
	 * Get the best action as determined by the neural-net for the provided
	 * state s.
	 * 
	 * @param s
	 *            the current traffic state.
	 * @return
	 */
	public int getBestAction(INDArray s) {
		INDArray actions = model.output(s, true);
		// Decide on the action to take.
		int action = -1;
		if (SimulatorCore.SIMCORE_RANDOM.nextDouble() < epsilon)
			action = SimulatorCore.SIMCORE_RANDOM.nextInt(actions.length());
		else
			action = Nd4j.getExecutioner().execAndReturn(new IAMax(actions)).getFinalResult();
		return action;
	}

	private MultiLayerConfiguration getNNConfig(double learningRate, double regularization) {
		long seed = SimulatorCore.SIMCORE_RANDOM.nextLong();
		MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
				.seed(seed)
				.optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
				.iterations(1)
				.learningRate(learningRate)
				.updater(Updater.RMSPROP)
				.rmsDecay(0.95)
				.regularization(true)
				.l2(regularization)
				.list()
				.layer(0,
						new DenseLayer.Builder().nIn(numOfCells).nOut(204).activation("leakyrelu")
								.weightInit(WeightInit.RELU).build())
				.layer(1,
						new DenseLayer.Builder().nIn(204).nOut(150).activation("leakyrelu")
								.weightInit(WeightInit.RELU).build())
				.layer(2,
						new OutputLayer.Builder(LossFunction.MSE).activation("identity").nIn(150)
								.nOut(numOfActions).build()).pretrain(false).backprop(true).build();
		return conf;
	}

	public abstract INDArray qLearning(INDArray prevState, int action, double reward,
			boolean isTerminalState);

	/**
	 * Get the cell state characterized by the cell density as an ND4j vector.
	 * 
	 * @return the normalized cell state.
	 */
	public INDArray getCellState() {
		int i = 0;
		double cellDensities[] = new double[numOfCells];
		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
				double ratio = cell.getNumOfVehicles() / cell.getnMax();
				ratio = ratio > 1.0 ? 1.0 : ratio;
				cellDensities[i] = ratio;
				i++;
			}
		}
		INDArray state = Nd4j.create(cellDensities, new int[] { 1, numOfCells });
		// Normalize
		// state = Transforms.normalizeZeroMeanAndUnitVariance(state);
		return state;
	}

	public static INDArray getCellState(CellNetwork cellNetwork, int numOfCells) {
		int i = 0;
		double cellDensities[] = new double[numOfCells];
		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
				double ratio = cell.getNumOfVehicles() / cell.getnMax();
				ratio = ratio > 1.0 ? 1.0 : ratio;
				cellDensities[i] = ratio;
				i++;
			}
		}
		INDArray state = Nd4j.create(cellDensities, new int[] { 1, numOfCells });
		return state;
	}

	/**
	 * @return the cellNetwork
	 */
	public CellNetwork getCellNetwork() {
		return cellNetwork;
	}

	/**
	 * @param cellNetwork
	 *            the cellNetwork to set
	 */
	public void setCellNetwork(CellNetwork cellNetwork) {
		this.cellNetwork = cellNetwork;
	}

	/**
	 * @return the model
	 */
	public MultiLayerNetwork getModel() {
		return model;
	}

	/**
	 * @param model
	 *            the model to set
	 */
	public void setModel(MultiLayerNetwork model) {
		this.model = model;
	}

	/**
	 * @return the epsilon
	 */
	public double getEpsilon() {
		return epsilon;
	}

	/**
	 * @param epsilon
	 *            the epsilon to set
	 */
	public void setEpsilon(double epsilon) {
		this.epsilon = epsilon;
	}

}
