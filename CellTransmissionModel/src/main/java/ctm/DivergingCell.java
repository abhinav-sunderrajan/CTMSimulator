package ctm;

import main.SimulatorCore;

/**
 * Update method for divergent cells.
 * 
 * @author abhinav
 * 
 */
public class DivergingCell extends Cell {

	public DivergingCell(String cellId, double length) {
		super(cellId, length);
	}

	@Override
	public void updateOutFlow() {
		this.outflow = 0;
		for (Cell successor : successors) {
			double turnRatio = SimulatorCore.turnRatios.get(successor.getRoad().getRoadId());
			outflow += Math.min(successor.receivePotential, turnRatio * this.sendingPotential);
		}

	}

}
