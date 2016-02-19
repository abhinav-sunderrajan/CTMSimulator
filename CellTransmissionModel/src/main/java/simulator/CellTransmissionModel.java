package simulator;

import java.awt.Color;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import main.SimulatorCore;

import org.apache.log4j.Logger;

import rnwmodel.Road;
import utils.TrafficStateInitialize;
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
 * @see <a
 *      href="http://en.wikipedia.org/wiki/Cell_Transmission_Model">http://en.wikipedia.org/wiki/Cell_Transmission_Model</a>
 * 
 * @author abhinav
 * 
 * 
 */

public class CellTransmissionModel implements Callable<Integer> {

	private long simulationTime;
	private long endTime;
	private CTMSimViewer viewer;
	private Map<Cell, Color> cellColorMap;
	private boolean haveVisualization;
	private int tts;
	private CellNetwork cellNetwork;
	private List<Road> ramps;
	private Map<Cell, RampMeter> meteredRamps;
	private boolean applyRampMetering;
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);
	private boolean determineRampFlows;

	/**
	 * Initialize the Cell transmission model by creating the cell network from
	 * the roads.
	 * 
	 */
	public void intializeTrafficState(Collection<Road> roadCollection) {

		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (!(cell instanceof SinkCell || cell instanceof SourceCell))
				cell.setInitilalized(false);
		}

		TrafficStateInitialize.parseXML(cellNetwork);

		// The XML does not have information regarding the on/off ramps so have
		// a very bad fix now. Need to resolve this.
		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
				if (!cell.isInitilalized()) {
					int nt = (int) (cell.getCriticalDensity() * cell.getLength() * cell
							.getNumOfLanes());
					cell.setNumberOfvehicles(nt);

					double density = nt / (cell.getLength() * cell.getNumOfLanes());
					double meanSpeed = cell.getFreeFlowSpeed()
							* Math.exp((-1 / SimulationConstants.AM)
									* Math.pow((density / cell.getCriticalDensity()),
											SimulationConstants.AM));
					cell.setMeanSpeed(meanSpeed);
					cell.setInitilalized(true);
				}
			}
		}

	}

	/**
	 * 
	 * @param roadCollection
	 *            collection of roads to be simulated
	 * @param haveAccident
	 *            simulate an accident?
	 * @param applyMetering
	 *            apply ramp metering?
	 * @param haveViz
	 *            enable visualization?
	 * @param determineRampFlows
	 *            determine ramp flows once the correct queue percentage values
	 *            for all on ramps are determined;
	 * @param simTime
	 *            time over which to simulate.
	 */
	public CellTransmissionModel(Collection<Road> roadCollection, boolean haveAccident,
			boolean applyMetering, boolean haveViz, boolean determineRampFlows, long simTime) {

		this.applyRampMetering = applyMetering;
		this.determineRampFlows = determineRampFlows;
		if (determineRampFlows && !applyRampMetering) {
			throw new IllegalStateException(
					"To determine the ramp flows, ramp metering must be enabled");
		}

		ramps = new ArrayList<Road>();
		meteredRamps = new LinkedHashMap<Cell, RampMeter>();
		cellNetwork = new CellNetwork(roadCollection, ramps);
		for (Road ramp : ramps) {
			RampMeter rampMeter = new RampMeter(ramp, cellNetwork);
			meteredRamps.put(rampMeter.getMeterCell(), rampMeter);
		}

		intializeTrafficState(roadCollection);

		cellColorMap = new ConcurrentHashMap<Cell, Color>();
		this.haveVisualization = haveViz;
		SimulatorCore.df.setRoundingMode(RoundingMode.CEILING);

		if (haveVisualization) {
			viewer = CTMSimViewer.getCTMViewerInstance("CTM Model", SimulatorCore.roadNetwork,
					cellColorMap, SimulatorCore.dbConnectionProperties);

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
	public Integer call() {
		try {

			for (simulationTime = 0; simulationTime <= endTime; simulationTime += SimulationConstants.TIME_STEP) {

				// Update the sending potential of cells
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell))
						cell.determineSendingPotential();
				}

				// Update the receiving potential
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell))
						cell.determineReceivePotential();
				}

				// Update out flow and the speed of cells.
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (applyRampMetering) {
						if (meteredRamps.containsKey(cell)) {
							meteredRamps.get(cell).regulateOutFlow();
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
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateMeanSpeed();

				// This loop performs two functions.
				// 1) Computes cell statistics such as density and average speed
				// used to compute travel time and waiting times.

				if (haveVisualization) {
					for (Cell cell : cellNetwork.getCellMap().values()) {
						if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
							cellColorMap.put(
									cell,
									CTMSimViewer.numberToColor(cell.getMeanSpeed()
											/ cell.getFreeFlowSpeed()));
						}
					}

					viewer.getMapFrame().repaint();
					Thread.sleep(10);
				}

				// Thread.sleep(100);
			}

			// System.out.println("Finished " + ((endTime) / 60.0) +
			// " minute simulation in :"
			// + (System.currentTimeMillis() - time) + " ms");

			tts = 0;
			for (Cell cell : cellNetwork.getCellMap().values()) {
				if (!(cell instanceof SourceCell || cell instanceof SinkCell)) {
					tts += cell.getNumOfVehicles();
				}
			}

			tts = tts * SimulationConstants.TIME_STEP;

			if (haveVisualization)
				viewer.getMapFrame().dispose();

		} catch (InterruptedException e) {
			LOGGER.error("Error waiting  for simulation time to advance.", e);
		}

		if (determineRampFlows) {
			for (RampMeter meter : meteredRamps.values()) {
				System.out.println(meter.getMeterCell().getRoad() + "--> queue:"
						+ meter.getQueuePercentage() + " total red-time:" + meter.getTotalRedTime()
						+ " total green-time:" + meter.getTotalGreenTime());
			}
		}

		return tts;

	}

	/**
	 * @return the total time spent.
	 */
	public int getTts() {
		return tts;
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
	public static double uniform(double a, double b) {
		if (!(a < b))
			throw new IllegalArgumentException("Invalid range");
		return a + SimulatorCore.random.nextDouble() * (b - a);
	}

	/**
	 * @return the meteredRamps
	 */
	public Map<Cell, RampMeter> getMeteredRamps() {
		return meteredRamps;
	}

	/**
	 * @param meteredRamps
	 *            the meteredRamps to set
	 */
	public void setMeteredRamps(Map<Cell, RampMeter> meteredRamps) {
		this.meteredRamps = meteredRamps;
	}

}
