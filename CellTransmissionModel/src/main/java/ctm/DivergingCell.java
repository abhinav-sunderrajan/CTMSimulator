package ctm;

import simulator.CTMSimulator;

/**
 * Update method for divergent cells.
 * 
 * @author abhinav
 * 
 */
public class DivergingCell extends Cell {

	/**
	 * 
	 * @param cellId
	 * @param length
	 * @param freeFlowSpeed
	 * @param jamDensity
	 * @param w
	 */
	public DivergingCell(String cellId, double length, double freeFlowSpeed, int numOfLanes) {
		super(cellId, length, freeFlowSpeed, numOfLanes);
	}

	@Override
	public void updateOutFlow() {

		// The sending potential of this cell.
		double min = getSendingPotential();
		for (Cell successor : successors) {
			double turnRatio = CTMSimulator.turnRatios.get(successor.getRoadId());
			// The receiving potential of the successor cells.
			double recvPotential = successor.getReceivePotential() / turnRatio;
			if (recvPotential < min)
				min = recvPotential;
		}

		this.outflow = min;

	}

}
