package ctm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	private Collection<Road> roads;
	private List<Road> ramps;

	/**
	 * Create the Cell transmission model for the road network model from the
	 * {@link RoadNetworkModel}
	 * 
	 * @param ramps
	 * 
	 * @param model
	 */
	public CellNetwork(Collection<Road> roads, List<Road> ramps) {

		cellMap = new LinkedHashMap<String, Cell>();

		// Create cells and connectors to be used in the CTM model.
		this.roads = roads;
		this.ramps = ramps;
		createCells();
		generatePredecessorsAndSuccessors();

	}

	/**
	 * Add predecessors and successors to the cells created.
	 */
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

					Cell otherMergingCell = null;
					boolean findOther = false;
					for (Road inRoad : ins) {
						String inCellId = inRoad.getRoadId() + "_"
								+ (inRoad.getSegmentsLength().length - 1);
						Cell inCell = cellMap.get(inCellId);
						inCell.addSuccessor(cell);
						cell.addPredecessor(inCell);
						if (ins.size() == 2 && (inCell instanceof MergingCell && !findOther)) {
							otherMergingCell = inCell;
							findOther = true;
						}

						if (findOther && inCell instanceof MergingCell) {
							((MergingCell) otherMergingCell)
									.setOthermergingCell((MergingCell) inCell);
							((MergingCell) inCell)
									.setOthermergingCell((MergingCell) otherMergingCell);
						}

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
			int roadId = road.getRoadId();

			RoadNode beginNode = road.getBeginNode();
			RoadNode endNode = road.getEndNode();

			for (int i = 0; i < road.getRoadNodes().size() - 1; i++) {
				double cellLength = road.getSegmentsLength()[i];
				Cell cell = null;
				String cellId = roadId + "_" + i;

				// Note the difference between a merging and diverging cells.
				if (i == 0) {
					List<Road> ins = new ArrayList<>();
					for (Road inRoad : beginNode.getInRoads()) {
						if (roads.contains(inRoad))
							ins.add(inRoad);
					}

					cell = new OrdinaryCell(cellId, cellLength);
					if (ins.size() == 0) {
						Cell sourceCell = new SourceCell(roadId + "_source", 0);
						sourceCell.addSuccessor(cell);
						cell.addPredecessor(sourceCell);
						cellMap.put(roadId + "_source", sourceCell);
						++sourceCellCount;
					}

				} else if (i == road.getRoadNodes().size() - 2) {
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
						cell = new DivergingCell(cellId, cellLength);
						++divergingCellCount;
					} else if (outs.size() == 0) {
						cell = new OrdinaryCell(cellId, cellLength);
						Cell sinkCell = new SinkCell(roadId + "_sink", 0);
						sinkCell.setRoad(road);
						cell.addSuccessor(sinkCell);
						sinkCell.addPredecessor(cell);
						cellMap.put(roadId + "_sink", sinkCell);

						++sinkCellCount;
					} else if (ins.size() == 2 && outs.size() == 1) {
						if (road.getKind().equalsIgnoreCase("ramps")
								|| road.getKind().equalsIgnoreCase("Interchange")) {
							this.ramps.add(road);
						}
						cell = new MergingCell(cellId, cellLength);
						++mergingCellCount;

					} else {
						cell = new OrdinaryCell(cellId, cellLength);
					}

				} else {
					cell = new OrdinaryCell(cellId, cellLength);

				}

				cellMap.put(cellId, cell);

			}
		}

		// System.out.println("Number of source cells:" + sourceCellCount);
		// System.out.println("Number of sink cells:" + sinkCellCount);
		// System.out.println("Number of merging cells:" + mergingCellCount);
		// System.out.println("Number of diverging cells:" +
		// divergingCellCount);

	}

	/**
	 * @return the cellMap
	 */
	public Map<String, Cell> getCellMap() {
		return cellMap;
	}

}
