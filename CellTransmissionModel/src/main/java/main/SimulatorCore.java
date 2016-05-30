package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;
import simulator.CellTransmissionModel;
import simulator.SimulationConstants;
import strategy.RampMeter;
import utils.RoadRepairs;
import utils.ThreadPoolExecutorService;

public class SimulatorCore {

	// kept public for now.
	private Properties dbConnectionProperties;
	private RoadNetworkModel roadNetwork;
	private Map<Integer, Double> turnRatios;
	private Map<Integer, Double> mergePriorities;
	private Map<Integer, Double> flowRates;
	private Random random;

	public static final int PIE_ROADS[] = { 30633, 30634, 82, 28377, 30635, 28485, 30636, 29310,
			30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640, 30790, 30641, 37976, 37981,
			37980, 30642, 37982, 30643, 38539, 28595, 30644, 29152, 28594, 30645, 28597, 30646,
			29005, 30647, 28387, 30648, 29553, 30649, 28611, 30650, 28613, 29131, 30651, 31991,
			30580, 28500, 30581 };
	private static final Logger LOGGER = Logger.getLogger(SimulatorCore.class);
	public static final DecimalFormat df = new DecimalFormat("#.###");
	public static final SAXReader SAX_READER = new SAXReader();
	private Map<Integer, Road> pieChangi;
	private static SimulatorCore instance;

	// To be deleted

	private Map<Double, Double> semSIMDistanceMap = new TreeMap<Double, Double>();

	private void initialize(long seed) {

		try {
			// Initialization.
			random = new Random(seed);
			df.setRoundingMode(RoundingMode.CEILING);
			dbConnectionProperties = new Properties();
			dbConnectionProperties.load(new FileInputStream(
					"src/main/resources/connectionLocal.properties"));
			roadNetwork = new QIRoadNetworkModel(dbConnectionProperties, "qi_roads", "qi_nodes");

			pieChangi = new HashMap<Integer, Road>();
			for (int roadId : PIE_ROADS) {
				Road road = roadNetwork.getAllRoadsMap().get(roadId);
				pieChangi.put(roadId, road);
			}

			// Delete not needed
			// BufferedReader br = new BufferedReader(new FileReader(new
			// File("dist-num-map.txt")));
			// if (br.ready()) {
			// while (true) {
			// String line = br.readLine();
			// if (line == null)
			// break;
			// String split[] = line.split("\t");
			// semSIMDistanceMap.put(Double.parseDouble(split[0]),
			// Double.parseDouble(split[1]));
			// }
			// }

			// br.close();

			BufferedReader br = new BufferedReader(new FileReader(new File("Lanecount.txt")));

			while (br.ready()) {
				String line = br.readLine();
				String[] split = line.split("\t");
				if (pieChangi.containsKey(Integer.parseInt(split[0])))
					pieChangi.get(Integer.parseInt(split[0])).setLaneCount(
							Integer.parseInt(split[2]));
			}
			br.close();

			// Repair the roads
			repair(pieChangi.values());

			// Test repair
			for (Road road : pieChangi.values()) {
				double minLength = (road.getSpeedLimit()[1] * (5 / 18.0))
						* SimulationConstants.TIME_STEP;
				for (int i = 0; i < road.getSegmentsLength().length; i++) {
					if (road.getSegmentsLength()[i] < minLength) {
						throw new IllegalStateException(
								"cell length cannot be less than mimimum value for road:"
										+ road.getRoadId());
					}
				}
			}

			turnRatios = new HashMap<Integer, Double>();
			mergePriorities = new HashMap<Integer, Double>();
			flowRates = new HashMap<Integer, Double>();

			mergeTurnAndInterArrivals(pieChangi.values());
		} catch (FileNotFoundException e) {
			LOGGER.error("Unable to find the properties file", e);
		} catch (IOException e) {
			LOGGER.error("Error reading config file", e);
		}

	}

	private SimulatorCore(long seed) {
		initialize(seed);
	}

	/**
	 * Get instance of the simulator.
	 * 
	 * @param seed
	 * @return
	 */
	public static SimulatorCore getInstance(long seed) {
		if (instance == null) {
			instance = new SimulatorCore(seed);
		}

		return instance;

	}

	/**
	 * This method serves to ensure that none of the road segments which
	 * ultimately form the cells have a length smaller than the minimum length
	 * of V0*delta_T.
	 * 
	 * @param pieChangi
	 */
	public static void repair(Collection<Road> pieChangi) {

		System.out.println("Repairing roads..");
		// Need to do some repairs the roads are not exactly perfect.
		// 1) Ensure that none of the roads have a single cell this is
		// prone to errors. hence add an extra node in the middle of these
		// single segment roads.
		int nodeId = Integer.MAX_VALUE;
		for (Road road : pieChangi) {
			if (road.getSegmentsLength().length == 1) {
				double x = (road.getBeginNode().getX() + road.getEndNode().getX()) / 2.0;
				double y = (road.getBeginNode().getY() + road.getEndNode().getY()) / 2.0;
				road.getRoadNodes().add(1, new RoadNode(nodeId--, x, y));
			}

		}

		// The main constraint of CTM is that the length of cell i li>v*delta_T
		// This loop ensures that no cell is smaller than v*delta_T.
		for (Road road : pieChangi) {
			double minLength = (road.getSpeedLimit()[1] * (5 / 18.0))
					* SimulationConstants.TIME_STEP;

			int numOfSegments = road.getSegmentsLength().length;

			// If number of segments is equal to two which is usually off and on
			// ramps
			if (numOfSegments == 2)
				RoadRepairs.repairTwoSegmentRoads(road, minLength, pieChangi);

			if (numOfSegments > 2)
				RoadRepairs.repairMultiSegmentRoads(road, minLength, pieChangi);

		}

		RoadRepairs.breakLongSegments(pieChangi, --nodeId);

	}

	public static void main(String args[]) throws InterruptedException, ExecutionException {
		ThreadPoolExecutor executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();

		double queuePercentages[] = { 0.03, 0.0, 0.0, 0.0, 0.11, 0.0, 0.8, 0.0, 0.0, 0.0, 0.0 };
		Random randLocal = new Random();
		SimulatorCore core = SimulatorCore.getInstance(1);

		double meanQos = 0.0;
		int trials = 1;
		for (int i = 0; i < trials; i++) {
			core.random.setSeed(randLocal.nextLong());
			CellTransmissionModel ctm = new CellTransmissionModel(core,
					SimOptions.getOption(SimOptions.NO_ACC), false,
					SimOptions.getOption(SimOptions.HAVE_VIZ), 7100);
			int index = 0;
			for (RampMeter meter : ctm.getMeteredRamps().values())
				meter.setQueuePercentage(queuePercentages[index++]);

			Future<Double> future = executor.submit(ctm);
			Double qos = future.get();
			meanQos += Math.round(qos);
			System.out.print(Math.round(qos) + ",");
		}

		System.out.println((meanQos / trials));

		executor.shutdown();

	}

	/**
	 * Set the merge priorities, turn ratios and inter-arrival times for the
	 * roads to be simulated.
	 * 
	 * @param pieChangi
	 */
	private void mergeTurnAndInterArrivals(Collection<Road> pieChangi) {
		try {
			Document document = SAX_READER.read("road_state.xml");

			// Flow at sources
			Element flow = document.getRootElement().element("Flow");
			for (Iterator<?> i = flow.elementIterator("source"); i.hasNext();) {
				Element source = (Element) i.next();
				flowRates.put(Integer.parseInt(source.attributeValue("id")),
						Double.parseDouble(source.getStringValue()));
			}

			// Merge priorities at on-ramps
			Element mergePriority = document.getRootElement().element("MergePriorities");
			for (Iterator<?> i = mergePriority.elementIterator("merge"); i.hasNext();) {
				Element merge = (Element) i.next();
				List<Element> roadElementList = merge.elements("road");
				for (Element roadElement : roadElementList) {
					mergePriorities.put(Integer.parseInt(roadElement.attributeValue("id")),
							Double.parseDouble(roadElement.getStringValue()));

				}

			}

			// Turn ratios at off-ramps
			Element turnRatioElements = document.getRootElement().element("TurnRatios");
			for (Iterator<?> i = turnRatioElements.elementIterator("turn"); i.hasNext();) {
				Element turn = (Element) i.next();
				List<Element> roadElementList = turn.elements("road");
				for (Element roadElement : roadElementList) {
					turnRatios.put(Integer.parseInt(roadElement.attributeValue("id")),
							Double.parseDouble(roadElement.getStringValue()));
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * @return the dbConnectionProperties
	 */
	public Properties getDbConnectionProperties() {
		return dbConnectionProperties;
	}

	/**
	 * @return the roadNetwork
	 */
	public RoadNetworkModel getRoadNetwork() {
		return roadNetwork;
	}

	/**
	 * @return the turnRatios
	 */
	public Map<Integer, Double> getTurnRatios() {
		return turnRatios;
	}

	/**
	 * @return the mergePriorities
	 */
	public Map<Integer, Double> getMergePriorities() {
		return mergePriorities;
	}

	/**
	 * @return the flowRates
	 */
	public Map<Integer, Double> getFlowRates() {
		return flowRates;
	}

	/**
	 * @return the random
	 */
	public Random getRandom() {
		return random;
	}

	/**
	 * @return the pieRoads
	 */
	public static int[] getPieRoads() {
		return PIE_ROADS;
	}

	/**
	 * @return the pieChangi
	 */
	public Map<Integer, Road> getPieChangi() {
		return pieChangi;
	}

}
