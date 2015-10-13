package ctm;

/**
 * Implementation of a Sink cell i.e. a cell which does not have any outgoing
 * connectors
 * 
 * @author abhinav
 * 
 */
public class SinkCell extends Cell {

	public SinkCell(String cellId, double length, double freeFlowSpeed, int numOfLanes) {
		super(cellId, length, freeFlowSpeed, numOfLanes);
		assert (successors.size() == 0);
		nMax = Integer.MAX_VALUE;
	}

	@Override
	public void updateOutFlow() {
		// No out flow for this sink cell just collects and keeps updating the
		// count it has received in total. Actually you can determine the
		// number of vehicles collected in all sink cells at the end of the
		// simulation.
		outflow = 0;
	}

}
