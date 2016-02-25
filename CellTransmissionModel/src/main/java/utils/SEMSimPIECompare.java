package utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rnwmodel.Lane;
import rnwmodel.LaneModel;
import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * Get a snap shot of the SEMSim output for comparison to CTM using R Studio.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class SEMSimPIECompare {

	private static long roadIIDLong = 0;
	private static Map<Long, String> roadIIDMapping = new LinkedHashMap<Long, String>();
	private static final RoadNetworkModel ROAD_MODEL = new QIRoadNetworkModel(
			"jdbc:postgresql://172.25.187.111/abhinav", "abhinav", "qwert$$123", "qi_roads",
			"qi_nodes");

	private static final int PIE_ROADS[] = { 30632, 30633, 30634, 82, 28377, 30635, 28485, 30636,
			38541, 38260, 29309, 29310, 30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640,
			30788, 30789, 30790, 30641, 37976, 37981, 37980, 30642, 37982, 30643, 38539, 2355,
			2356, 28595, 30644, 22009, 29152, 28594, 30645, 28597, 30646, 19116, 19117, 29005,
			30647, 28387, 30648, 29552, 29553, 30649, 28611, 30650, 28613, 29131, 30651, 31985,
			31991, 30580, 28500, 30581 };

	public static void main(String[] args) throws IOException {

		int[] roadArr = { 30634, 30635, 30636, 30637, 30638, 30639, 30640, 30641, 37981, 30642,
				30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650, 30651, 30580, 30581 };
		double distance = 0.0;
		Road prev = null;
		HashMap<Integer, Double> distanceMap = new LinkedHashMap<>();
		for (Integer roadId : roadArr) {
			Road road = ROAD_MODEL.getAllRoadsMap().get(roadId);
			if (prev != null)
				distance += prev.getWeight();
			distanceMap.put(roadId, Math.round(distance * 100.0) / 100.0);
			prev = road;
		}

		List<Road> pieChangiOrdered = new ArrayList<>();
		for (int roadId : PIE_ROADS) {
			Road road = ROAD_MODEL.getAllRoadsMap().get(roadId);
			pieChangiOrdered.add(road);
		}

		// Set lane count for each road along P.I.E some what close to
		// reality.

		BufferedReader br = new BufferedReader(new FileReader(new File("Lanecount.txt")));
		while (br.ready()) {
			String line = br.readLine();
			String[] split = line.split("\t");
			Road road = ROAD_MODEL.getAllRoadsMap().get(Integer.parseInt(split[0]));
			road.setLaneCount(Integer.parseInt(split[2]));

		}
		br.close();

		LaneModel laneModel = new LaneModel(ROAD_MODEL);
		Map<String, List<Lane>> linkLaneMapping = laneModel.createLaneModel(pieChangiOrdered);

		for (String roadIID : linkLaneMapping.keySet()) {
			List<Lane> lanes = linkLaneMapping.get(roadIID);
			for (Lane lane : lanes) {
				if (!roadIIDMapping.containsValue(lane.roadLinkIID)) {
					roadIIDMapping.put(++roadIIDLong, lane.roadLinkIID);
				}
			}
		}

		File dir = new File("C:\\Users\\abhinav.sunderrajan\\Desktop\\SEMSim-output\\100percent");
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".csv");
			}
		});

		BufferedReader reader = null;

		BufferedWriter bw = new BufferedWriter(new FileWriter(new File("semsimop.txt")));

		for (int index = 0; index < files.length; index++) {
			System.out.println("Started reading " + files[index].getName());
			reader = new BufferedReader(new FileReader(files[index]));
			long minTime = -1;
			while (reader.ready()) {
				String line = reader.readLine();
				String[] split = line.split(",");

				if (split[1].contains(".") || split[2].equals("-1")) {
					continue;
				}
				int agent_id = Integer.parseInt(split[1]);

				if (agent_id % 1 == 0) {
					double lon = Double.parseDouble(split[3]);
					double lat = Double.parseDouble(split[4]);
					long time = Long.parseLong(split[0]) / 1000;
					if (time < 1200)
						continue;
					if (time > 1320)
						break;
					String roadSegment = roadIIDMapping.get(Long.parseLong(split[2]));
					String[] roadSegmentSplit = roadSegment.split("_");
					Integer roadId = Integer.parseInt(roadSegmentSplit[0]);
					Road road = ROAD_MODEL.getAllRoadsMap().get(roadId);
					Integer segment = Integer.parseInt(roadSegmentSplit[1]);
					if (distanceMap.containsKey(roadId)) {
						if (minTime == -1)
							minTime = time;
						time = time - minTime;

						double distanceAlongRoad = distanceMap.get(roadId);
						for (int i = 0; i < segment; i++) {
							distanceAlongRoad += road.getSegmentsLength()[i];
						}

						Coordinate node = road.getRoadNodes().get(segment).getPosition();
						double distanceToRoadNode = EarthFunctions.haversianDistance(node,
								new Coordinate(lon, lat));
						distanceAlongRoad = distanceAlongRoad - distanceToRoadNode;
						distanceAlongRoad = Math.round((distanceAlongRoad * 100.0) / 100.0);
						bw.write(time + "\t" + agent_id + "\t" + distanceAlongRoad + "\t"
								+ Double.parseDouble(split[5]) + "\n");
						bw.flush();

					}

				}
			}

			reader.close();
			bw.close();

		}

		System.out.println("Finished loading all SEMSim CSV to database.");
	}
}
