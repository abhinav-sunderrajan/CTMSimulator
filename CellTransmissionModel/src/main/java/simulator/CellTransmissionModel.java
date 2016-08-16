package simulator;

import java.awt.Color;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import main.SimulatorCore;

import org.apache.log4j.Logger;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.indexaccum.IAMax;
import org.nd4j.linalg.factory.Nd4j;

import rl.DeepQLearning;
import rnwmodel.Road;
import strategy.SimpleRampMeter;
import viz.CTMSimViewer;
import ctm.Cell;
import ctm.CellNetwork;
import ctm.MergingCell;
import ctm.OrdinaryCell;
import ctm.SinkCell;
import ctm.SourceCell;

/**
 * Initialize and advance a PIE scale macroscopic Cell transmission based
 * traffic simulation.
 * 
 * @see <a href="http://en.wikipedia.org/wiki/Cell_Transmission_Model">
 * 
 * @author abhinav
 * 
 * 
 */

public class CellTransmissionModel implements Callable<Double> {

	private long simulationTime;
	private long endTime;
	private CTMSimViewer viewer;
	private Map<Cell, Color> cellColorMap;
	private boolean haveVisualization;
	private boolean haveAccident;
	private CellNetwork cellNetwork;
	private Map<Cell, SimpleRampMeter> meteredRamps;
	private boolean applyRampMetering;
	private double netDelay = 0.0;
	private static Map<Integer, String> actionMap;
	private static DeepQLearning qlearning;
	private static boolean training = false;
	private static boolean testing = false;
	private static MultiLayerNetwork nnModel;
	private static int numberOfinputs;
	private static final boolean PRINT_FINAL_STATE = false;
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);

	// private static final String SIMULATION_OP_PATH =
	// "C:/Users/abhinav.sunderrajan/Desktop/MapMatch/MapMatchingStats/ctmop.txt";

	/**
	 * Initialize cell state.
	 * 
	 * @param cellState
	 */
	public void intializeTrafficState(Set<String> cellState) {
		for (String state : cellState) {
			String split[] = state.split(":");
			Cell cell = cellNetwork.getCellMap().get(split[0]);
			cell.setMeanSpeed(Double.parseDouble(split[1]));
			cell.setNumberOfvehicles(Double.parseDouble(split[2]));
			cell.setInitilalized(true);
		}
	}

	/**
	 * Sets up the deep reinforcement learning algorithm.
	 * 
	 * @param numberOfInputs
	 *            the number of cells in the cell network.
	 * @param actionMap
	 *            the action map in terms of red green light combination for all
	 *            controllable ramps.
	 * @param deepLearning
	 *            the deep RL algorithm employed.
	 */
	public static void setUpTraining(int numberOfInputs, Map<Integer, String> actionMap,
			DeepQLearning deepLearning) {
		CellTransmissionModel.actionMap = actionMap;
		CellTransmissionModel.qlearning = deepLearning;
		CellTransmissionModel.training = true;
		CellTransmissionModel.testing = false;
		CellTransmissionModel.numberOfinputs = numberOfInputs;
		CellTransmissionModel.nnModel = qlearning.getModel();
	}

	/**
	 * Evaluate the performance of deep-rl based on the configured neural
	 * network.
	 * 
	 * @param multiLayerNetwork
	 * 
	 */
	public static void testModel(MultiLayerNetwork multiLayerNetwork) {
		CellTransmissionModel.testing = true;
		CellTransmissionModel.training = false;
		CellTransmissionModel.nnModel = multiLayerNetwork;
	}

	/**
	 * 
	 * @param core2
	 *            collection of roads to be simulated
	 * @param haveAccident
	 *            simulate an accident?
	 * @param applyMetering
	 *            apply ramp metering?
	 * @param haveViz
	 *            enable visualization?
	 * @param simTime
	 *            time over which to simulate.
	 */
	public CellTransmissionModel(SimulatorCore core, boolean haveAccident, boolean applyMetering,
			boolean haveViz, long simTime) {
		this.applyRampMetering = applyMetering;
		this.haveAccident = haveAccident;
		meteredRamps = new LinkedHashMap<Cell, SimpleRampMeter>();
		this.cellNetwork = new CellNetwork(core.getPieChangi().values());
		Cell.setApplyRampMetering(applyMetering);
		Cell.setRamps(cellNetwork.getRamps());
		for (Road ramp : cellNetwork.getRamps()) {
			SimpleRampMeter rampMeter = new SimpleRampMeter(cellNetwork);
			rampMeter.setRamp(ramp);
			meteredRamps.put(rampMeter.getMeterCell(), rampMeter);
		}

		cellColorMap = new ConcurrentHashMap<Cell, Color>();
		this.haveVisualization = haveViz;
		SimulatorCore.df.setRoundingMode(RoundingMode.CEILING);

		if (haveVisualization) {
			viewer = CTMSimViewer.getCTMViewerInstance("CTM Model", core.getRoadNetwork(),
					cellColorMap, core.getDbConnectionProperties());

			for (Cell cell : cellNetwork.getCellMap().values()) {
				if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
					Color color = null;
					if (cell instanceof OrdinaryCell) {
						// Gray for ordinary cell.
						color = new Color(137, 112, 122, 225);
					} else if (cell instanceof MergingCell) {
						// Green for merging cell.
						color = new Color(51, 255, 153, 225);
					} else {
						// Blue for diverging cell.
						color = new Color(51, 51, 255, 225);
					}
					cellColorMap.put(cell, color);
				}
			}

		}

		endTime = simTime;

	}

	/**
	 * @return the cellNetwork
	 */
	public CellNetwork getCellNetwork() {
		return cellNetwork;
	}

	@Override
	public Double call() throws IOException {
		try {
			Cell accidentCell = cellNetwork.getCellMap().get(SimulationConstants.ACCIDENT_CELL);
			int blockedLanes = 1;
			String trafficLights = null;
			double delay = 0.0;
			INDArray state = null;
			int action = -1;
			if (applyRampMetering) {
				qlearning.setCellNetwork(cellNetwork);
				state = qlearning.getCellState();
				List<INDArray> ops = nnModel.feedForward(state);
				INDArray actions = ops.get(ops.size() - 1);
				action = Nd4j.getExecutioner().execAndReturn(new IAMax(actions)).getFinalResult();
				trafficLights = actionMap.get(action);
			}

			double prevDelay = 1800.0;
			double reward = 0.0;

			for (simulationTime = 0; simulationTime <= endTime; simulationTime += SimulationConstants.TIME_STEP) {
				if (applyRampMetering && simulationTime % SimpleRampMeter.PHASE_MIN == 0) {

					if (simulationTime > 0) {

						if (training) {
							// Get reward for action taken
							reward = (prevDelay - delay) * 0.005;
							boolean isTerminalState = simulationTime == endTime ? true : false;
							// The next state after updating the neural net.
							state = qlearning.qLearning(state, action, reward, isTerminalState);
							// get the appropriate action for this state
							action = qlearning.getBestAction(state);
						}
						if (testing) {
							// Use the neural network to determine the best
							// possible action.
							state = qlearning.getCellState();
							List<INDArray> ops = nnModel.feedForward(state);
							INDArray actions = ops.get(ops.size() - 1);
							action = Nd4j.getExecutioner().execAndReturn(new IAMax(actions))
									.getFinalResult();
						}

						trafficLights = actionMap.get(action);
						if (testing)
							System.out
									.println(simulationTime + "\t" + delay + "\t" + trafficLights);
						netDelay += delay;
						prevDelay = delay;
						delay = 0.0;
					}

				}

				if (haveAccident) {
					if (simulationTime == 0) {
						createAccident(accidentCell, blockedLanes);
					}
					if (simulationTime == 1100) {
						recoverAccident(accidentCell, blockedLanes);
					}
				}

				// Update the sending potential of cells
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell)) {
						cell.determineSendingPotential();
						cell.determineReceivePotential();
					}
				}

				// Update out flow of cells.
				int light = 0;
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (applyRampMetering) {
						if (meteredRamps.containsKey(cell)) {
							SimpleRampMeter sr = meteredRamps.get(cell);
							boolean allow = trafficLights.charAt(light) == 'G' ? true : false;
							sr.setAllow(allow);
							sr.controlAction(simulationTime);
							light++;
						} else {
							cell.updateOutFlow();
						}
					} else {
						cell.updateOutFlow();
					}

				}

				// Compute delay
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SinkCell)) {

						if (cell instanceof SourceCell) {
							delay += ((SourceCell) cell).getSourceDelay();
						} else {
							// Number of vehicles that can exit a cell under
							// free-flow conditions.
							double ff = (cell.getNumOfVehicles() * cell.getFreeFlowSpeed() * SimulationConstants.TIME_STEP)
									/ cell.getLength();
							if ((ff - cell.getOutflow()) > 0.8) {
								delay += (ff - cell.getOutflow());
							}
						}

					}
				}

				if (!applyRampMetering && simulationTime % SimpleRampMeter.PHASE_MIN == 0) {
					netDelay += delay;
					// System.out.println(simulationTime + "\t" + delay);
					delay = 0.0;
				}

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateNumberOfVehiclesInCell();

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateAnticipatedDensity();

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
						cell.updateMeanSpeed();
					}

				}

				if (haveVisualization) {
					for (Cell cell : cellNetwork.getCellMap().values()) {
						if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
							Color c = Color.getHSBColor(
									(float) (cell.getMeanSpeed() * 0.4f / cell.getFreeFlowSpeed()),
									0.9f, 0.9f);
							cellColorMap.put(cell, c);
						}
					}

					viewer.getMapFrame().repaint();
					Thread.sleep(50);
				}

			}

			if (haveVisualization)
				viewer.getMapFrame().dispose();

		} catch (InterruptedException e) {
			LOGGER.error("Error waiting  for simulation time to advance.", e);
		}

		if (PRINT_FINAL_STATE) {
			/*
			 * bw.flush(); bw.close(); System.out.println("Printed file to " +
			 * SIMULATION_OP_PATH);
			 */
			return netDelay;
		} else {
			return netDelay;
		}

	}

	/**
	 * Returns a random real number uniformly in [a, b).
	 * 
	 * @param a
	 *            the left end-point
	 * @param b
	 *            the right end-point
	 * @return a random real number uniformly in [a, b)
	 * @throws IllegalArgumentException
	 *             unless <tt>a < b</tt>
	 */
	public double uniform(double a, double b, Random rand) {
		if (!(a < b))
			throw new IllegalArgumentException("Invalid range");
		return a + rand.nextDouble() * (b - a);
	}

	/**
	 * @return the meteredRamps
	 */
	public Map<Cell, SimpleRampMeter> getMeteredRamps() {
		return meteredRamps;
	}

	/**
	 * @return the qlearning
	 */
	public static DeepQLearning getQlearning() {
		return qlearning;
	}

	/**
	 * @param meteredRamps
	 *            the meteredRamps to set
	 */
	public void setMeteredRamps(Map<Cell, SimpleRampMeter> meteredRamps) {
		this.meteredRamps = meteredRamps;
	}

	private void createAccident(Cell accidentCell, int blockLanes) {
		int laneCount = accidentCell.getNumOfLanes();
		accidentCell.setNumOfLanes(laneCount - blockLanes);
		accidentCell.updateAnticipatedDensity();
		accidentCell.updateMeanSpeed();
		accidentCell.determineSendingPotential();
		accidentCell.determineReceivePotential();

	}

	private void recoverAccident(Cell accidentCell, int blockLanes) {
		int laneCount = accidentCell.getNumOfLanes();
		accidentCell.setNumOfLanes(laneCount + blockLanes);
		accidentCell.updateAnticipatedDensity();
		accidentCell.updateMeanSpeed();
		accidentCell.determineSendingPotential();
		accidentCell.determineReceivePotential();
	}

}
