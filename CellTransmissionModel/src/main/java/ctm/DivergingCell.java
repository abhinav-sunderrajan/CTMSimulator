package ctm;

import main.SimulatorCore;

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
	public DivergingCell(String cellId, double length) {
		super(cellId, length);
	}

	@Override
	public void updateOutFlow() {

		// The sending potential of this cell.
		double min = getSendingPotential();
		for (Cell successor : successors) {
			double turnRatio = SimulatorCore.turnRatios.get(successor.getRoad().getRoadId());
			// The receiving potential of the successor cells.
			double recvPotential = successor.getReceivePotential() / turnRatio;
			if (recvPotential < min)
				min = recvPotential;
		}

		this.outflow = min;

	}

}
