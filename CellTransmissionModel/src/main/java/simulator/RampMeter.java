package simulator;

import rnwmodel.Road;
import ctm.Cell;

/**
 * A ramp meter controlled by the queue length on the on-ramp.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class RampMeter implements Runnable {

	private Road ramp;
	private double queuePercentage;
	private double nMaxOnRamp;
	private int meterCellNum;
	private Cell meterCell;
	private boolean red;
	private boolean green;

	/**
	 * The ramp to be controlled.
	 * 
	 * @param ramp
	 *            Road.
	 */
	public RampMeter(Road ramp) {
		this.ramp = ramp;
		red = false;
		green = true;
		this.meterCellNum = ramp.getRoadNodes().size() - 2;
		meterCell = CTMSimulator.cellNetwork.getCellMap()
				.get(ramp.getRoadId() + "_" + meterCellNum);
		for (int i = 0; i < meterCellNum; i++)
			nMaxOnRamp += CTMSimulator.cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + i)
					.getnMax();
	}

	/**
	 * @return the queuePercentage
	 */
	public double getQueuePercentage() {
		return queuePercentage;
	}

	/**
	 * The percentage of queue that requires full occupancy before the ramp
	 * meter is turned green from red.
	 * 
	 * @param queuePercentage
	 *            the queuePercentage to set
	 */
	public void setQueuePercentage(double queuePercentage) {
		assert (queuePercentage < 1 && queuePercentage > 0);
		this.queuePercentage = queuePercentage;
	}

	@Override
	public void run() {

		// The peak density parameter overrides the controller in case the size
		// of the queue breaches the queuePercentage parameter.
		boolean peakDensity = false;
		double qMaxMeterCell = meterCell.getQmax();
		double n = 0;
		for (int i = 0; i < meterCellNum; i++) {
			Cell rampCell = CTMSimulator.cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + i);
			n += rampCell.getNumOfVehiclesInCell();
			if (n / nMaxOnRamp > queuePercentage) {
				peakDensity = true;
				break;
			}
		}
		if (green) {
			red = false;
			meterCell.setQmax(qMaxMeterCell);
		} else {
			if (peakDensity) {
				System.out
						.println("Overriding ramp metering. Peak density reached. Setting to green");
				meterCell.setQmax(qMaxMeterCell);
				green = true;
				red = false;
			} else {
				meterCell.setQmax(0);
				green = false;

			}
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

}
