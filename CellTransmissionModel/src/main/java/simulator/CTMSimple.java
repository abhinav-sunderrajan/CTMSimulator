package simulator;

/**
 * A simple simulator of the CTM not perfectly working as of now. Need to
 * determine the error.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class CTMSimple {

	// All in SI units.
	private static final int T = 20;
	private static final int ROAD_LENGTH = 3000;
	private static final double V0 = 20;
	private static final int leff = 8;
	private static final int SIM_TIME = 3000;
	private static final int ACC_TIME = 200;
	private static final int ACC_DURATION = 600;
	private static final double PACE = 50.0;
	private static final int MAX_FLOW = 3600;
	private static final double TIME_GAP = 1.4;

	public static void main(String args[]) throws InterruptedException {

		double cellLength = V0 * T;
		double w = leff / TIME_GAP;
		double delta = w / V0;

		// One source Cell and
		// one Sink cell of infinite capacities
		int numOfCells = (int) (ROAD_LENGTH / cellLength) + 2;
		int maxVehiclesPerCell = (int) (cellLength / leff);

		// No of vehicles per second.
		int maxFlow = (MAX_FLOW * T) / 3600;
		// Static traffic during the course of the accident;
		int trafficInit = (int) (0.8 * maxFlow);

		// Accident
		int accidentCell = (numOfCells - 2) * 2 / 3;
		int reducedTraffic = (int) (0.2 * maxFlow);

		// Values to be computed
		int n[] = new int[numOfCells];
		int N[] = new int[numOfCells];
		int Q[] = new int[numOfCells];
		int outFlow[] = new int[numOfCells];

		// Source cell
		n[0] = Integer.MAX_VALUE;
		N[0] = Integer.MAX_VALUE;
		Q[0] = maxFlow;
		outFlow[0] = trafficInit;

		// Sink cell
		n[numOfCells - 1] = 0;
		N[numOfCells - 1] = Integer.MAX_VALUE;
		outFlow[numOfCells - 1] = 0;
		Q[numOfCells - 1] = Integer.MAX_VALUE;

		for (int i = 1; i < numOfCells - 1; i++) {
			n[i] = trafficInit;
			N[i] = maxVehiclesPerCell;
			Q[i] = maxFlow;
		}

		System.out.println(" Max vehicles per cell:" + maxVehiclesPerCell + " Accident cell:"
				+ accidentCell + " Max Flow:" + maxFlow + " reduced capacity:" + reducedTraffic);
		System.out.println("End of initialization, start the simulation...");

		for (int t = 0; t <= SIM_TIME; t = t + T) {

			outFlow[0] = (int) Math.min(Q[1], N[1] - n[1]);
			if (t >= ACC_TIME && t < (ACC_TIME + ACC_DURATION)) {
				Q[accidentCell] = reducedTraffic;
			}

			if (t > (ACC_TIME + ACC_DURATION))
				Q[accidentCell] = maxFlow;

			for (int i = 1; i < numOfCells - 1; i++) {

				// This alpha shit is not working as of now. Need to look into
				// this..
				double alpha = n[i] > Q[i + 1] ? 1.0 : 1.0;
				outFlow[i] = (int) Math
						.min(n[i], Math.min(Q[i + 1], alpha * (N[i + 1] - n[i + 1])));

			}

			for (int i = 1; i < numOfCells; i++)
				n[i] = n[i] - outFlow[i] + outFlow[i - 1];

			System.out.print(t + "\t");
			for (int i = 1; i < numOfCells - 1; i++)
				System.out.print(n[i] + "\t");
			System.out.print("\n");
			Thread.sleep((long) ((T * 1000) / PACE));

		}

	}
}
