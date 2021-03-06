package strategy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import main.SimulatorCore;
import simulator.CellTransmissionModel;
import ctm.Cell;
import ctm.CellNetwork;
import ctm.SinkCell;
import ctm.SourceCell;

/**
 * You unnecessarily simulate 15-20 minutes of the predictive simulation as a
 * warm up time. Use this utility class to which provides the cell state at the
 * end of 15 minutes for a given traffic state by averaging 5 different runs.
 * This can then be used for the evaluation of any control strategy.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class WarmupCTM {
	private static Set<String> state = new HashSet<>();

	/**
	 * Returns a string representing cell id, mean speed and number of vehicles
	 * After running and averaging over five simulations.
	 * 
	 * @param core
	 *            simulation core
	 * @param numOfIter
	 *            number of iterations
	 * @return
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public static Set<String> initializeCellState(SimulatorCore core, int numOfIter)
			throws InterruptedException, ExecutionException {
		Map<Cell, Double> cellSpeed = new HashMap<Cell, Double>();
		Map<Cell, Double> cellNumOfVehicles = new HashMap<Cell, Double>();
		state.clear();
		for (int i = 0; i < numOfIter; i++) {
			CellTransmissionModel ctm = new CellTransmissionModel(core, false, false, false, 1800);
			Future<Double> future = core.getExecutor().submit(ctm);
			future.get();
			CellNetwork cellNetwork = ctm.getCellNetwork();
			for (Cell cell : cellNetwork.getCellMap().values()) {
				if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
					if (!cellSpeed.containsKey(cell)) {
						cellSpeed.put(cell, cell.getMeanSpeed());
						cellNumOfVehicles.put(cell, cell.getNumOfVehicles());
					} else {
						double sumSpeed = cell.getMeanSpeed() + cellSpeed.get(cell);
						double sumNumOfVehicles = cell.getNumOfVehicles()
								+ cellNumOfVehicles.get(cell);
						cellSpeed.put(cell, sumSpeed);
						cellNumOfVehicles.put(cell, sumNumOfVehicles);

					}
				}
			}
		}

		for (Cell cell : cellSpeed.keySet()) {
			double meanSpeed = cellSpeed.get(cell) / numOfIter;
			double meanNt = Math.round(cellNumOfVehicles.get(cell) / numOfIter);
			if (meanNt > cell.getnMax())
				meanNt = cell.getnMax();
			state.add(cell.getCellId() + ":" + meanSpeed + ":" + meanNt);
		}

		return state;
	}

	/**
	 * Returns the number of physical cells in the cell network.
	 * 
	 * @param core
	 * @return
	 */
	public static int getNumberOfPhysicalCells(SimulatorCore core) {
		CellTransmissionModel ctm = new CellTransmissionModel(core, false, false, false, 2000);
		CellNetwork cellNetwork = ctm.getCellNetwork();
		int numOfCells = 0;
		for (Cell cell : cellNetwork.getCellMap().values()) {
			if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
				numOfCells++;
			}
		}
		return numOfCells;
	}
}
