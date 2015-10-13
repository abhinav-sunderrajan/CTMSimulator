package ctm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;

/**
 * Class representing the cell network. Right now the implementation is only for
 * freeway segments. Do not try urban street network.
 * 
 * @see <a
 *      href="http://en.wikipedia.org/wiki/Cell_Transmission_Model">http://en.wikipedia.org/wiki/Cell_Transmission_Model</a>
 * @author abhinav
 * 
 */
public class CellNetwork {

	private Map<String, Cell> cellMap;
	private List<Road> roads;

	/**
	 * Create the Cell transmission model for the road network model from the
	 * {@link RoadNetworkModel}
	 * 
	 * @param model
	 */
	public CellNetwork(List<Road> roads) {

		cellMap = new ConcurrentHashMap<String, Cell>();
		// Create cells and connectors to be used in the CTM model.
		this.roads = roads;
		createCells();
		generatePredecessorsAndSuccessors();
	}

	private void generatePredecessorsAndSuccessors() {
		for (Road road : roads) {
			RoadNode beginNode = road.getBeginNode();
			RoadNode endNode = road.getEndNode();

			for (int i = 0; i < road.getRoadNodes().size() - 1; i++) {
				int roadId = road.getRoadId();
				String cellId = roadId + "_" + i;

				Cell cell = cellMap.get(cellId);
				if (i == 0) {
					List<Road> ins = new ArrayList<>();
					for (Road inRoad : beginNode.getInRoads()) {
						if (roads.contains(inRoad))
							ins.add(inRoad);
					}

					for (Road inRoad : ins) {
						String inCellId = inRoad.getRoadId() + "_"
								+ (inRoad.getSegmentsLength().length - 1);
						Cell inCell = cellMap.get(inCellId);
						inCell.addSuccessor(cell);
						cell.addPredecessor(inCell);
					}

					cell.addSuccessor(cellMap.get(roadId + "_1"));

				} else if (i == road.getRoadNodes().size() - 2) {
					cell.addPredecessor(cellMap.get(roadId + "_" + (i - 1)));

					List<Road> outs = new ArrayList<>();
					for (Road outRoad : endNode.getOutRoads()) {
						if (roads.contains(outRoad))
							outs.add(outRoad);
					}

					for (Road outRoad : outs) {
						String outCellId = outRoad.getRoadId() + "_0";
						Cell outCell = cellMap.get(outCellId);
						cell.addSuccessor(outCell);
						outCell.addPredecessor(cell);
					}
				} else {
					Cell prev = cellMap.get(roadId + "_" + (i - 1));
					Cell next = cellMap.get(roadId + "_" + (i + 1));
					cell.addPredecessor(prev);
					cell.addSuccessor(next);

				}

			}

		}

	}

	/**
	 * Create the cells of the CTM model. You need to have one
	 */
	private void createCells() {
		int sourceCellCount = 0;
		int sinkCellCount = 0;
		int mergingCellCount = 0;
		int divergingCellCount = 0;

		for (Road road : roads) {
			double freeFlowSpeed = road.getSpeedLimit()[1] * 5.0 / 18;
			int roadId = road.getRoadId();
			int numOfLanes = road.getLaneCount();

			RoadNode beginNode = road.getBeginNode();
			RoadNode endNode = road.getEndNode();

			for (int i = 0; i < road.getRoadNodes().size() - 1; i++) {
				double cellLength = road.getSegmentsLength()[i];
				Cell cell = null;
				String cellId = roadId + "_" + i;

				if (i == 0) {

					List<Road> ins = new ArrayList<>();
					for (Road inRoad : beginNode.getInRoads()) {
						if (roads.contains(inRoad))
							ins.add(inRoad);
					}

					cell = new OrdinaryCell(cellId, cellLength, freeFlowSpeed, numOfLanes);
					if (ins.size() == 0) {
						Cell sourceCell = new SourceCell(roadId + "_source", 0, freeFlowSpeed,
								numOfLanes);
						sourceCell.addSuccessor(cell);
						cell.addPredecessor(sourceCell);
						cellMap.put(roadId + "_source", sourceCell);
						++sourceCellCount;
					}

				}// Note the difference between a merging and diverging cells.
				else if (i == road.getRoadNodes().size() - 2) {
					List<Road> outs = new ArrayList<>();
					for (Road outRoad : endNode.getOutRoads()) {
						if (roads.contains(outRoad))
							outs.add(outRoad);
					}

					List<Road> ins = new ArrayList<>();
					for (Road inRoad : endNode.getInRoads()) {
						if (roads.contains(inRoad))
							ins.add(inRoad);
					}

					if (outs.size() > 1) {
						cell = new DivergingCell(cellId, cellLength, freeFlowSpeed, numOfLanes);
						++divergingCellCount;
					} else if (outs.size() == 0) {
						cell = new OrdinaryCell(cellId, cellLength, freeFlowSpeed, numOfLanes);
						Cell sinkCell = new SinkCell(roadId + "_sink", 0, freeFlowSpeed, numOfLanes);
						sinkCell.setRoadId(road.getRoadId());
						cell.addSuccessor(sinkCell);
						sinkCell.addPredecessor(cell);
						cellMap.put(roadId + "_sink", sinkCell);

						++sinkCellCount;
					} else if (ins.size() == 2 && outs.size() == 1) {
						cell = new MergingCell(cellId, cellLength, freeFlowSpeed, numOfLanes);
						++mergingCellCount;

					} else {
						cell = new OrdinaryCell(cellId, cellLength, freeFlowSpeed, numOfLanes);
					}

				} else {
					cell = new OrdinaryCell(cellId, cellLength, freeFlowSpeed, numOfLanes);

				}

				cellMap.put(cellId, cell);

			}
		}

		System.out.println("Number of source cells:" + sourceCellCount);
		System.out.println("Number of sink cells:" + sinkCellCount);
		System.out.println("Number of merging cells:" + mergingCellCount);
		System.out.println("Number of diverging cells:" + divergingCellCount);

	}

	/**
	 * @return the cellMap
	 */
	public Map<String, Cell> getCellMap() {
		return cellMap;
	}

}
