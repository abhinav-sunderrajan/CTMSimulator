package simulator;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import main.SimulatorCore;

import org.apache.log4j.Logger;

import rnwmodel.Road;
import utils.TrafficStateInitialize;
import viz.CTMSimViewer;
import viz.ColorHelper;
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

public class CellTransmissionModel implements Callable<Double> {

	private long simulationTime;
	private long endTime;
	private CTMSimViewer viewer;
	private Map<Cell, Color> cellColorMap;
	private boolean haveVisualization;
	private CellNetwork cellNetwork;
	private List<Road> ramps;
	private Map<Cell, RampMeter> meteredRamps;
	private boolean applyRampMetering;
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);
	private boolean determineRampFlows;
	private double ntTotal = 0.0;
	private SimulatorCore core;
	private static final boolean PRINT_FINAL_STATE = false;
	private static final String SIMULATION_OP_PATH = "C:/Users/abhinav.sunderrajan/Desktop/MapMatch/MapMatchingStats/ctmop.txt";

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

		TrafficStateInitialize.parseXML(cellNetwork, core);

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
	 * @param core2
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
	public CellTransmissionModel(SimulatorCore core, boolean haveAccident, boolean applyMetering,
			boolean haveViz, boolean determineRampFlows, long simTime) {
		this.core = core;
		this.applyRampMetering = applyMetering;
		this.determineRampFlows = determineRampFlows;
		if (determineRampFlows && !applyRampMetering) {
			throw new IllegalStateException(
					"To determine the ramp flows, ramp metering must be enabled");
		}

		ramps = new ArrayList<Road>();
		meteredRamps = new LinkedHashMap<Cell, RampMeter>();
		Cell.setApplyRampMetering(applyMetering);
		cellNetwork = new CellNetwork(core.getPieChangi().values(), ramps);
		Cell.setRamps(ramps);
		for (Road ramp : ramps) {
			RampMeter rampMeter = new RampMeter(ramp, cellNetwork);
			meteredRamps.put(rampMeter.getMeterCell(), rampMeter);
		}

		// intializeTrafficState(roadCollection);

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

			long tStart = System.currentTimeMillis();

			for (simulationTime = 0; simulationTime <= endTime; simulationTime += SimulationConstants.TIME_STEP) {

				// Update the sending potential of cells
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (!(cell instanceof SourceCell || cell instanceof SinkCell)) {
						cell.determineSendingPotential();
						cell.determineReceivePotential();
					}
				}

				// Update out flow of cells.
				for (Cell cell : cellNetwork.getCellMap().values()) {
					if (applyRampMetering && simulationTime >= 900) {
						if (meteredRamps.containsKey(cell)) {
							meteredRamps.get(cell).regulateOutFlow(simulationTime);
						} else {
							cell.updateOutFlow();
						}
					} else {
						cell.updateOutFlow();
					}

				}

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values()) {
					cell.updateNumberOfVehiclesInCell();

					// Reduce the total number of vehicles after warm up time of
					// 15 minutes.
					if (simulationTime > 900) {
						if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
							ntTotal += cell.getNumOfVehicles();
						}
					}

				}

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateAnticipatedDensity();

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateMeanSpeed();

				if (haveVisualization) {
					for (Cell cell : cellNetwork.getCellMap().values()) {
						if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
							cellColorMap.put(cell, ColorHelper.numberToColor(cell.getMeanSpeed()));
							// System.out.println(cell.getMeanSpeed() + "\t" +
							// cell.getDensity());
						}
					}

					viewer.getMapFrame().repaint();
					Thread.sleep(50);
				}

			}

			// System.out.println("finish:" + (System.currentTimeMillis() -
			// tStart));

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

		double leastSquare = 0.0;
		int mainRoads[] = { 30634, 30635, 30636, 30637, 30638, 30639, 30640, 30641, 37981, 30642,
				30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650, 30651, 30580, 30581 };

		double distance = 0.0;
		Road prev = null;
		HashMap<Integer, Double> distanceMap = new LinkedHashMap<>();
		for (Integer roadId : mainRoads) {
			Road road = core.getPieChangi().get(roadId);
			if (prev != null)
				distance += prev.getWeight();
			distanceMap.put(roadId, Math.round(distance * 100.0) / 100.0);
			prev = road;
		}

		List<Integer> pieList = new ArrayList<>();
		for (int roadId : mainRoads)
			pieList.add(roadId);

		BufferedWriter bw = null;
		// State at the end of simulation
		if (PRINT_FINAL_STATE) {
			bw = new BufferedWriter(new FileWriter(new File(SIMULATION_OP_PATH)));
			bw.write("cell_id\tspeed\tnum_of_vehicles\tdistance\tdensity\n");
		}

		// Minor deletions.
		Map<Double, Double> speedDistanceMap = new TreeMap<Double, Double>();
		Map<Double, Cell> cellDistancemap = new HashMap<>();

		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (cell.getCellId().contains("source") || cell.getCellId().contains("sink"))
				continue;
			if (pieList.contains(cell.getRoad().getRoadId())) {
				double speed = Math.round(cell.getMeanSpeed() * 100.0) / 100.0;
				String split[] = cell.getCellId().split("_");
				Integer roadId = Integer.parseInt(split[0]);
				Road road = core.getPieChangi().get(roadId);
				Integer segment = Integer.parseInt(split[1]);
				if (distanceMap.containsKey(roadId)) {
					double distanceAlongRoad = distanceMap.get(roadId);
					for (int i = 0; i < segment; i++) {
						distanceAlongRoad += road.getSegmentsLength()[i];
					}
					distanceAlongRoad = Math.round((distanceAlongRoad * 100.0) / 100.0);
					if (PRINT_FINAL_STATE) {
						bw.write(cell.getCellId() + "\t" + speed + "\t" + cell.getNumOfVehicles()
								+ "\t" + distanceAlongRoad + "\t" + cell.getDensity() + "\n");
					} else {
						speedDistanceMap.put(distanceAlongRoad, cell.getNumOfVehicles());
						cellDistancemap.put(distanceAlongRoad, cell);
					}
				}
			}

		}

		if (PRINT_FINAL_STATE) {
			bw.flush();
			bw.close();
			System.out.println("Printed file to " + SIMULATION_OP_PATH);
			return ntTotal;
		} else {
			// for (Entry<Double, Double> entry :
			// SimulatorCore.semSIMDistanceMap.entrySet()) {
			// double weight = 1.0;
			// double diff = entry.getValue() -
			// speedDistanceMap.get(entry.getKey());
			// // If difference is greater than 20 percent than penalize more
			// if (Math.abs(diff / entry.getValue()) > 0.25)
			// weight = 1.0 + Math.abs(diff / entry.getValue());
			// leastSquare += diff * weight * diff;
			// }
			// return
			// Double.parseDouble(SimulatorCore.df.format(Math.sqrt(leastSquare)));

			return ntTotal;
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
