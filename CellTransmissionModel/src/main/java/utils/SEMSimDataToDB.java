package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import main.CombineMapMatchData.MessageInternal;
import rnwmodel.Lane;
import rnwmodel.LaneModel;
import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import utils.DatabaseAccess;
import utils.EarthFunctions;
import analytics.PIESEMSimXMLGenerator;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.vividsolutions.jts.geom.Coordinate;

public class SEMSimDataToDB {

	private EventHandler<MessageInternal> handler;
	private Disruptor<MessageInternal> disruptor;
	private RingBuffer<MessageInternal> ringBuffer;
	private static long roadIIDLong = 0;
	private static Map<Long, String> roadIIDMapping = new LinkedHashMap<Long, String>();

	private final static int RING_SIZE = 65536;
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
	private static final RoadNetworkModel ROAD_MODEL = new QIRoadNetworkModel(
			"jdbc:postgresql://172.25.187.111/abhinav", "abhinav", "qwert$$123", "qi_roads",
			"qi_nodes");
	private static final DatabaseAccess DB = new DatabaseAccess(
			"jdbc:postgresql://172.25.187.111/abhinav", "abhinav", "qwert$$123");

	private static final int PIE_ROADS[] = { 30632, 30633, 30634, 82, 28377, 30635, 28485, 30636,
			38541, 38260, 29309, 29310, 30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640,
			30788, 30789, 30790, 30641, 37976, 37981, 37980, 30642, 37982, 30643, 38539, 2355,
			2356, 28595, 30644, 22009, 29152, 28594, 30645, 28597, 30646, 19116, 19117, 29005,
			30647, 28387, 30648, 29552, 29553, 30649, 28611, 30650, 28613, 29131, 30651, 31985,
			31991, 30580, 28500, 30581 };

	private static boolean isAccident = false;
	private static int iterCount = 47;
	private static char CONGESTION = 'H';

	@SuppressWarnings("unchecked")
	public SEMSimDataToDB() {
		final DatabaseAccess access = new DatabaseAccess(
				"jdbc:postgresql://172.25.187.111/abhinav", "abhinav", "qwert$$123");
		;
		try {
			access.setBlockExecutePS("INSERT INTO  semsim_output VALUES (?,?,?,?,?,?,?,?,?,?)", 10);
		} catch (BatchUpdateException e) {
			e.printStackTrace();
			e.getNextException().printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// Initialize the handler first
		handler = new EventHandler<MessageInternal>() {

			@Override
			public void onEvent(final MessageInternal object, final long sequence,
					final boolean endOfBatch) throws Exception {
				access.executeBlockUpdate(object.getInt("agent_id"), object.getLong("timestamp"),
						object.getDouble("lat"), object.getDouble("lon"),
						object.getDouble("speed"), object.getDouble("distance_along_road"),
						object.getInt("road_id"), object.getInt("iteration"), isAccident,
						CONGESTION);
			}
		};
		disruptor = new Disruptor<MessageInternal>(MessageInternal.EVENT_FACTORY, EXECUTOR,
				new SingleThreadedClaimStrategy(RING_SIZE), new SleepingWaitStrategy());
		disruptor.handleEventsWith(handler);
		ringBuffer = disruptor.start();
	}

	public static void main(String[] args) throws IOException {

		try {

			String expresswayRoadList = null;
			SEMSimDataToDB ssd = new SEMSimDataToDB();

			ResultSet rs = DB
					.retrieveQueryResult("SELECT road_id_list from express_way_groupings where road_name LIKE 'P.I.E (Changi)' and town LIKE 'CENTRAL'");
			while (rs.next())
				expresswayRoadList = rs.getString("road_id_list");
			rs.close();

			HashMap<Integer, Double> distanceMap = new LinkedHashMap<>();

			String roads = expresswayRoadList.replace("(", "");
			roads = roads.replace(")", "");
			String roadArr[] = roads.split(",");
			double distance = 0.0;
			Road prev = null;
			for (String roadStr : roadArr) {
				Integer roadId = Integer.parseInt(roadStr);
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

			// Reorder roads as exists on the map
			PIESEMSimXMLGenerator.reOrderRoads(pieChangiOrdered);

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

			File dir = new File(
					"C:\\Users\\abhinav.sunderrajan\\Desktop\\SEMSim-output\\100percent");
			File[] files = dir.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(".csv");
				}
			});

			BufferedReader reader = null;

			for (int index = 0; index < files.length; index++) {
				System.out.println("Started reading " + files[index].getName());
				reader = new BufferedReader(new FileReader(files[index]));
				long minTime = -1;
				while (reader.ready()) {
					String line = reader.readLine();
					String[] split = line.split(",");

					if (split[1].contains(".")) {
						System.out.println(split[0] + " " + split[1] + " " + split[2]);
						continue;
					}
					int agent_id = Integer.parseInt(split[1]);

					if (agent_id % 1 == 0) {
						double lon = Double.parseDouble(split[3]);
						double lat = Double.parseDouble(split[4]);
						long time = Long.parseLong(split[0]) / 1000;
						if (time > 7200)
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

							long sequence = ssd.ringBuffer.next();
							MessageInternal next = ssd.ringBuffer.get(sequence);
							next.put("agent_id", agent_id);
							next.put("lon", lon);
							next.put("lat", lat);
							next.put("speed", Double.parseDouble(split[5]));
							next.put("timestamp", time);
							next.put("distance_along_road",
									(Math.round(distanceAlongRoad * 100.0) / 100.0));
							next.put("road_id", roadId);
							next.put("iteration", iterCount);
							ssd.ringBuffer.publish(sequence);
						}

					}
				}

				reader.close();
				System.out.println("Finished loading iteration:" + iterCount);
				++iterCount;

			}

			System.out.println("Finished loading all SEMSim CSV to database.");
		} catch (SQLException e) {
			e.printStackTrace();
			e.getNextException().printStackTrace();
		}
	}
}
