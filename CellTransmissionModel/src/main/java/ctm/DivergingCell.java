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
		double min = sendingPotential;
		for (Cell successor : successors) {
			double turnRatio = SimulatorCore.turnRatios.get(successor.getRoad().getRoadId());
			double recvPotential = successor.receivePotential / turnRatio;
			if (recvPotential < min)
				min = recvPotential;
		}

		this.outflow = min;

	}

}
