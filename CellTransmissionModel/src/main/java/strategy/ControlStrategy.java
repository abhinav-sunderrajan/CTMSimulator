package strategy;

import ctm.CellNetwork;

/**
 * Implements the control strategy to optimize traffic on the freeway.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public abstract class ControlStrategy {

	protected CellNetwork cellNetwork;

	/**
	 * Initialize the control strategy with the cell network.
	 * 
	 * @param cellNetwork
	 */
	public ControlStrategy(CellNetwork cellNetwork) {
		this.cellNetwork = cellNetwork;
	}

	/**
	 * Implement the control action at the given simulation time.
	 * 
	 * @param simulationTime
	 */
	public abstract void controlAction(long simulationTime);

}
