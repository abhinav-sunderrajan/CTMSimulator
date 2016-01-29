package simulator;

import java.awt.Color;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import main.SimulatorCore;

import org.apache.log4j.Logger;

import rnwmodel.Road;
import viz.CTMSimViewer;
import ctm.Cell;
import ctm.CellNetwork;
import ctm.MergingCell;
import ctm.OrdinaryCell;
import ctm.SinkCell;
import ctm.SourceCell;

/**
 * Main singleton class which initializes and advances a PIE scale macroscopic
 * Cell transmission based traffic simulation.
 * 
 * @see <a
 *      href="http://en.wikipedia.org/wiki/Cell_Transmission_Model">http://en.wikipedia.org/wiki/Cell_Transmission_Model</a>
 * 
 * @author abhinav
 * 
 * 
 */

public class CellTransmissionModel implements Runnable {

	public static CellNetwork cellNetwork;
	public List<Road> ramps;
	public long simulationTime;
	private long startTime;
	private long endTime;
	private CTMSimViewer viewer;
	private Map<Cell, Color> cellColorMap;
	private Collection<Road> pieChangiOrdered;
	private boolean haveVisualization;
	private boolean simulateAccident;
	private boolean applyRampMetering;
	private List<RampMeter> meteredRamps;
	private List<Cell> modifiedCells = new ArrayList<>();

	// Static variables
	private static CellTransmissionModel simulator;
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);

	/**
	 * Initialize the Cell transmission model by creating the cell network from
	 * the roads.
	 */
	private void intializeCTM(Collection<Road> roadCollection, boolean haveAccident,
			boolean applyMetering, boolean haveViz) {

		this.ramps = new ArrayList<>();
		cellColorMap = new ConcurrentHashMap<Cell, Color>();
		this.meteredRamps = new ArrayList<RampMeter>();
		this.haveVisualization = haveViz;
		this.simulateAccident = haveAccident;
		this.applyRampMetering = applyMetering;
		this.pieChangiOrdered = roadCollection;
		SimulatorCore.df.setRoundingMode(RoundingMode.CEILING);

		LOGGER.info("Create a cell based network for the roads.");
		cellNetwork = new CellNetwork(pieChangiOrdered, ramps);

		// LOGGER.info("Initializing traffic state for the cells created..");
		// TrafficStateInitialize.parseXML();
		// LOGGER.info("Traffic state for the cells initialized..");

		for (Road ramp : ramps) {
			RampMeter rampMeter = new RampMeter(ramp);
			rampMeter.setQueuePercentage(0.7);
			meteredRamps.add(rampMeter);
		}

		if (haveVisualization) {
			viewer = CTMSimViewer.getCTMViewerInstance("CTM Model", SimulatorCore.roadNetwork,
					cellColorMap, SimulatorCore.dbConnectionProperties);
		}

	}

	/**
	 * Gets the CTM simulator instance.
	 * 
	 * @param roadCollection
	 *            the roads to be simulated.
	 * @param haveAccident
	 *            simulate an accident?
	 * @param applyMetering
	 *            apply ramp metering ?
	 * @param haveViz
	 *            have a visualization?
	 * @return the CTM simulator instance.
	 */
	public static CellTransmissionModel getSimulatorInstance(Collection<Road> roadCollection,
			boolean haveAccident, boolean applyMetering, boolean haveViz) {
		if (simulator == null) {
			simulator = new CellTransmissionModel(roadCollection, haveAccident, applyMetering,
					haveViz);
		}
		return simulator;
	}

	private CellTransmissionModel(Collection<Road> roadCollection, boolean haveAccident,
			boolean applyMetering, boolean haveViz) {

		intializeCTM(roadCollection, haveAccident, applyMetering, haveViz);

		// Color coding the visualization
		if (haveVisualization) {
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

		startTime = Long.parseLong(SimulatorCore.configProperties.getProperty("start.hour"));
		endTime = Long.parseLong(SimulatorCore.configProperties.getProperty("end.hour"));

	}

	/**
	 * @return the cellNetwork
	 */
	public CellNetwork getCellNetwork() {
		return cellNetwork;
	}

	@Override
	public void run() {
		try {
			Cell accidentCell = cellNetwork.getCellMap().get(SimulationConstants.ACCIDENT_CELL);
			long time = System.currentTimeMillis();
			double totalWaitingTime = 0.0;

			for (simulationTime = startTime; simulationTime <= endTime; simulationTime += SimulationConstants.TIME_STEP) {

				// Update the sending potential of cells
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell))
						cell.determineSendingPotential();

				}

				// Update the receiving potential, out flow and the speed of
				// cells.
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell))
						cell.determineReceivePotential();
					cell.updateOutFlow();
					// Update mean speed flow/density.
					if (!(cell instanceof SourceCell || cell instanceof SinkCell)) {

						if (cell.getNumOfVehicles() > 0) {
							cell.setMeanSpeed(cell.getOutflow() * cell.getLength()
									/ (cell.getNumOfVehicles() * SimulationConstants.TIME_STEP));
						} else {
							cell.setMeanSpeed(cell.getFreeFlowSpeed());
						}

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

				if (simulationTime % SimulationConstants.TIME_STEP == 0) {
					System.out.println("Finished iteration at " + simulationTime);
				}

				// This loop performs two functions.
				// 1) Computes cell statistics such as density and average speed
				// used to compute travel time and waiting times.

				if (haveVisualization) {
					for (Cell cell : cellNetwork.getCellMap().values()) {
						if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
							cellColorMap.put(
									cell,
									CTMSimViewer.numberToColor(cell.getNumOfVehicles()
											/ cell.getnMax()));
						}
					}

					viewer.getMapFrame().repaint();
					Thread.sleep(50);
				}
			}

			System.out.println("Total waiting time is "
					+ SimulatorCore.df.format((totalWaitingTime / (60.0))) + " minutes");

			LOGGER.info("Finished " + ((endTime - startTime) / 60.0) + " minute simulation in :"
					+ (System.currentTimeMillis() - time) + " ms");
			Thread.sleep(100);
			// if (haveVisualization)
			// viewer.getMapFrame().dispose();

		} catch (InterruptedException e) {
			LOGGER.error("Error waiting  for simulation time to advance.", e);
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
	public static double uniform(double a, double b) {
		if (!(a < b))
			throw new IllegalArgumentException("Invalid range");
		return a + SimulatorCore.random.nextDouble() * (b - a);
	}

}
