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
import rnwmodel.RoadNode;
import roadsection.PIESEMSimXMLGenerator;

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

	public static void main(String[] args) throws IOException {

		int[] roadArr = { 30634, 30635, 30636, 30637, 30638, 30639, 30640, 30641, 37981, 30642,
				30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650, 30651, 30580, 30581 };
		double distance = 0.0;
		Road prev = null;
		HashMap<Integer, Double> distanceMap = new LinkedHashMap<>();
		for (Integer roadId : roadArr) {
			Road road = PIESEMSimXMLGenerator.roadModel.getAllRoadsMap().get(roadId);
			if (prev != null)
				distance += prev.getWeight();
			distanceMap.put(roadId, Math.round(distance * 100.0) / 100.0);
			prev = road;
		}


		for (String roadIID : PIESEMSimXMLGenerator.linkLaneMapping.keySet()) {
			List<Lane> lanes = PIESEMSimXMLGenerator.linkLaneMapping.get(roadIID);
			for (Lane lane : lanes) {
				if (!roadIIDMapping.containsValue(lane.roadLinkIID)) {
					roadIIDMapping.put(++roadIIDLong, lane.roadLinkIID);
				}
			}
		}

		File dir = new File("C:\\Users\\abhinav.sunderrajan\\Desktop\\SEMSim-output\\100percent\\resources");
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
					if (time < 1779)
						continue;
					if (time >= 1780)
						break;
					String roadSegment = roadIIDMapping.get(Long.parseLong(split[2]));
					String[] roadSegmentSplit = roadSegment.split("_");
					Integer roadId = Integer.parseInt(roadSegmentSplit[0]);
					Road road = PIESEMSimXMLGenerator.roadModel.getAllRoadsMap().get(roadId);
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

		System.out.println("Finished...");
	}
}
