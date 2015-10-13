package utils;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.pool2.impl.GenericObjectPool;

import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;

/**
 * Class for parallel computation of routes backed by a Scheduled executor and
 * object pooling. The route is returned as a {@link Future} object to the
 * calling class.
 * 
 * @author abhinav
 * 
 */
public class DjikstraRouterService {

	private ExecutorService executor;
	private RoadNetworkModel rnwModel;
	private static GenericObjectPool<RoutingDjikstra> pool;
	private static DjikstraRouterService router;
	private static final int NUM_OF_THREADS = 16;

	private DjikstraRouterService(RoadNetworkModel rnwModel) {
		this.rnwModel = rnwModel;
		executor = Executors.newFixedThreadPool(NUM_OF_THREADS);
	}

	/**
	 * Get and instance of the router. Think of this service as a routing
	 * provider.
	 * 
	 * @param rnwModel
	 *            the road network model.
	 * @return instance of the routing service.
	 */
	public static DjikstraRouterService getRouterInstance(
			RoadNetworkModel rnwModel) {
		if (router == null) {
			router = new DjikstraRouterService(rnwModel);
			pool = new GenericObjectPool<RoutingDjikstra>(
					new RoutingPoolFactory(rnwModel));
		}

		return router;

	}

	/**
	 * Return a {@link Future} object representing an array list of roads
	 * between the begin and end roads.
	 * 
	 * @param beginRoadId
	 *            the road Id of the origin road.
	 * @param endRoadId
	 *            the road Id of the destination road.
	 * @return
	 */
	public Future<ArrayList<Road>> getRoute(Integer beginRoadId,
			Integer endRoadId) {
		Future<ArrayList<Road>> future = null;
		try {
			GetRoute getRoute = new GetRoute(beginRoadId, endRoadId);
			future = executor.submit(getRoute);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return future;
	}

	private class GetRoute implements Callable<ArrayList<Road>> {

		private Integer beginRoadId;
		private Integer endRoadId;

		GetRoute(Integer beginRoadId, Integer endRoadId) {
			this.beginRoadId = beginRoadId;
			this.endRoadId = endRoadId;
		}

		@Override
		public ArrayList<Road> call() throws Exception {
			RoutingDjikstra dij = pool.borrowObject();

			Road beginRoad = rnwModel.getAllRoadsMap().get(beginRoadId);
			Road endRoad = rnwModel.getAllRoadsMap().get(endRoadId);
			dij.djikstra(beginRoad.getEndNode(), endRoad.getBeginNode());
			ArrayList<Road> route = dij.getRoute();
			pool.returnObject(dij);
			return route;
		}

	}

	/**
	 * @return the executor
	 */
	public ExecutorService getExecutor() {
		return executor;
	}

}
