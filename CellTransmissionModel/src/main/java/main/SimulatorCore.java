package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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

	private static final int PIE_ROADS[] = { 30633, 30634, 82, 28377, 30635, 28485, 30636, 38541,
			38260, 29309, 29310, 30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640, 30788,
			30789, 30790, 30641, 37976, 37981, 37980, 30642, 37982, 30643, 38539, 2355, 2356,
			28595, 30644, 22009, 29152, 28594, 30645, 28597, 30646, 19116, 19117, 29005, 30647,
			28387, 30648, 29553, 30649, 28611, 30650, 28613, 29131, 30651, 31985, 31991, 30580,
			28500, 30581 };
	private static final Logger LOGGER = Logger.getLogger(SimulatorCore.class);

	public static void main(String args[]) {

		try {
			// Initialization.
			random = new Random(SimulationConstants.SEED);
			configProperties = new Properties();
			configProperties.load(new FileInputStream("src/main/resources/config.properties"));
			dbConnectionProperties = new Properties();
			dbConnectionProperties.load(new FileInputStream(
					"src/main/resources/connectionLocal.properties"));
			roadNetwork = new QIRoadNetworkModel(SimulatorCore.dbConnectionProperties, "qi_roads",
					"qi_nodes");

			Map<Integer, Road> pieChangi = new HashMap<Integer, Road>();
			for (int roadId : PIE_ROADS)
				pieChangi.put(roadId, roadNetwork.getAllRoadsMap().get(roadId));

			BufferedReader br;
			br = new BufferedReader(new FileReader(new File("src/main/resources/Lanecount.txt")));

			while (br.ready()) {
				String line = br.readLine();
				String[] split = line.split("\t");
				if (pieChangi.containsKey(Integer.parseInt(split[0])))
					pieChangi.get(Integer.parseInt(split[0])).setLaneCount(
							Integer.parseInt(split[2]));
			}
			br.close();
			turnRatios = new HashMap<Integer, Double>();
			mergePriorities = new HashMap<Integer, Double>();
			interArrivalTimes = new HashMap<Integer, Double>();

			mergeTurnAndInterArrivals(pieChangi.values());

			// Get the singleton instance of CTM to start the simulation thread.

			cellTransmissionModel = CellTransmissionModel.getSimulatorInstance(pieChangi.values(),
					false, false, false);
			Thread th = new Thread(cellTransmissionModel);
			th.start();

		} catch (FileNotFoundException e) {
			LOGGER.error("Unable to find the properties file", e);
		} catch (IOException e) {
			LOGGER.error("Error reading config file", e);
		}

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

				if (ins.size() == 0) {
					if (road.getRoadClass() == 0)
						interArrivalTimes.put(road.getRoadId(), 1.0);
					else
						interArrivalTimes.put(road.getRoadId(), 2.25);
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

	}
}
