package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pie.PIESEMSimXMLGenerator;

import rnwmodel.Lane;
import rnwmodel.LaneModel;
import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;
import utils.DatabaseAccess;
import utils.EarthFunctions;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.vividsolutions.jts.geom.Coordinate;

public class SEMSimDataToDB {

	private EventHandler<BSONDataType> handler;
	private Disruptor<BSONDataType> disruptor;
	private RingBuffer<BSONDataType> ringBuffer;
	private static long roadIIDLong = 0;
	private static Map<Long, String> roadIIDMapping = new LinkedHashMap<Long, String>();
	private final static int RING_SIZE = 65536;
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();


	private static boolean isAccident = false;
	private static int iterCount = 1;
	private static char CONGESTION = 'M';

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
		handler = new EventHandler<BSONDataType>() {

			@Override
			public void onEvent(final BSONDataType object, final long sequence,
					final boolean endOfBatch) throws Exception {
				access.executeBlockUpdate(object.getInt("agent_id"), object.getLong("timestamp"),
						object.getDouble("lat"), object.getDouble("lon"),
						object.getDouble("speed"), object.getDouble("distance_along_road"),
						object.getInt("road_id"), object.getInt("iteration"), isAccident,
						CONGESTION);
			}
		};
		disruptor = new Disruptor<BSONDataType>(BSONDataType.EVENT_FACTORY, EXECUTOR,
				new SingleThreadedClaimStrategy(RING_SIZE), new SleepingWaitStrategy());
		disruptor.handleEventsWith(handler);
		ringBuffer = disruptor.start();
	}

	public static void main(String[] args) throws IOException {

		SEMSimDataToDB ssd = new SEMSimDataToDB();

		HashMap<Integer, Double> distanceMap = new LinkedHashMap<>();

		int[] pieMain = { 30634, 30635, 30636, 30637, 30638, 30639, 30640, 30641, 37981, 30642,
				30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650, 30651, 30580, 30581 };
		double distance = 0.0;
		Road prev = null;
		for (Integer roadId : pieMain) {
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

		File dir = new File("C:\\Users\\abhinav.sunderrajan\\Desktop\\SEMSim-output\\100percent");
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

				if (split[1].contains(".") || split[2].equals("-1")) {
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

						long sequence = ssd.ringBuffer.next();
						BSONDataType next = ssd.ringBuffer.get(sequence);
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
	}
}
