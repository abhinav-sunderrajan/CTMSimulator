package main;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pie.PIESEMSimXMLGenerator;
import rnwmodel.Lane;
import beans.BeanPak;
import beans.CellStateBean;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;

import esper.EsperOperatorFactory;
import esper.FileStreamer;
import esper.Subscriber;

/**
 * This main class launches a data stream subscriber. It also launches an Esper
 * based stream processing system to have obtain the current traffic state in
 * all cells constituting the cell network..
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class MiddlewareCore {

	private static Map<Long, String> roadIIDMapping = new HashMap<Long, String>();
	private static long roadIIDLong = 0;

	static {
		for (String roadIID : PIESEMSimXMLGenerator.linkLaneMapping.keySet()) {
			List<Lane> lanes = PIESEMSimXMLGenerator.linkLaneMapping.get(roadIID);
			for (Lane lane : lanes) {
				if (!roadIIDMapping.containsValue(lane.roadLinkIID)) {
					roadIIDMapping.put(++roadIIDLong, lane.roadLinkIID);
				}
			}
		}
	}

	public static void main(String args[]) throws IOException, InterruptedException {

		// Instantiate esper operators for extracting per cell id statistics.

		final EsperOperatorFactory esperInstance = new EsperOperatorFactory("cell-stats");
		esperInstance
				.createEsperOperatorWithSQL(
						"SELECT cellId,COUNT(*),AVG(speed),STDDEV(speed) FROM "
								+ "beans.CellStateBean.win:ext_timed(time, 10 seconds) GROUP BY cellId having count(*) >10",
						new Subscriber() {
							@Override
							public void processOutputStream(Object... objects) {
								System.out.println(objects[0] + "<<>>" + objects[1] + "<<>>"
										+ objects[2] + "<<>>" + objects[3]);

							}
						});

		// create a data stream listener.
		DisruptorFactory<? extends BeanPak> disruptorInstance = new DisruptorFactory<CellStateBean>() {

			@Override
			public void implementLMAXHandler() {
				BeanPak.EVENT_FACTORY = new EventFactory<CellStateBean>() {
					public CellStateBean newInstance() {
						return new CellStateBean();
					}
				};

				disruptor = new Disruptor<CellStateBean>(
						(EventFactory<CellStateBean>) CellStateBean.EVENT_FACTORY, EXECUTOR,
						new SingleThreadedClaimStrategy(RING_SIZE), new SleepingWaitStrategy());
				handler = new EventHandler<CellStateBean>() {

					@Override
					public void onEvent(final CellStateBean bean, final long sequence,
							boolean endOfBatch) throws Exception {
						esperInstance.getCepRT().sendEvent(bean);
					}
				};

			}
		};

		// read and stream from file for now.
		FileStreamer streamer = new FileStreamer(
				"C:\\Users\\abhinav.sunderrajan\\Desktop\\SEMSim-output\\100percent\\output-wintersim\\Scenario-1",
				disruptorInstance);
		streamer.startSEMSimStream();

	}

	/**
	 * @return the roadIIDMapping
	 */
	public static Map<Long, String> getRoadIIDMapping() {
		return roadIIDMapping;
	}

}
