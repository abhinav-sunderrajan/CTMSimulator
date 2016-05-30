package strategy;

import java.util.Map;

import ctm.Cell;
import ctm.CellNetwork;

public class VariableSpeedLimit extends ControlStrategy {

	private static final int PIE[] = { 30632, 30633, 30634, 30635, 30636, 30637, 30638, 30639,
			30640, 30641, 37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649,
			30650, 30651, 30580, 30581 };
	private long evalTime;
	private Map<Integer, Double> speedLimits;

	public VariableSpeedLimit(CellNetwork cellNetwork) {
		super(cellNetwork);
	}

	/**
	 * Set the free flow speed at these roads
	 * 
	 * @param speedLimits
	 */
	public void setSpeedLimits(Map<Integer, Double> speedLimits) {
		this.speedLimits = speedLimits;
		if (speedLimits.size() != PIE.length)
			throw new IllegalArgumentException("");

	}

	@Override
	public void controlAction(long simulationTime) {
		for (int roadId : PIE) {
			int i = 0;
			while (true) {
				Cell cell = cellNetwork.getCellMap().get(roadId + "_" + i);
				if (cell == null)
					break;
				cell.setFreeFlowSpeed(speedLimits.get(roadId));
				i++;
			}

		}
	}
}
