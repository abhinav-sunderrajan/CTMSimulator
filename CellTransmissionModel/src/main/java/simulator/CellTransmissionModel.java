package simulator;

import java.awt.Color;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import main.SimulatorCore;

import org.apache.commons.lang.ArrayUtils;
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
	private Double noRMDelay;
	private double mainPIEDelay = 0.0;
	private static Map<Integer, String> actionMap;
	private static DeepQLearning qlearning;
	private static boolean training = false;
	private static boolean testing = false;
	private static MultiLayerNetwork nnModel;
	private static int numOfInputs;
	private static double delayScale = 1.0e-4;
	private static boolean printActions;
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);
	private static boolean generateStates;
	private static List<INDArray> states = new ArrayList<>();
	private static SimulatorCore simCore;
	private static final Integer rampControl[] = { 28947, 28594, 29005, 31991 };

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
	 * @param noRMDelay
	 * @param delayScale2
	 */
	public static void setUpTraining(int numberOfInputs, Map<Integer, String> actionMap,
			DeepQLearning deepLearning, double delayScale2) {
		CellTransmissionModel.actionMap = actionMap;
		CellTransmissionModel.qlearning = deepLearning;
		CellTransmissionModel.training = true;
		CellTransmissionModel.testing = false;
		CellTransmissionModel.numOfInputs = numberOfInputs;
		delayScale = delayScale2;
	}

	/**
	 * Evaluate the performance of deep-rl based on the configured neural
	 * network.
	 * 
	 * @param multiLayerNetwork
	 * @param numOfCells
	 * @param actionMap
	 * @param printActions
	 */
	public static void testModel(MultiLayerNetwork multiLayerNetwork, int numOfCells,
			Map<Integer, String> actionMap, boolean printActions) {
		CellTransmissionModel.actionMap = actionMap;
		testing = true;
		training = false;
		nnModel = multiLayerNetwork;
		if (!nnModel.isInitCalled()) {
			nnModel.init();
		}

		CellTransmissionModel.numOfInputs = numOfCells;
		CellTransmissionModel.printActions = printActions;
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
		if (CellTransmissionModel.simCore == null)
			simCore = core;
		this.applyRampMetering = applyMetering;
		this.haveAccident = haveAccident;
		meteredRamps = new LinkedHashMap<Cell, SimpleRampMeter>();
		this.cellNetwork = new CellNetwork(core.getPieChangi().values());
		Cell.setApplyRampMetering(applyMetering);
		Cell.setRamps(cellNetwork.getRamps());
		for (Road ramp : cellNetwork.getRamps()) {
			SimpleRampMeter rampMeter = new SimpleRampMeter(cellNetwork);
			rampMeter.setRamp(ramp);
			if (Arrays.asList(rampControl).contains(ramp.getRoadId()))
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
			// Always start with green lights.
			if (applyRampMetering) {
				if (training) {
					qlearning.setCellNetwork(cellNetwork);
					state = qlearning.getCellState();
					action = actionMap.size() - 1;
				} else {
					state = DeepQLearning.getCellState(cellNetwork, numOfInputs);
					INDArray actions = nnModel.output(state, false);
					action = Nd4j.getExecutioner().execAndReturn(new IAMax(actions))
							.getFinalResult();
				}
				trafficLights = actionMap.get(action);
			}

			double prevDelay = -1.0;

			for (simulationTime = 0; simulationTime <= endTime; simulationTime += SimulationConstants.TIME_STEP) {
				if (applyRampMetering && simulationTime % SimpleRampMeter.PHASE_MIN == 0) {

					if (simulationTime > 0) {
						netDelay += delay;
						if (training) {
							// Get reward for action taken
							if (prevDelay == -1)
								prevDelay = delay;

							boolean isTerminalState = simulationTime == endTime ? true : false;
							double reward = (-delay) * 0.005;

							if (isTerminalState) {
								if (noRMDelay > netDelay) {
									reward = (noRMDelay - netDelay) * 0.25;
								}
							}

							// The next state after updating the neural net.
							state = qlearning.qLearning(state, action, reward, isTerminalState);
							// get the appropriate action for this state
							action = qlearning.getBestAction(state);
						}
						if (testing) {
							// Use the neural network to determine the best
							// possible action.
							state = DeepQLearning.getCellState(cellNetwork, numOfInputs);
							INDArray actions = nnModel.output(state, false);
							if (SimulatorCore.SIMCORE_RANDOM.nextDouble() > 0.01)
								action = Nd4j.getExecutioner().execAndReturn(new IAMax(actions))
										.getFinalResult();
							else
								action = SimulatorCore.SIMCORE_RANDOM.nextInt(actions.length());
						}

						trafficLights = actionMap.get(action);
						if (printActions)
							System.out
									.println(simulationTime + "\t" + delay + "\t" + trafficLights);

						prevDelay = delay;
						delay = 0.0;
					}

				}

				if (generateStates && simulationTime % 12 == 0) {
					states.add(DeepQLearning.getCellState(cellNetwork, numOfInputs));
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
						} else if (applyRampMetering && meteredRamps.containsKey(cell)) {
							SimpleRampMeter sr = meteredRamps.get(cell);
							delay += sr.getDelay();
						} else {
							// Number of vehicles that can exit a cell under
							// free-flow conditions.
							double ff = (cell.getNumOfVehicles() * cell.getFreeFlowSpeed() * SimulationConstants.TIME_STEP)
									/ cell.getLength();
							ff = Math.round(ff);
							if ((ff - cell.getOutflow()) > 0) {
								delay += (ff - cell.getOutflow());
								if (testing
										&& ArrayUtils.contains(SimulatorCore.PIE_MAIN_ROADS, cell
												.getRoad().getRoadId())) {
									mainPIEDelay += delay;
								}

							}
						}

					}
				}

				if (!applyRampMetering && simulationTime % SimpleRampMeter.PHASE_MIN == 0) {
					netDelay += delay;
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

		return netDelay;

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

	/**
	 * @return the states
	 */
	public static List<INDArray> getStates() {
		return states;
	}

	public static void generateStates(boolean b, int numOfCells) {
		generateStates = b;
		numOfInputs = numOfCells;

	}

	/**
	 * @param noRMDelay
	 *            the noRMDelay to set
	 */
	public void setNoRMDelay(Double noRMDelay) {
		this.noRMDelay = noRMDelay;
	}

	/**
	 * @return the mainPIEDelay
	 */
	public double getMainPIEDelay() {
		return mainPIEDelay;
	}

}
