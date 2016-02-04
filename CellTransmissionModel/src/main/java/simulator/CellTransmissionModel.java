package simulator;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
	private List<RampMeter> meteredRamps;
	private BufferedWriter bw;

	// Static variables
	private static CellTransmissionModel simulator;
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);

	/**
	 * Initialize the Cell transmission model by creating the cell network from
	 * the roads.
	 * 
	 * @throws IOException
	 */
	private void intializeCTM(Collection<Road> roadCollection, boolean haveAccident,
			boolean applyMetering, boolean haveViz) throws IOException {

		this.ramps = new ArrayList<>();
		cellColorMap = new ConcurrentHashMap<Cell, Color>();
		this.meteredRamps = new ArrayList<RampMeter>();
		this.haveVisualization = haveViz;
		this.pieChangiOrdered = roadCollection;
		SimulatorCore.df.setRoundingMode(RoundingMode.CEILING);
		bw = new BufferedWriter(new FileWriter(new File("simoutput.csv")));
		bw.write("time,cell-id,speed,density,flow\n");

		LOGGER.info("Create a cell based network for the roads.");
		cellNetwork = new CellNetwork(pieChangiOrdered, ramps);

		LOGGER.info("Initializing traffic state for the cells created..");
		// TrafficStateInitialize.parseXML();
		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
				int nt = (int) (cell.getCriticalDensity() * cell.getLength() * cell.getNumOfLanes() * 1.25);
				cell.setNumberOfvehicles(nt);

				double density = nt / (cell.getLength() * cell.getNumOfLanes());
				double meanSpeed = cell.getFreeFlowSpeed()
						* Math.exp((-1 / SimulationConstants.AM)
								* Math.pow((density / cell.getCriticalDensity()),
										SimulationConstants.AM));
				cell.setMeanSpeed(meanSpeed);
				System.out.println(cell.getCellId() + "--> mean:" + meanSpeed + " freeflow:"
						+ cell.getFreeFlowSpeed() + " nt:" + nt + " nmax:" + cell.getnMax());
			}
		}

		LOGGER.info("Traffic state for the cells initialized..");

		// for (Road ramp : ramps) {
		// RampMeter rampMeter = new RampMeter(ramp);
		// rampMeter.setQueuePercentage(0.7);
		// meteredRamps.add(rampMeter);
		// }

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

		try {
			intializeCTM(roadCollection, haveAccident, applyMetering, haveViz);
		} catch (IOException e) {
			LOGGER.error("error creating simulation-output file");
		}

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

				// Update the receiving potential,
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell))
						cell.determineReceivePotential();
				}

				// Update out flow and the speed of cells.
				for (Cell cell : cellNetwork.getCellMap().values()) {
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
							bw.write(simulationTime + "," + cell.getCellId() + ","
									+ cell.getMeanSpeed() + "," + (cell.getDensity() * 1000.0)
									+ "," + (cell.getDensity() * cell.getMeanSpeed() * 3600) + "\n");
						}
					}

					viewer.getMapFrame().repaint();
					Thread.sleep(10);
					if (simulationTime % 100 == 0)
						System.out.println("time:" + simulationTime);
				}
			}

			System.out.println("Total waiting time is "
					+ SimulatorCore.df.format((totalWaitingTime / (60.0))) + " minutes");

			LOGGER.info("Finished " + ((endTime - startTime) / 60.0) + " minute simulation in :"
					+ (System.currentTimeMillis() - time) + " ms");

			bw.flush();
			Thread.sleep(100);
			if (haveVisualization)
				viewer.getMapFrame().dispose();

		} catch (InterruptedException e) {
			LOGGER.error("Error waiting  for simulation time to advance.", e);
		} catch (IOException e) {
			LOGGER.error("Error writing to file");
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
