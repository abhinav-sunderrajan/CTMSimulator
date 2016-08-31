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

		double k = sendingPotential;
		int p[] = new int[2];
		double tr1 = core.getTurnRatios().get(successors.get(0).getRoad().getRoadId());
		while (k > 0) {
			if (Math.random() < tr1)
				p[0]++;
			else
				p[1]++;
			k--;

		}
		int i = 0;

		for (Cell successor : successors) {
			double successorOutflow = Math.min(successor.receivePotential, p[i]);
			outFlows.put(successor.getCellId(), successorOutflow);
			outflow += successorOutflow;
			i++;
		}

	}

	/**
	 * @return the outFlows
	 */
	public Map<String, Double> getOutFlowsMap() {
		return outFlows;
	}

}
