package ctm;

import main.SimulatorCore;

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
	 */
	public MergingCell(String cellId, double length) {
		super(cellId, length);
		mergePriority = SimulatorCore.mergePriorities.get(road.getRoadId());
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
		double recvPotential = this.mergePriority * Ek.receivePotential;
		this.outflow = Math.min(recvPotential, sendingPotential);
	}
}
