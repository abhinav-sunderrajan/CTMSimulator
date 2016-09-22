package strategy;

import simulator.SimulationConstants;
import ctm.CellNetwork;

public class SimpleRampMeter extends RampMeterQueueThreshhold {

	int cycle = 0;

	public SimpleRampMeter(CellNetwork cellNetwork) {
		super(cellNetwork);
	}

	@Override
	public void controlAction(long simulationTime) {

		if (!allow) {
			meterCell.setOutflow(0);
			cycle++;
		} else {
			meterCell.updateOutFlow();
			cycle = 0;
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
		double ff = (meterCell.getNumOfVehicles() * meterCell.getFreeFlowSpeed() * SimulationConstants.TIME_STEP)
				/ meterCell.getLength();
		ff = Math.round(ff);
		if ((ff - meterCell.getOutflow()) > 0) {
			delay = mult * (ff - meterCell.getOutflow());
		}
		return delay;

	}
}
