package ctm;

import main.SimulatorCore;
import simulator.SimulationConstants;

/**
 * Represents a cell where the number of incoming connectors is equal to zero.
 * 
 * @author abhinav
 * 
 */
public class SourceCell extends Cell {

	// The mean inter-arrival time in seconds.
	private double meanIat;

	/**
	 * 
	 * @param cellId
	 * @param length
	 * @param freeFlowSpeed
	 * @param jamDensity
	 * @param w
	 */
	public SourceCell(String cellId, double length, double freeFlowSpeed, int numOfLanes) {
		super(cellId, length, freeFlowSpeed, numOfLanes);
		nMax = Integer.MAX_VALUE;
		nt = Integer.MAX_VALUE;
		meanIat = SimulatorCore.interArrivalTimes.get(road.getRoadId());

	}

	@Override
	public void updateOutFlow() {

		// The number of vehicles that enters first link is either the mean or
		// the space left in the first link.
		Cell successor = successors.get(0);
		this.outflow = Math.min(poissonRandomNumber(SimulationConstants.TIME_STEP / meanIat),
				successor.getReceivePotential());

		// this.outflow = Math.min(SimulationConstants.TIME_STEP / meanIat,
		// successor.getReceivePotential());

		// this.outflow = successor.getReceivePotential();

	}

	@Override
	/**
	 * The number of vehicles in the source cell is always infinity.
	 */
	public void updateNumberOfVehiclesInCell() {
		nt = Integer.MAX_VALUE;
	}

	/**
	 * Return the mean inter-arrival time associated with this source cell.
	 * 
	 * @return the meanIat
	 */
	public double getMeanIAT() {
		return meanIat;
	}

	/**
	 * Set the mean inter-arrival time associated with the souce cell.
	 * 
	 * @param dt
	 *            the dt to set
	 */
	public void setMeanIAT(double dt) {
		this.meanIat = dt;
	}

	/**
	 * Generate a random poisson with mean equal to the mean number of vehicles
	 * to be inserted to the first link every time interval.
	 * 
	 * @param lambda
	 * @return
	 */
	private int poissonRandomNumber(double lambda) {
		double L = Math.exp(-lambda);
		int k = 0;
		double p = 1;
		do {
			k = k + 1;
			double u = SimulatorCore.random.nextDouble();
			p = p * u;
		} while (p > L);
		return k - 1;
	}

}
