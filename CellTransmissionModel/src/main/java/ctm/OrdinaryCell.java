package ctm;

import main.SimulatorCore;

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
			double temp = 0.6 + 0.3 * SimulatorCore.SIMCORE_RANDOM.nextDouble();
			this.outflow = Math.round(temp * sendingPotential);
		} else {
			this.outflow = Math.min(Ek.receivePotential, sendingPotential);
		}

	}
}
