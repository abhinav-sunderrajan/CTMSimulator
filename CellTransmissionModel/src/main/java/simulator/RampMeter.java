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
	private boolean allow;
	private CellNetwork cellNetwork;
	private long phaseTime;
	private int redCycleTime;
	private int totalRedTime;
	private int totalGreenTime;
	private static final int RED_MAX = 120;
	private static final int PHASE_MIN = 10;

	/**
	 * The ramp to be controlled.
	 * 
	 * @param ramp
	 *            Road.
	 * @param determineRampFlows
	 */
	public RampMeter(Road ramp, CellNetwork cellNetwork) {
		this.ramp = ramp;
		allow = true;
		queuePercentage = 0.0;
		this.cellNetwork = cellNetwork;
		this.meterCellNum = ramp.getRoadNodes().size() - 2;
		meterCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + meterCellNum);
		nMaxOnRamp = getNMaxOnRamp();
	}

	private double getNMaxOnRamp() {
		nMaxOnRamp = 0;
		for (int i = 0; i < meterCellNum; i++) {
			Cell rampCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + i);
			nMaxOnRamp += rampCell.getnMax();
		}

		return nMaxOnRamp;

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

		if ((n / getNMaxOnRamp()) > queuePercentage) {
			peakDensity = true;
		}
		return peakDensity;
	}

	/**
	 * Update the out-flow for the meter cell.
	 * 
	 * @param simulationTime
	 */
	public void regulateOutFlow(long simulationTime) {

		if (phaseTime <= simulationTime) {
			if (peakDensityReached()) {
				allow = true;
				redCycleTime = 0;

			} else {
				if (redCycleTime >= RED_MAX) {
					allow = true;
					redCycleTime = 0;
					phaseTime = simulationTime + PHASE_MIN;
				} else {
					allow = false;
				}
			}
			phaseTime = simulationTime + PHASE_MIN;

		}

		if (!allow) {
			meterCell.setOutflow(0);
			totalRedTime += SimulationConstants.TIME_STEP;
			redCycleTime += SimulationConstants.TIME_STEP;
		} else {
			meterCell.updateOutFlow();
			totalGreenTime += SimulationConstants.TIME_STEP;
		}

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
