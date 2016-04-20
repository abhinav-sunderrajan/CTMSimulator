package ctm;


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
		mergePriority = core.getMergePriorities().get(road.getRoadId());
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

		if (Ek.receivePotential > (this.sendingPotential + othermergingCell.sendingPotential)) {
			double temp = (mergePriority * Ek.receivePotential)
					/ (mergePriority + othermergingCell.mergePriority);
			this.outflow = Math.min(temp, sendingPotential);
		} else {
			this.outflow = Math.min(mergePriority * Ek.receivePotential, sendingPotential);
		}

	}
}
