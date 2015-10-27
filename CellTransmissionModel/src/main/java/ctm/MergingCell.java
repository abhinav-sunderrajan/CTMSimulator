package ctm;

import simulator.CTMSimulator;

/**
 * Update method for merging cells.
 * 
 * @author abhinav
 * 
 */
public class MergingCell extends Cell {

	private double mergePriority;
	private MergingCell othermergingCell;

	/**
	 * Initializes a merging cell.
	 * 
	 * @param cellId
	 * @param length
	 * @param freeFlowSpeed
	 * @param numOfLanes
	 */
	public MergingCell(String cellId, double length, double freeFlowSpeed, int numOfLanes) {
		super(cellId, length, freeFlowSpeed, numOfLanes);
		mergePriority = CTMSimulator.mergePriorities.get(road.getRoadId());
	}

	/**
	 * @return the othermergingCells
	 */
	public MergingCell getOthermergingCell() {
		return othermergingCell;
	}

	/**
	 * @param othermergingCells
	 *            the othermergingCells to set
	 */
	public void setOthermergingCell(MergingCell othermergingCell) {
		this.othermergingCell = othermergingCell;
	}

	@Override
	public void updateOutFlow() {
		Cell Ek = this.successors.get(0);
		// The receiving potential of the next cell.
		double recvPotential = mergePriority * Ek.getReceivePotential();
		// Receive potential next - Sending other
		// double other = Ek.getReceivePotential() -
		// othermergingCell.getSendingPotential();

		// Mid will never work this needs to be verified

		// double mid = Math.max(Math.min(sendingPotential, recvPotential),
		// Math.min(Math.max(sendingPotential, recvPotential), other));

		this.outflow = Math.min(recvPotential, getSendingPotential());

	}
}
