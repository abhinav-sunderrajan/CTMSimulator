package main;

import strategy.ControlStrategy;
import strategy.RampMeter;
import strategy.VariableSpeedLimit;
import ctm.CellNetwork;

public enum Control {

	VSL, RAMP_METERING;

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
		default:
			strategy = new VariableSpeedLimit(cellNetwork);
			break;

		}
		return strategy;
	}
}
