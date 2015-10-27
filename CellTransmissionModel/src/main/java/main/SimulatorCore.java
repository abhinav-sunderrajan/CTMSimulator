package main;

import simulator.CTMSimulator;

public class SimulatorCore {

	private static final int PIE_ROADS[] = { 30633, 30634, 82, 28377, 30635, 28485, 30636, 38541,
			38260, 29309, 29310, 30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640, 30788,
			30789, 30790, 30641, 37976, 37981, 37980, 30642, 37982, 30643, 38539, 2355, 2356,
			28595, 30644, 22009, 29152, 28594, 30645, 28597, 30646, 19116, 19117, 29005, 30647,
			28387, 30648, 29553, 30649, 28611, 30650, 28613, 29131, 30651, 31985, 31991, 30580,
			28500, 30581 };

	public static void main(String args[]) {
		CTMSimulator sim = CTMSimulator.getSimulatorInstance(PIE_ROADS, true, false, false);
		Thread th = new Thread(sim);
		th.start();
	}

}
