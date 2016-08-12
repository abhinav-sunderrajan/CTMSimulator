package strategy;

import simulator.SimulationConstants;
import ctm.CellNetwork;

public class SimpleRampMeter extends RampMeterQueueThreshhold {

	public SimpleRampMeter(CellNetwork cellNetwork) {
		super(cellNetwork);
	}

	@Override
	public void controlAction(long simulationTime) {

		if (!allow) {
			meterCell.setOutflow(0);
			totalRedTime += SimulationConstants.TIME_STEP;
			redCycleTime += SimulationConstants.TIME_STEP;
		} else {
			meterCell.updateOutFlow();
			totalGreenTime += SimulationConstants.TIME_STEP;
		}

	}
}
