package ctm;

import simulator.SimulationConstants;

/**
 * An ordinary cell has only one outgoing cell. Can have multiple incoming
 * cells.
 * 
 * 
 * @author abhinav
 * 
 */
public class OrdinaryCell extends Cell {

	public OrdinaryCell(String cellId, double length) {
		super(cellId, length);
	}

	@Override
	public void updateOutFlow() {
		Cell Ek = this.successors.get(0);
		if (Ek instanceof SinkCell) {
			this.outflow = (int) Math.round(Math.min(nt, density * meanSpeed * numOfLanes
					* SimulationConstants.TIME_STEP));
		} else {
			this.outflow = (int) Math.round(Math.min(Ek.receivePotential, sendingPotential));
			// if (cellId.equals("30633_0")) {
			// System.out.println("sending potential:" + this.sendingPotential +
			// " nt:" + this.nt
			// + " mean speed:" + this.meanSpeed + " length:" + length);
			// }
		}

	}
}
