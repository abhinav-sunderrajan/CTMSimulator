package simulator;

import rnwmodel.Road;
import ctm.Cell;
import ctm.CellNetwork;

/**
 * A ramp meter controlled by the queue length on the on-ramp.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class RampMeter {

	private Road ramp;
	private double queuePercentage;
	private double nMaxOnRamp;
	private int meterCellNum;
	private Cell meterCell;
	private boolean red;
	private boolean green;
	private CellNetwork cellNetwork;
	private int redCycleTime;
	private int greenCycleTime;
	// These values are determined from a web site which seems official.
	private static final int GREEN_MIN = 10;
	private static final int RED_MIN = 6;
	private int totalRedTime;
	private int totalGreenTime;

	/**
	 * The ramp to be controlled.
	 * 
	 * @param ramp
	 *            Road.
	 * @param determineRampFlows
	 */
	public RampMeter(Road ramp, CellNetwork cellNetwork) {
		this.ramp = ramp;
		red = false;
		green = true;
		redCycleTime = 0;
		queuePercentage = 0.5;
		this.cellNetwork = cellNetwork;
		this.meterCellNum = ramp.getRoadNodes().size() - 2;
		meterCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + meterCellNum);
		for (int i = 0; i < meterCellNum; i++) {
			Cell rampCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + i);
			nMaxOnRamp += (rampCell.getLength() * rampCell.getNumOfLanes())
					/ SimulationConstants.VEHICLE_LENGTH;
		}

		assert (green ^ red);
	}

	private boolean peakDensityReached() {
		// The peak density parameter overrides the controller in case the size
		// of the queue breaches the queuePercentage parameter.
		boolean peakDensity = false;
		double n = 0;
		for (int i = 0; i <= meterCellNum; i++) {
			Cell rampCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + i);
			n += rampCell.getNumOfVehicles();
		}

		if ((n / nMaxOnRamp) > queuePercentage) {
			peakDensity = true;
		}
		return peakDensity;
	}

	/**
	 * Update the out-flow for the meter cell.
	 */
	public void regulateOutFlow() {
		if (peakDensityReached()) {
			if (redCycleTime >= RED_MIN) {
				red = false;
				green = true;
				redCycleTime = 0;
				greenCycleTime += 2;
			} else {
				redCycleTime += 2;
			}

		} else {

			if (greenCycleTime >= GREEN_MIN) {
				green = false;
				red = true;
				redCycleTime += 2;
				greenCycleTime = 0;
			} else {
				greenCycleTime += 2;
			}

		}

		if (red && !green) {
			meterCell.setOutflow(0);
			totalRedTime += 2;
		} else {
			meterCell.updateOutFlow();
			totalGreenTime += 2;
		}

	}

	/**
	 * @return the red
	 */
	public boolean isRed() {
		return red;
	}

	/**
	 * @param red
	 *            the red to set
	 */
	public void setRed(boolean red) {
		this.red = red;
	}

	/**
	 * @return the green
	 */
	public boolean isGreen() {
		return green;
	}

	/**
	 * @param green
	 *            the green to set
	 */
	public void setGreen(boolean green) {
		this.green = green;
	}

	/**
	 * @return the meterCell
	 */
	public Cell getMeterCell() {
		return meterCell;
	}

	/**
	 * @return the queuePercentage
	 */
	public double getQueuePercentage() {
		return this.queuePercentage;
	}

	/**
	 * @param queuePercentage
	 *            the queuePercentage to set
	 */
	public void setQueuePercentage(double queuePercentage) {
		this.queuePercentage = queuePercentage;
	}

	/**
	 * @return the netOutFlow
	 */
	public int getTotalRedTime() {
		return totalRedTime;
	}

	/**
	 * @return the greenTime
	 */
	public int getTotalGreenTime() {
		return totalGreenTime;
	}

}
