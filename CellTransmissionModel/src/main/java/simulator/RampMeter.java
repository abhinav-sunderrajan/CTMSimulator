package simulator;

import java.util.Map;

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
	private Map<String, Cell> cellMap;
	private int meterCellNum;
	private Cell meterCell;

	/**
	 * The ramp to be controlled.
	 * 
	 * @param ramp
	 */
	public RampMeter(Road ramp, Map<String, Cell> cellMap) {
		this.ramp = ramp;
		this.cellMap = cellMap;
		this.meterCellNum = ramp.getRoadNodes().size() - 2;
		meterCell = cellMap.get(ramp.getRoadId() + "_" + meterCellNum);
		for (int i = 0; i < meterCellNum; i++)
			nMaxOnRamp += cellMap.get(ramp.getRoadId() + "_" + i).getnMax();
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
		boolean peakDensity = false;
		double qMaxMeterCell = meterCell.getQmax();
		double n = 0;
		for (int i = 0; i < meterCellNum; i++) {
			Cell rampCell = cellMap.get(ramp.getRoadId() + "_" + i);
			n += rampCell.getNumOfVehiclesInCell();
			if (n / nMaxOnRamp > queuePercentage) {
				peakDensity = true;
				break;
			}
		}
		if (!peakDensity) {
			System.out.println("ramp meter green..");
			meterCell.setQmax(qMaxMeterCell);
		} else {
			meterCell.setQmax(0);
			System.out.println("Ramp meter red");
		}

	}

}
