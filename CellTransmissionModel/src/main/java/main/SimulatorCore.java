package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.apache.log4j.Logger;

import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;
import simulator.CellTransmissionModel;
import simulator.SimulationConstants;

public class SimulatorCore {

	// kept public for now.
	public static Properties configProperties;
	public static Properties dbConnectionProperties;
	public static RoadNetworkModel roadNetwork;
	public static Map<Integer, Double> turnRatios;
	public static Map<Integer, Double> mergePriorities;
	public static Map<Integer, Double> interArrivalTimes;
	public static Random random;
	public static CellTransmissionModel cellTransmissionModel;

	private static final int PIE_ROADS[] = { 30633, 30634, 82, 28377, 30635, 28485, 30636, 29310,
			30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640, 30790, 30641, 37976, 37981,
			37980, 30642, 37982, 30643, 38539, 28595, 30644, 29152, 28594, 30645, 28597, 30646,
			29005, 30647, 28387, 30648, 29553, 30649, 28611, 30650, 28613, 29131, 30651, 31991,
			30580, 28500, 30581 };
	private static final Logger LOGGER = Logger.getLogger(SimulatorCore.class);
	public static final DecimalFormat df = new DecimalFormat("#.###");

	// TODO In repair roads When I break very large cells the simulation seems
	// to break down for some inexplicable reason. Find out why. Breaking up
	// large cells should improve graphs but I'm unable to do now.

	// TODO Have to design a reward system at the end of every time-step for
	// TD/Q-based Reinforcement learning. Put some thought into the reward
	// functions.
	public static void main(String args[]) {

		try {
			// Initialization.
			random = new Random(SimulationConstants.SEED);
			df.setRoundingMode(RoundingMode.CEILING);
			configProperties = new Properties();
			configProperties.load(new FileInputStream("src/main/resources/config.properties"));
			dbConnectionProperties = new Properties();
			dbConnectionProperties.load(new FileInputStream(
					"src/main/resources/connectionLocal.properties"));
			roadNetwork = new QIRoadNetworkModel(SimulatorCore.dbConnectionProperties, "qi_roads",
					"qi_nodes");

			Map<Integer, Road> pieChangi = new HashMap<Integer, Road>();
			for (int roadId : PIE_ROADS) {
				Road road = roadNetwork.getAllRoadsMap().get(roadId);
				double freeFlowSpeed = road.getRoadClass() == 0 ? 80 * 5.0 / 18 : 60.0 * 5.0 / 18;
				if (roadId == 37980 || roadId == 28387)
					freeFlowSpeed = 40 * 5.0 / 18.0;
				road.setFreeFlowSpeed(freeFlowSpeed);
				pieChangi.put(roadId, road);
			}

			BufferedReader br = new BufferedReader(new FileReader(new File(
					"src/main/resources/Lanecount.txt")));

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
				double minLength = road.getFreeFlowSpeed() * SimulationConstants.TIME_STEP;
				for (int i = 0; i < road.getSegmentsLength().length; i++) {
					if (road.getSegmentsLength()[i] < minLength) {
						throw new IllegalStateException(
								"cell length cannot be less than mimimum value");
					}
				}
			}

			turnRatios = new HashMap<Integer, Double>();
			mergePriorities = new HashMap<Integer, Double>();
			interArrivalTimes = new HashMap<Integer, Double>();

			mergeTurnAndInterArrivals(pieChangi.values());

			cellTransmissionModel = CellTransmissionModel.getSimulatorInstance(pieChangi.values(),
					false, false, true);
			Thread th = new Thread(cellTransmissionModel);
			th.start();

		} catch (FileNotFoundException e) {
			LOGGER.error("Unable to find the properties file", e);
		} catch (IOException e) {
			LOGGER.error("Error reading config file", e);
		}

	}

	/**
	 * This method serves to ensure that none of the road segments which
	 * ultimately form the cells have a length smaller than the minimum length
	 * of V0*delta_T.
	 * 
	 * @param pieChangi
	 */
	public static void repair(Collection<Road> pieChangi) {

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

		// The main constraint of CTM is that the length of cell i li>=v*delta_T
		// This loop ensures that no cell is smaller than v*delta_T.
		for (Road road : pieChangi) {
			double minLength = road.getFreeFlowSpeed() * SimulationConstants.TIME_STEP;
			if (road.getSegmentsLength().length > 2) {
				while (true) {
					boolean noSmallSegments = true;
					int numOfSegments = road.getSegmentsLength().length;
					if (numOfSegments < 3)
						break;
					for (int i = 0; i < numOfSegments; i++) {
						if (road.getSegmentsLength()[i] < minLength) {
							if (i == numOfSegments - 1)
								road.getRoadNodes().remove(i);
							else
								road.getRoadNodes().remove(i + 1);
							noSmallSegments = false;
							break;
						}
					}
					if (noSmallSegments)
						break;
				}

			}

		}

		// // Do not have long cells break them up. Just to see if this improves
		// // graphs generated in any way.
		// for (Road road : pieChangi) {
		// double minLength = road.getFreeFlowSpeed() *
		// SimulationConstants.TIME_STEP;
		// while (true) {
		// boolean noLargeSegments = true;
		// int numOfSegments = road.getSegmentsLength().length;
		// for (int i = 0; i < numOfSegments; i++) {
		// if (road.getSegmentsLength()[i] > (minLength * 2.5)) {
		// double x = (road.getRoadNodes().get(i).getX() + road.getRoadNodes()
		// .get(i + 1).getX()) / 2.0;
		// double y = (road.getRoadNodes().get(i).getY() + road.getRoadNodes()
		// .get(i + 1).getY()) / 2.0;
		// road.getRoadNodes().add(i + 1, new RoadNode(nodeId--, x, y));
		// noLargeSegments = false;
		// break;
		// }
		// }
		// if (noLargeSegments)
		// break;
		// }
		// }

	}

	/**
	 * Set the merge priorities, turn ratios and inter-arrival times for the
	 * roads to be simulated.
	 * 
	 * @param pieChangi
	 */
	private static void mergeTurnAndInterArrivals(Collection<Road> pieChangi) {
		try {

			for (Road road : pieChangi) {
				RoadNode beginNode = road.getBeginNode();
				RoadNode endNode = road.getEndNode();
				List<Road> ins = new ArrayList<>();
				for (Road inRoad : beginNode.getInRoads()) {
					if (pieChangi.contains(inRoad) && !inRoad.equals(road))
						ins.add(inRoad);
				}

				List<Road> outs = new ArrayList<>();
				for (Road outRoad : endNode.getOutRoads()) {
					if (pieChangi.contains(outRoad))
						outs.add(outRoad);
				}

				// Inter-arrival rates represents the number of vehicles that
				// enter a source link every time step
				if (ins.size() == 0) {
					double capacityPerLane = road.getFreeFlowSpeed()
							/ (road.getFreeFlowSpeed() * SimulationConstants.TIME_GAP + SimulationConstants.LEFF);

					if (road.getRoadClass() == 0)
						interArrivalTimes.put(road.getRoadId(),
								capacityPerLane * road.getLaneCount()
										* SimulationConstants.TIME_STEP * 0.8);
					else
						interArrivalTimes.put(road.getRoadId(),
								capacityPerLane * road.getLaneCount()
										* SimulationConstants.TIME_STEP * 0.7);
				}

				if (ins.size() > 1) {
					for (Road inRoad : ins) {

						if (inRoad.getKind().equalsIgnoreCase("Ramps")
								|| inRoad.getKind().equalsIgnoreCase("Interchange")) {
							mergePriorities.put(inRoad.getRoadId(), 0.5);
						} else {
							mergePriorities.put(inRoad.getRoadId(), 0.9);
						}

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

	}
}
