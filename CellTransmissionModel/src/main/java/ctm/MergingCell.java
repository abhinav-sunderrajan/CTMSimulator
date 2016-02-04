package ctm;

import java.util.Arrays;

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
		double arr[] = { mergePriority * Ek.receivePotential, sendingPotential,
				Ek.receivePotential - othermergingCell.sendingPotential };
		Arrays.sort(arr);
		this.outflow = (int) Math.round(arr[0]);

	}
}
