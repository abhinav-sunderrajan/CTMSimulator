package strategy;

import ctm.Cell;
import ctm.CellNetwork;
import simulator.SimulationConstants;

public class AlineaLocal extends RampMeterQueueThreshhold {

	int cycle = 0;
	private static final int CYLE_TIME = 60;
	private static final int G_MAX = 48;
	private static final int G_MIN = 12;
	private long cycleTime;
	private int greenTime;
	private int redTime;
	private double rampFlow;
	private double rSat;

	public AlineaLocal(CellNetwork cellNetwork) {
		super(cellNetwork);
		allow = false;
		cycleTime = 0;
		rampFlow = -1.0;
	}

	@Override
	public void controlAction(long simulationTime) {

		if (cycleTime == 0) {
			rampFlow = meterCell.getOutflow();
			rSat = meterCell.getCellCapacity();
		}

		if (cycleTime <= simulationTime) {
			Cell downCell = meterCell.getSuccessors().get(0);
			rampFlow = rampFlow + 70.0 * (downCell.getCriticalDensity() - downCell.getDensity());
			greenTime = (int) ((rampFlow / rSat) * CYLE_TIME);
			greenTime = greenTime > G_MAX ? G_MAX : greenTime;
			greenTime = greenTime < G_MIN ? G_MIN : greenTime;
			redTime = CYLE_TIME - greenTime;
			cycleTime = cycleTime + CYLE_TIME;
		}

		if (phaseTime <= simulationTime) {
			if (allow) {
				allow = false;
				phaseTime = simulationTime + redTime;

			} else {
				allow = true;
				phaseTime = simulationTime + greenTime;
			}

		}

		if (!allow) {
			meterCell.setOutflow(0);
		} else {
			meterCell.updateOutFlow();
		}

	}

	/**
	 * Weighted delay for on-ramp when signal is red.
	 * 
	 * @return
	 */
	public double getDelay() {
		double mult = 1;
		double delay = 0.0;
		if (!allow && cycle > 0) {
			// 1 complete phase cycle of 12 seconds of RED will result in mult
			// being increased by 0.33 until a max of 7.0.
			// mult = (1.0 + cycle / 9.0) > 7.0 ? 7.0 : (1.0 + cycle / 9.0);
		}
		double ffOutflow = (meterCell.getNumOfVehicles() * meterCell.getFreeFlowSpeed()
				* SimulationConstants.TIME_STEP) / meterCell.getLength();
		ffOutflow = Math.round(ffOutflow);
		if ((ffOutflow - meterCell.getOutflow()) > 0) {
			delay = mult * (ffOutflow - meterCell.getOutflow());
		}
		return delay;

	}

}
