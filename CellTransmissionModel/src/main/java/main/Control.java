package main;

import strategy.ControlStrategy;
import strategy.RampMeter;
import strategy.VariableSpeedLimit;
import ctm.CellNetwork;

public enum Control {

	VSL, RAMP_METERING, NONE;

	/**
	 * 
	 * @param control
	 * @param cellNetwork
	 * @return
	 */
	public ControlStrategy getControlInstance(Control control, CellNetwork cellNetwork) {
		ControlStrategy strategy = null;
		switch (control) {
		case VSL:
			strategy = new RampMeter(cellNetwork);
			break;
		case RAMP_METERING:
			strategy = new VariableSpeedLimit(cellNetwork);
			break;
		default:
			strategy = null;
			break;

		}
		return strategy;
	}
}
