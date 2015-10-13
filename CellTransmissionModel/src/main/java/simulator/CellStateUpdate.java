package simulator;

import java.util.HashSet;
import java.util.Set;

import ctm.Cell;

/**
 * Updates the cells constituting a section of the road network.
 * 
 * @author abhinav
 * 
 */
public class CellStateUpdate implements Runnable {
	Set<Cell> cells;
	int id;

	/**
	 * 
	 * @param cells
	 */
	public CellStateUpdate(int id) {
		this.cells = new HashSet<Cell>();
		this.id = id;
	}

	@Override
	public void run() {
		for (Cell cell : cells) {
			cell.updateOutFlow();
		}
	}

}
