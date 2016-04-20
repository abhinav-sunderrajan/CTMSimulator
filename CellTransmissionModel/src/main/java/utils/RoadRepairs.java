package utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import rnwmodel.Road;
import rnwmodel.RoadNode;
import simulator.SimulationConstants;

import com.vividsolutions.jts.geom.Coordinate;

public class RoadRepairs {

	/**
	 * A bad fix to set right two segment roads which fail to conform the two
	 * the minimum cell size requirement of CTM.
	 * 
	 * @param road
	 * @param minLength
	 * @param pieChangi
	 */
	public static void repairTwoSegmentRoads(Road road, double minLength, Collection<Road> pieChangi) {
		double segementL1 = road.getSegmentsLength()[0];
		double segementL2 = road.getSegmentsLength()[1];

		if ((segementL1 + segementL2) < (2.0 * minLength)) {
			RoadNode beginNode = road.getBeginNode();
			RoadNode endNode = road.getEndNode();
			List<Road> ins = new ArrayList<>();
			for (Road inRoad : beginNode.getInRoads()) {
				if (pieChangi.contains(inRoad))
					ins.add(inRoad);
			}
			List<Road> outs = new ArrayList<>();
			for (Road outRoad : endNode.getOutRoads()) {
				if (pieChangi.contains(outRoad))
					outs.add(outRoad);
			}
			double distanceDifference = (2.0 * minLength) - (segementL1 + segementL2) + 1.0;

			if (ins.size() == 0) {
				double bearing = EarthFunctions.bearing(road.getRoadNodes().get(1).getPosition(),
						road.getRoadNodes().get(0).getPosition());
				Coordinate newCoord = EarthFunctions.getPointAtDistanceAndBearing(road
						.getRoadNodes().get(0).getPosition(), distanceDifference,
						Math.toRadians(bearing));
				road.getRoadNodes().get(0).setPosition(newCoord);
			}

			if (outs.size() == 0) {
				double bearing = EarthFunctions.bearing(road.getRoadNodes().get(1).getPosition(),
						road.getRoadNodes().get(2).getPosition());

				Coordinate newCoord = EarthFunctions.getPointAtDistanceAndBearing(road
						.getRoadNodes().get(2).getPosition(), distanceDifference,
						Math.toRadians(bearing));
				road.getRoadNodes().get(2).setPosition(newCoord);
			}

			Coordinate midPoint = EarthFunctions.midPoint(road.getRoadNodes().get(0).getPosition(),
					road.getRoadNodes().get(2).getPosition());
			road.getRoadNodes().get(1).setPosition(midPoint);
		}

		if (road.getSegmentsLength()[1] < minLength || road.getSegmentsLength()[0] < minLength) {
			Coordinate midPoint = EarthFunctions.midPoint(road.getRoadNodes().get(0).getPosition(),
					road.getRoadNodes().get(2).getPosition());
			road.getRoadNodes().get(1).setPosition(midPoint);
		}
	}

	/**
	 * Do not have long cells break them up. Just to see if this improves graphs
	 * generated in any way.
	 * 
	 * @param pieChangi
	 * @param nodeId
	 */
	public static void breakLongSegments(Collection<Road> pieChangi, int nodeId) {
		for (Road road : pieChangi) {
			double minLength = (road.getSpeedLimit()[1] * (5 / 18.0))
					* SimulationConstants.TIME_STEP;
			while (true) {
				boolean noLargeSegments = true;
				int numOfSegments = road.getSegmentsLength().length;
				for (int i = 0; i < numOfSegments; i++) {
					if (road.getSegmentsLength()[i] > (minLength * 2.5)) {
						double x = (road.getRoadNodes().get(i).getX() + road.getRoadNodes()
								.get(i + 1).getX()) / 2.0;
						double y = (road.getRoadNodes().get(i).getY() + road.getRoadNodes()
								.get(i + 1).getY()) / 2.0;
						road.getRoadNodes().add(i + 1, new RoadNode(nodeId, x, y));
						noLargeSegments = false;
						break;
					}
				}
				if (noLargeSegments)
					break;
			}
		}
	}

	/**
	 * A bad fix to set right two segment roads which fail to conform the two
	 * the minimum cell size requirement of CTM.
	 * 
	 * @param road
	 * @param minLength
	 * @param pieChangi
	 */
	public static void repairMultiSegmentRoads(Road road, double minLength,
			Collection<Road> pieChangi) {
		int numOfSegments = road.getSegmentsLength().length;
		while (true) {
			boolean noSmallSegments = true;
			numOfSegments = road.getSegmentsLength().length;
			if (numOfSegments < 3) {
				if (numOfSegments == 2)
					RoadRepairs.repairTwoSegmentRoads(road, minLength, pieChangi);
				break;
			}
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
