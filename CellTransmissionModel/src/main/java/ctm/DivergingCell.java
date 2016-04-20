package ctm;

import java.util.HashMap;
import java.util.Map;

/**
 * Update method for divergent cells.
 * 
 * @author abhinav
 * 
 */
public class DivergingCell extends Cell {

	private Map<String, Double> outFlows;

	public DivergingCell(String cellId, double length) {
		super(cellId, length);

	}

	@Override
	public void updateOutFlow() {
		this.outflow = 0;

		if (outFlows == null) {
			outFlows = new HashMap<String, Double>();
			for (Cell successor : successors)
				outFlows.put(successor.getCellId(), 0.0);
		}

		for (Cell successor : successors) {
			double turnRatio = core.getTurnRatios().get(successor.getRoad().getRoadId());
			double successorOutflow = Math.min(successor.receivePotential, turnRatio
					* this.sendingPotential);
			outFlows.put(successor.getCellId(), successorOutflow);
			outflow += successorOutflow;
		}

	}

	/**
	 * @return the outFlows
	 */
	public Map<String, Double> getOutFlowsMap() {
		return outFlows;
	}

}
