package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import main.SimulatorCore;
import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.mongodb.BasicDBObject;

public class CTMDataToDB {

	private EventHandler<MessageInternal> handler;
	private Disruptor<MessageInternal> disruptor;
	private RingBuffer<MessageInternal> ringBuffer;

	private static RoadNetworkModel roadNetworkModel;
	private static DatabaseAccess dba;

	private static final int PIE_ROADS[] = { 30634, 30635, 30636, 30637, 30638, 30639, 30640,
			30641, 37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650,
			30651, 30580, 30581 };
	private static ExecutorService EXECUTOR = Executors.newCachedThreadPool();
	private final static int RING_SIZE = 65536;

	private static boolean isAccident = true;
	private static int iterCount = 2;
	private static char CONGESTION = 'H';

	static {

		try {
			Properties dbConnectionProperties = new Properties();
			dbConnectionProperties.load(new FileInputStream(
					"src/main/resources/connectionLocal.properties"));
			roadNetworkModel = new QIRoadNetworkModel(dbConnectionProperties, "qi_roads",
					"qi_nodes");
			dba = new DatabaseAccess(dbConnectionProperties);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public CTMDataToDB() {

		try {
			dba.setBlockExecutePS("INSERT INTO  ctm_output VALUES (?,?,?,?,?,?,?,?)", 10);
		} catch (BatchUpdateException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// Initialize the handler first
		handler = new EventHandler<MessageInternal>() {

			@Override
			public void onEvent(final MessageInternal object, final long sequence,
					final boolean endOfBatch) throws Exception {
				dba.executeBlockUpdate(object.getLong("timestamp"), object.getDouble("speed"),
						object.getDouble("distance_along_road"), object.getInt("road_id"),
						object.getInt("segment"), object.getInt("iteration"), isAccident,
						CONGESTION);
			}
		};
		disruptor = new Disruptor<MessageInternal>(MessageInternal.EVENT_FACTORY, EXECUTOR,
				new SingleThreadedClaimStrategy(RING_SIZE), new SleepingWaitStrategy());
		disruptor.handleEventsWith(handler);
		ringBuffer = disruptor.start();
	}

	public static void main(String[] args) throws IOException {

		CTMDataToDB ssd = new CTMDataToDB();

		HashMap<Integer, Double> distanceMap = new LinkedHashMap<>();

		double distance = 0.0;
		Road prev = null;
		List<Road> pieChangiOrdered = new ArrayList<Road>();
		for (int roadId : PIE_ROADS) {
			Road road = roadNetworkModel.getAllRoadsMap().get(roadId);
			pieChangiOrdered.add(road);
			if (prev != null)
				distance += prev.getWeight();
			distanceMap.put(roadId, Math.round(distance * 100.0) / 100.0);
			prev = road;
		}

		SimulatorCore.repair(pieChangiOrdered);

		BufferedReader reader = new BufferedReader(new FileReader(new File("ctmsim_acc.txt")));
		while (reader.ready()) {
			String line = reader.readLine();
			String[] split = line.split(",");

			int time = Integer.parseInt(split[3]) - 800;
			if (time > 7200)
				break;

			Integer roadId = Integer.parseInt(split[0]);
			Road road = roadNetworkModel.getAllRoadsMap().get(roadId);
			Integer segment = Integer.parseInt(split[1]);
			if (distanceMap.containsKey(roadId)) {

				double distanceAlongRoad = distanceMap.get(roadId);
				for (int i = 0; i < segment; i++) {
					distanceAlongRoad += road.getSegmentsLength()[i];
				}

				long sequence = ssd.ringBuffer.next();
				MessageInternal next = ssd.ringBuffer.get(sequence);
				next.put("speed", Double.parseDouble(split[2]));
				next.put("timestamp", time);
				next.put("distance_along_road", (Math.round(distanceAlongRoad * 100.0) / 100.0));
				next.put("road_id", roadId);
				next.put("segment", segment);
				next.put("iteration", iterCount);
				ssd.ringBuffer.publish(sequence);
			}

		}

		reader.close();
		System.out.println("Finished loading iteration:" + iterCount);
		++iterCount;

		System.out.println("Finished loading all CTM text to database.");
	}

	private static class MessageInternal extends BasicDBObject {

		private static final long serialVersionUID = 1L;
		public static final EventFactory<MessageInternal> EVENT_FACTORY = new EventFactory<MessageInternal>() {
			public MessageInternal newInstance() {
				return new MessageInternal();
			}
		};

	}
}
