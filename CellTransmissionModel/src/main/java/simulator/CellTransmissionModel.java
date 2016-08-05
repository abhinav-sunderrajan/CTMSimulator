package simulator;

import java.awt.Color;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import main.SimulatorCore;
import network.Layer;
import network.NeuralNetwork;

import org.apache.log4j.Logger;
import org.la4j.Matrix;
import org.la4j.Vector;
import org.la4j.vector.DenseVector;

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
	private SimulatorCore core;
	private static final boolean PRINT_FINAL_STATE = false;
	private static final String SIMULATION_OP_PATH = "C:/Users/abhinav.sunderrajan/Desktop/MapMatch/MapMatchingStats/ctmop.txt";
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);
	private static NeuralNetwork neuralNet;
	private static final double ETA = 2.0;
	private static final double DISCOUNT = 0.9;

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
	 * @return the neuralNet
	 */
	public static NeuralNetwork getNeuralNet() {
		return neuralNet;
	}

	/**
	 * @param neuralNet
	 *            the neuralNet to set
	 */
	public static void setNeuralNet(NeuralNetwork neuralNet) {
		CellTransmissionModel.neuralNet = neuralNet;
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
		this.core = core;
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
			Vector trafficLights = null;
			Vector cellStateVector = null;
			double delay = 0.0;
			double prev = 700.0;

			for (simulationTime = 0; simulationTime <= endTime; simulationTime += SimulationConstants.TIME_STEP) {

				if (applyRampMetering && simulationTime % SimpleRampMeter.PHASE_MIN == 0) {
					cellStateVector = DenseVector.zero(neuralNet.getNnLayers()[0].getBias()
							.length());
					int i = 0;
					for (Cell cell : cellNetwork.getCellMap().values()) {
						if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
							double density = cell.getNumOfVehicles()
									/ (cell.getLength() * cell.getNumOfLanes());
							cellStateVector.set(i, density);
							i++;
						}
					}

					trafficLights = neuralNet.feedForward(cellStateVector);
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
							boolean allow = trafficLights.get(light) >= 0.5 ? true : false;
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

				// Compute delay
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell)) {
						// Number of vehicles that can exit a cell under
						// free-flow conditions.
						double ff = (cell.getNumOfVehicles() * cell.getFreeFlowSpeed() * SimulationConstants.TIME_STEP)
								/ cell.getLength();
						if (cell.getNumOfVehicles() - cell.getOutflow() > 0.8) {
							delay += (ff - cell.getOutflow());
						}

					}
				}

				if (applyRampMetering && simulationTime % SimpleRampMeter.PHASE_MIN == 0) {
					StringBuffer buff = new StringBuffer("");
					for (double i : trafficLights) {
						String tl = i >= 0.5 ? "GREEN" : "RED";
						buff.append(tl + "\t");
					}

					netDelay += delay;
					// Positive reward if the current delay is lesser than the
					// previous
					double reward = Math.tanh((prev - delay) / prev);
					reinforce(reward, cellStateVector);
					System.out.println(delay + "\t" + reward + "\t" + trafficLights + "\t"
							+ simulationTime);
					prev = delay;
					delay = 0.0;
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

	private void reinforce(final double reward, final Vector input) {
		double baseline = 3000.0;
		double reinforcement = (reward) * ETA;
		List<Vector> activations = new ArrayList<>();
		List<Vector> zlList = new ArrayList<>();
		Vector activation = input;
		activations.add(input);
		Layer nnLayers[] = neuralNet.getNnLayers();
		int index = 0;
		int numOfLayers = neuralNet.getNumOfLayers();
		while (index < numOfLayers) {
			Matrix weight = nnLayers[index].getWeight();
			Vector bias = nnLayers[index].getBias();
			Vector zl = weight.multiply(activation);
			zl = zl.add(bias);
			zlList.add(zl);
			Vector op = nnLayers[index].getLayerOutput(activation);
			activations.add(op);
			activation = op;
			index++;
		}

		for (int i = numOfLayers; i >= 1; i--) {

			Vector delta = nnLayers[i - 1].getActivationFunction().sigmaPrimeBySigma(
					zlList.get(i - 1));

			// Vector bias = nnLayers[i - 1].getBias();
			Matrix weight = nnLayers[i - 1].getWeight();
			Matrix deltaW = delta.outerProduct(activations.get(i - 1)).multiply(reinforcement);
			// Vector deltaB = delta.multiply(reinforcement);
			// nnLayers[i - 1].setBias(bias.add(deltaB));
			nnLayers[i - 1].setWeight(weight.add(deltaW));

		}

	}
}
