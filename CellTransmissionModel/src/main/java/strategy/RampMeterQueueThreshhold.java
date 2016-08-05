package strategy;

import rnwmodel.Road;
import simulator.SimulationConstants;
import ctm.Cell;
import ctm.CellNetwork;

/**
 * A ramp meter controlled by the queue length on the on-ramp.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class RampMeterQueueThreshhold extends ControlStrategy {

	protected Road ramp;
	protected double queuePercentage;
	protected double nMaxOnRamp;
	protected int meterCellNum;
	protected Cell meterCell;
	protected boolean allow;
	protected long phaseTime;
	protected int redCycleTime;
	protected int totalRedTime;
	protected int totalGreenTime;
	protected static final int RED_MAX = 120;
	public static final int PHASE_MIN = 12;

	/**
	 * The ramp to be controlled.
	 * 
	 * @param ramp
	 *            Road.
	 * @param determineRampFlows
	 */
	public RampMeterQueueThreshhold(CellNetwork cellNetwork) {
		super(cellNetwork);
		allow = true;
		queuePercentage = 0.0;
	}

	protected double getNMaxOnRamp() {
		nMaxOnRamp = 0;
		for (int i = 0; i < meterCellNum; i++) {
			Cell rampCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + i);
			nMaxOnRamp += rampCell.getnMax();
		}

		return nMaxOnRamp;
	}

	/**
	 * @return the ramp
	 */
	public Road getRamp() {
		return ramp;
	}

	/**
	 * @param ramp
	 *            the ramp to set
	 */
	public void setRamp(Road ramp) {
		this.ramp = ramp;
		this.meterCellNum = ramp.getRoadNodes().size() - 2;
		meterCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + meterCellNum);
		nMaxOnRamp = getNMaxOnRamp();
	}

	protected boolean peakDensityReached() {
		// The peak density parameter overrides the controller in case the size
		// of the queue breaches the queuePercentage parameter.
		boolean peakDensity = false;
		double nTotal = 0;
		for (int i = 0; i <= meterCellNum; i++) {
			Cell rampCell = cellNetwork.getCellMap().get(ramp.getRoadId() + "_" + i);
			nTotal += rampCell.getNumOfVehicles();
		}

		if ((nTotal / getNMaxOnRamp()) >= queuePercentage) {
			peakDensity = true;
		}
		return peakDensity;
	}

	@Override
	public void controlAction(long simulationTime) {

		if (queuePercentage > 0.0 && phaseTime <= simulationTime) {
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

	/**
	 * @return the allow
	 */
	public boolean isAllow() {
		return allow;
	}

	/**
	 * @param allow
	 *            the allow to set
	 */
	public void setAllow(boolean allow) {
		this.allow = allow;
	}

}
