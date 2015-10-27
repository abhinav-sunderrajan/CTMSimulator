package simulator;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.log4j.Logger;

import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;
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

// TODO The simulation needs to spit out the total travel time across the
// freeway and the total waiting time if any across the ramps each time-step.
// TODO Have to design a reward system at the end of every time-step for
// TD/Q-based Reinforcement learning. Put some thought into the reward
// functions.

public class CTMSimulator implements Runnable {

	public static CellNetwork cellNetwork;
	public static List<Road> ramps = new ArrayList<>();
	public static long simulationTime;
	private static Properties configProperties;
	private static long startTime;
	private static long endTime;
	private static CTMSimViewer viewer;
	private static Map<Cell, Color> cellColorMap;

	// kept public for now. The road network, turn ratios and the mean
	// inter-arrival times for the source links
	public static RoadNetworkModel roadNetwork;
	public static Map<Integer, Double> turnRatios;
	public static Map<Integer, Double> mergePriorities;
	public static Map<Integer, Double> interArrivalTimes;
	public static Random random;
	private static CTMSimulator simulator;
	// Final variables
	private static int pieRoads[];
	private static boolean haveVisualization = true;
	private static boolean simulateAccident = true;
	private static boolean applyRampMetering = true;
	private static final String ACCIDENT_CELL = "30651_3";
	private static List<RampMeter> meteredRamps = new ArrayList<RampMeter>();
	private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
	private List<Cell> modifiedCells = new ArrayList<>();
	private static final Logger LOGGER = Logger.getLogger(CTMSimulator.class);

	private static void intialize(int[] roadIds, boolean haveAccident, boolean applyMetering,
			boolean haveViz) {
		random = new Random(SimulationConstants.SEED);
		cellColorMap = new ConcurrentHashMap<>();
		haveVisualization = haveViz;
		simulateAccident = haveAccident;
		applyRampMetering = applyMetering;

		pieRoads = roadIds;
		roadNetwork = new QIRoadNetworkModel("jdbc:postgresql://172.25.187.111/abhinav", "abhinav",
				"qwert$$123", "qi_roads", "qi_nodes");
		turnRatios = new HashMap<Integer, Double>();
		mergePriorities = new HashMap<Integer, Double>();
		interArrivalTimes = new HashMap<Integer, Double>();

		List<Road> pieChangiOrdered = createCellNetwork();

		// Need to do some repairs the roads are not exactly perfect.
		// 1) Ensure that none of the roads have a single cell this is
		// prone to errors. hence add an extra node in the middle of these
		// single segment roads.
		int nodeId = Integer.MAX_VALUE;
		for (Road road : pieChangiOrdered) {
			if (road.getSegmentsLength().length == 1) {
				double x = (road.getBeginNode().getX() + road.getEndNode().getX()) / 2.0;
				double y = (road.getBeginNode().getY() + road.getEndNode().getY()) / 2.0;
				road.getRoadNodes().add(1, new RoadNode(nodeId--, x, y));
			}
		}

		// Do not have long cells break them up.
		for (Road road : pieChangiOrdered) {
			for (int i = 0; i < road.getSegmentsLength().length; i++) {
				if (road.getSegmentsLength()[i] > 120.0) {
					double x = (road.getRoadNodes().get(i).getX() + road.getRoadNodes().get(i + 1)
							.getX()) / 2.0;
					double y = (road.getRoadNodes().get(i).getY() + road.getRoadNodes().get(i + 1)
							.getY()) / 2.0;
					road.getRoadNodes().add(i + 1, new RoadNode(nodeId--, x, y));
				}
			}
		}

		LOGGER.info("Create a cell based network for the roads.");
		cellNetwork = new CellNetwork(pieChangiOrdered);

		for (Road ramp : ramps) {
			RampMeter rampMeter = new RampMeter(ramp);
			rampMeter.setQueuePercentage(0.8);
			meteredRamps.add(rampMeter);
		}

		try {
			configProperties = new Properties();
			configProperties.load(new FileInputStream("src/main/resources/config.properties"));
			if (haveVisualization) {
				Properties dbConnectionProperties = new Properties();
				dbConnectionProperties.load(new FileInputStream(
						"src/main/resources/connection.properties"));
				viewer = CTMSimViewer.getCTMViewerInstance("CTM Model", roadNetwork, cellColorMap,
						dbConnectionProperties);
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			LOGGER.error("Error reading config file", e);
		}

	}

	/**
	 * Gets the CTM simulator instance.
	 * 
	 * @param roadIds
	 *            the roads to be simulated.
	 * @param haveAccident
	 *            simulate an accident?
	 * @param applyMetering
	 *            apply ramp metering ?
	 * @param haveViz
	 *            have a visualization?
	 * @return the CTM simulator instance.
	 */
	public static CTMSimulator getSimulatorInstance(int[] roadIds, boolean haveAccident,
			boolean applyMetering, boolean haveViz) {
		if (simulator == null) {
			intialize(roadIds, haveAccident, applyMetering, haveViz);
			simulator = new CTMSimulator();
		}
		return simulator;
	}

	private CTMSimulator() {

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

		startTime = Long.parseLong(configProperties.getProperty("start.hour"));
		endTime = Long.parseLong(configProperties.getProperty("end.hour"));

	}

	/**
	 * Simulation initialization
	 */
	private static List<Road> createCellNetwork() {
		List<Road> pieChangiOrdered = new ArrayList<>();
		for (int roadId : pieRoads) {
			Road road = roadNetwork.getAllRoadsMap().get(roadId);
			pieChangiOrdered.add(road);
		}
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(new File("src/main/resources/Lanecount.txt")));

			while (br.ready()) {
				String line = br.readLine();
				String[] split = line.split("\t");
				Road road = roadNetwork.getAllRoadsMap().get(Integer.parseInt(split[0]));
				road.setLaneCount(Integer.parseInt(split[2]));

			}
			br.close();

			for (Road road : pieChangiOrdered) {
				RoadNode beginNode = road.getBeginNode();
				RoadNode endNode = road.getEndNode();
				List<Road> ins = new ArrayList<>();
				for (Road inRoad : beginNode.getInRoads()) {
					if (pieChangiOrdered.contains(inRoad) && !inRoad.equals(road))
						ins.add(inRoad);
				}

				List<Road> outs = new ArrayList<>();
				for (Road outRoad : endNode.getOutRoads()) {
					if (pieChangiOrdered.contains(outRoad))
						outs.add(outRoad);
				}

				if (ins.size() == 0) {
					if (road.getRoadClass() == 0)
						interArrivalTimes.put(road.getRoadId(), 0.10 + random.nextDouble());
					else
						interArrivalTimes.put(road.getRoadId(), 1.25);
				}

				if (ins.size() > 1) {
					for (Road inRoad : ins) {

						if (inRoad.getKind().equalsIgnoreCase("Ramps")
								|| inRoad.getKind().equalsIgnoreCase("Interchange"))
							mergePriorities.put(inRoad.getRoadId(), 1.0 / road.getLaneCount());
						else
							mergePriorities.put(inRoad.getRoadId(),
									((double) inRoad.getLaneCount() / road.getLaneCount()));

					}

				}

				if (outs.size() > 1) {
					double x = 0.25;
					for (Road outRoad : outs) {
						if (outRoad.getKind().equalsIgnoreCase("Slip Road")
								|| outRoad.getKind().equalsIgnoreCase("Interchange")) {
							turnRatios.put(outRoad.getRoadId(), x);
						} else {
							turnRatios.put(outRoad.getRoadId(), 1 - x);
						}
					}

				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return pieChangiOrdered;

	}

	/**
	 * @return the roadNetwork
	 */
	public static RoadNetworkModel getRoadNetwork() {
		return roadNetwork;
	}

	@Override
	public void run() {
		try {

			Cell accidentCell = cellNetwork.getCellMap().get(ACCIDENT_CELL);
			long time = System.currentTimeMillis();

			for (simulationTime = startTime; simulationTime < endTime; simulationTime += SimulationConstants.TIME_STEP) {

				// Simulate an accident.
				if (simulateAccident) {
					if (simulationTime == 5) {
						System.err.println(simulationTime + " : ACCIDENT START!!");
						accidentCell.setQmax(accidentCell.getQmax() / 4);
					}
				}

				// Applying ramp metering
				if (applyRampMetering) {
					for (RampMeter rampMeter : meteredRamps) {
						scheduledExecutor.execute(rampMeter);
					}
				}

				// Update the outflows for all cells
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateOutFlow();

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateNumberOfVehiclesInCell();

				// Reset all modified cells to their defaiult model values
				if (simulationTime % (SimulationConstants.TIME_STEP * 2) == 0) {
					for (Cell cell : modifiedCells)
						cell.reset();
					modifiedCells.clear();
				}

				// This loop performs two functions.
				// 1) Computes cell statistics such as density and average speed
				// used to compute travel time and waiting times.

				// The second function is to introduce some degree of
				// stochasticity by varying leff and time-gap.

				double totalTravelTime = 0.0;
				double totalWaitingTime = 0.0;
				for (Cell cell : cellNetwork.getCellMap().values()) {
					double cellSpeed = cell.getFreeFlowSpeed();
					if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {

						// v=v_freeFlow-(v_freeFlow/jam_density)*density
						// v=v_freeFlow* ln(jam_density/density)

						double cellDensity = cell.getNumOfVehiclesInCell() / cell.getnMax();
						if (cell.getNumOfVehiclesInCell() > 0) {
							cellSpeed = cell.getFreeFlowSpeed() * Math.log(1.0 / cellDensity);
						}

						if (cellSpeed > cell.getFreeFlowSpeed())
							cellSpeed = cell.getFreeFlowSpeed();

						if (cell.getRoad().getName().contains("P.I.E (Changi)")) {
							if (cellSpeed > 2.0)
								totalTravelTime += cell.getLength() / cellSpeed;
							else
								totalWaitingTime += SimulationConstants.TIME_STEP;

						}

						if (haveVisualization)
							cellColorMap.put(cell, CTMSimViewer.numberToColor(1.0 - cellDensity));

						// Introduce some kind of variations
						// As noticed if the cell speed is less cars tend to
						// bunch together and the reduce their time and distance
						// headway.
						if (cellSpeed < 5.0) {
							cell.introduceStochasticty(
									uniform(SimulationConstants.LEFF * 0.4,
											SimulationConstants.LEFF * 0.6),
									uniform(SimulationConstants.TIME_GAP * 0.6,
											SimulationConstants.TIME_GAP * 0.8));

						} else {
							// Modify the dynamics of 1/3rd of all cells by
							// changing their time and distance headway every
							// three time steps.
							if (simulationTime % (SimulationConstants.TIME_STEP * 3) == 0) {
								if (random.nextDouble() < 0.3) {
									// Do not mess with the accident cell and
									// the metered ramp cells.
									boolean isMetercell = false;
									for (RampMeter rampMeter : meteredRamps) {
										if (rampMeter.getMeterCell().equals(cell)) {
											isMetercell = true;
											break;
										}
									}

									if (!(isMetercell || accidentCell.equals(cell))) {
										cell.introduceStochasticty(
												uniform(SimulationConstants.LEFF * 0.5,
														SimulationConstants.LEFF * 1.5),
												uniform(SimulationConstants.TIME_GAP * 0.8,
														SimulationConstants.TIME_GAP * 1.4));
										modifiedCells.add(cell);
									}

								}
							}

						}

					}

				}

				if (haveVisualization) {
					// Repaint the map frame.
					viewer.getMapFrame().repaint();
					Thread.sleep(50);
				}

				System.out.println("Travel time  is " + (totalTravelTime / 60.0)
						+ " and waiting time is " + (totalWaitingTime / 60.0) + " minutes");

			}

			LOGGER.info("Finished " + ((endTime - startTime) / 60.0) + " minute simulation in :"
					+ (System.currentTimeMillis() - time) + " ms");
			Thread.sleep(100);
			if (haveVisualization)
				viewer.getMapFrame().dispose();

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
		return a + random.nextDouble() * (b - a);
	}

}
