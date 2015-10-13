package ctm;

/**
 * An ordinary cell has only one outgoing cell. Can have multiple incoming
 * cells.
 * 
 * 
 * @author abhinav
 * 
 */
public class OrdinaryCell extends Cell {

	public OrdinaryCell(String cellId, double length, double freeFlowSpeed, int numOfLanes) {
		super(cellId, length, freeFlowSpeed, numOfLanes);
	}

	@Override
	public void updateOutFlow() {
		Cell Ek = this.successors.get(0);

		if (!(Ek instanceof SinkCell))
			this.outflow = Math.min(getSendingPotential(), Ek.getReceivePotential()) + 0.5;
		else
			this.outflow = Math.min(nt, Qmax);

	}
}
