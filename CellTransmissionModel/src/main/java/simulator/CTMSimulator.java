package simulator;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

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
 */
public class CTMSimulator implements Runnable {

	private static CellNetwork cellNetwork;
	public static long simulationTime;
	private static Properties configProperties;
	private static long startTime;
	private static long endTime;
	private static CTMSimViewer viewer;
	private static Map<Cell, Color> cellColorMap;

	// kept public for now. The road network, turn ratios and the mean
	// inter-arrival times for the source links.
	public static RoadNetworkModel roadNetwork;
	public static Map<Integer, Double> turnRatios;
	public static Map<Integer, Double> mergePriorities;
	public static Map<Integer, Double> interArrivalTimes;
	public static Random random;
	private boolean inAccident = false;
	private Thread rampThread;

	// Final variables
	private static final int PIE_ROADS[] = { 30633, 30634, 82, 28377, 30635, 28485, 30636, 38541,
			38260, 29309, 29310, 30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640, 30788,
			30789, 30790, 30641, 37976, 37981, 37980, 30642, 37982, 30643, 38539, 2355, 2356,
			28595, 30644, 22009, 29152, 28594, 30645, 28597, 30646, 19116, 19117, 29005, 30647,
			28387, 30648, 29552, 29553, 30649, 28611, 30650, 28613, 29131, 30651, 31985, 31991,
			30580, 28500, 30581 };
	private static final boolean HAVE_VIZ = true;
	private static final boolean HAVE_ACCIDENT = true;
	private static final boolean APPLY_METER = false;
	private static final String ACCIDENT_CELL = "30651_3";
	private static final Logger LOGGER = Logger.getLogger(CTMSimulator.class);

	static {
		random = new Random(110);
		roadNetwork = new QIRoadNetworkModel("jdbc:postgresql://172.25.187.111/abhinav", "abhinav",
				"qwert$$123", "qi_roads", "qi_nodes");
		turnRatios = new HashMap<Integer, Double>();
		mergePriorities = new HashMap<Integer, Double>();
		interArrivalTimes = new HashMap<Integer, Double>();

		List<Road> pieChangiOrdered = initialize();

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

		// Do not have long cells break them up. Just to see if this improves
		// performance in any way.
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
	}

	/**
	 * 
	 * @throws IOException
	 * @throws SQLException
	 */
	private CTMSimulator() throws IOException, SQLException {

		cellColorMap = new ConcurrentHashMap<>();
		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
				Color color = null;
				if (cell instanceof OrdinaryCell) {
					// Gray for ordinary.
					color = new Color(137, 112, 122, 225);
				} else if (cell instanceof MergingCell) {
					// Green for merging.
					color = new Color(51, 255, 153, 225);
				} else {
					// Blue for diverging.
					color = new Color(51, 51, 255, 225);
				}
				cellColorMap.put(cell, color);
			}

		}

		// Initialize the routing service provider.

		// Initialize the trip generator with required number of agents.
		LOGGER.info("Initializing trip generator");

		startTime = Long.parseLong(configProperties.getProperty("start.hour"));
		endTime = Long.parseLong(configProperties.getProperty("end.hour"));

	}

	private static void intializeViz(Properties dbConnectionProperties) {
		viewer = CTMSimViewer.getCTMViewerInstance("CTM Model", roadNetwork, cellColorMap,
				dbConnectionProperties);

	}

	/**
	 * Simulation initialization
	 */
	private static List<Road> initialize() {
		List<Road> pieChangiOrdered = new ArrayList<>();
		for (int roadId : PIE_ROADS) {
			Road road = roadNetwork.getAllRoadsMap().get(roadId);
			pieChangiOrdered.add(road);
		}
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(new File(
					"C:\\Users\\abhinav.sunderrajan\\Desktop\\Lanecount.txt")));

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
						interArrivalTimes.put(road.getRoadId(), 1.0 + random.nextDouble());
					else
						interArrivalTimes.put(road.getRoadId(), 5.25 + random.nextDouble());
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

	public static void main(String[] args) {
		try {
			configProperties = new Properties();
			configProperties.load(new FileInputStream("src/main/resources/config.properties"));
			final CTMSimulator ctmSim = new CTMSimulator();

			if (HAVE_VIZ) {
				Properties dbConnectionProperties = new Properties();
				dbConnectionProperties.load(new FileInputStream(
						"src/main/resources/connection.properties"));
				intializeViz(dbConnectionProperties);
			}

			System.out.println("Starting simulation..");

			Thread th = new Thread(ctmSim);
			th.start();

			final int meterCellNum = roadNetwork.getAllRoadsMap().get(29131).getRoadNodes().size() - 2;
			final Cell meterCell = cellNetwork.getCellMap().get("29131_" + meterCellNum);
			final double qMaxMeterCell = meterCell.getQmax();
			ctmSim.rampThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (ctmSim.inAccident) {
						boolean fullDensity = true;
						for (int i = 0; i < meterCellNum; i++) {
							Cell rampCell = cellNetwork.getCellMap().get("29131_" + i);
							if (rampCell.getnMax() > rampCell.getNumOfVehiclesInCell()) {
								fullDensity = false;
								break;
							}

						}
						if (!fullDensity) {
							meterCell.setQmax(0);
							System.out.println("Ramp meter red");
						} else {
							System.out.println("ramp meter green..");
							meterCell.setQmax(qMaxMeterCell);
						}
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

					}
					meterCell.setQmax(qMaxMeterCell);

				}
			});

			ctmSim.rampThread.setDaemon(true);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			LOGGER.error("Error reading trip generator or config or connection properties file", e);
		} catch (SQLException e) {
			LOGGER.error("Error in database operation.", e);
		}

	}

	@Override
	public void run() {
		try {

			Cell accidentCell = null;
			for (long time = startTime; time < endTime; time += SimulationConstants.TIME_STEP) {

				if (HAVE_ACCIDENT) {
					if (time == 600) {
						inAccident = true;
						System.err.println(time + " : ACCIDENT START!!");
						accidentCell = cellNetwork.getCellMap().get(ACCIDENT_CELL);
						accidentCell.setQmax(accidentCell.getQmax() / 4);
					}

					// Applying ramp metering

					if ((HAVE_ACCIDENT && APPLY_METER) && time == 600) {
						rampThread.start();
					}

					if (time == 2800) {
						System.out.println(time + ":Accident clears");
						accidentCell.setQmax(accidentCell.getQmax() * 4);
						inAccident = false;
						System.out.println("Stop ramp metering..");
					}

				}

				// Update the outflows for all cells
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateOutFlow();

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateNumberOfVehiclesInCell();
				if (time >= 600 && time <= 2800)
					System.out.print("Average cell density at " + time + ": ");
				double avgCellDensity = 0.0;
				int n = 0;
				for (Cell cell : cellNetwork.getCellMap().values()) {

					if (/*
						 * !(cell instanceof SinkCell || cell instanceof
						 * SourceCell)
						 */(cell.getCellId().contains("30650")
							|| cell.getCellId().contains("30649") || cell.getCellId().contains(
							"29131"))
							&& time >= 600 && time <= 2800) {
						// equation 1)
						// v=v_freeFlow-(v_freeFlow/jam_density)*density

						// equation 2) v=v_freeFlow* ln(jam_density/density)
						// The disadvantage of equation two is that speed tends
						// to infinity as the density becomes zero, this is
						// resolved as shown in the below equation.

						double cellDensity = cell.getNumOfVehiclesInCell() / cell.getnMax();
						cellColorMap.put(cell, CTMSimViewer.numberToColor(1.0 - cellDensity));
						// Compute average cell density for freeway alone.
						if (cell.getCellId().contains("30650")
								|| cell.getCellId().contains("30649")) {
							avgCellDensity += cellDensity;
							n++;
						}

					}

				}

				if (time >= 600 && time <= 2800)
					System.out.println(avgCellDensity / n);
				if (HAVE_VIZ)
					viewer.getMapFrame().repaint();
				Thread.sleep(/*
							 * (long) ((SimulationConstants.TIME_STEP * 1000) /
							 * SimulationConstants.PACE)
							 */50);

			}

			LOGGER.info("Finished simulation");

			Thread.sleep(100);
			viewer.getMapFrame().dispose();

		} catch (InterruptedException e) {
			LOGGER.error("Error waiting  for simulation time to advance.", e);
		}

	}
}
