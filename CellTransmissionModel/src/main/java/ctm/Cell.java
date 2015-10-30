package ctm;

import java.util.ArrayList;
import java.util.List;

import main.SimulatorCore;
import rnwmodel.Road;
import simulator.SimulationConstants;

/**
 * Base class of the cell in the cell transmission model.
 * 
 * @author abhinav
 * 
 */
public abstract class Cell {

	protected String cellId;
	protected List<Cell> predecessors;
	protected List<Cell> successors;
	// Id of the road the cell belongs
	protected Road road;

	protected int numOfLanes;

	// model parameters

	// Cell length
	protected double length;
	// maximum number of vehicles that can be contained in a cell.
	protected double nMax;
	// Number of vehicles present in the cell at time t
	protected double nt;
	// max number of vehicles that flow in or out of this cell at given time t.
	protected double Qmax;
	// free flow speed
	protected double freeFlowSpeed;
	// backward propagation speed.
	protected double w;

	// The number of vehicles that leave the cell in a time unit
	protected double outflow;

	private double sendingPotential;

	private double receivePotential;

	/**
	 * The abstract cell class.
	 * 
	 * @param cellId
	 * @param length
	 * @param freeFlowSpeed
	 * @param jamDensity
	 * @param w
	 */
	public Cell(String cellId, double length, double freeFlowSpeed, int numOfLanes) {
		this.cellId = cellId;
		this.numOfLanes = numOfLanes;
		this.length = length;
		this.freeFlowSpeed = freeFlowSpeed;

		String[] split = cellId.split("_");

		this.road = SimulatorCore.roadNetwork.getAllRoadsMap().get(Integer.parseInt(split[0]));
		predecessors = new ArrayList<Cell>();
		successors = new ArrayList<Cell>();

		// Cell parameters are set to the default .
		w = SimulationConstants.LEFF / SimulationConstants.TIME_GAP;
		double capacityPerLane = freeFlowSpeed
				/ (freeFlowSpeed * SimulationConstants.TIME_GAP + SimulationConstants.LEFF);
		double maxDesnsityPerlane = length / SimulationConstants.LEFF;
		Qmax = capacityPerLane * numOfLanes * SimulationConstants.TIME_STEP;
		nMax = maxDesnsityPerlane * numOfLanes;
		this.sendingPotential = Math.min(this.nt, this.Qmax);
		this.receivePotential = Math.min(Qmax, getAlpha() * (nMax - nt));

		// Random number of vehicles in all cells initially.
		nt = nMax / (1.2 + SimulatorCore.random.nextDouble());
	}

	/**
	 * Introduces some amount of stochasticty in the simulation by means of
	 * changing time gap and minimum distance headway.
	 * 
	 * @param leff
	 *            the new minimum distance headway.
	 * @param timeGap
	 *            the new time-gap.
	 */
	public void introduceStochasticty(double leff, double timeGap) {
		w = leff / timeGap;
		double capacityPerLane = freeFlowSpeed / (freeFlowSpeed * timeGap + leff);
		double maxDensityPerlane = length / SimulationConstants.LEFF;
		Qmax = capacityPerLane * numOfLanes * SimulationConstants.TIME_STEP;
		nMax = maxDensityPerlane * numOfLanes;

	}

	/**
	 * Reset the cell parameters to the original values.
	 */
	public void reset() {
		// Cell parameters are set to the default .
		w = SimulationConstants.LEFF / SimulationConstants.TIME_GAP;
		double capacityPerLane = freeFlowSpeed
				/ (freeFlowSpeed * SimulationConstants.TIME_GAP + SimulationConstants.LEFF);
		double maxDesnsityPerlane = length / SimulationConstants.LEFF;
		Qmax = capacityPerLane * numOfLanes * SimulationConstants.TIME_STEP;
		nMax = maxDesnsityPerlane * numOfLanes;
	}

	/**
	 * The number of vehicles that moves to the next cell(s) in this current
	 * time step.
	 * 
	 * @return the outflow
	 */
	public double getOutflow() {
		return outflow;
	}

	/**
	 * @param outflow
	 *            the outflow to set
	 */
	public void setOutflow(double outflow) {
		this.outflow = outflow;
	}

	/**
	 * The road ID associated with this cell.
	 * 
	 * @return the roadId
	 */
	public Road getRoad() {
		return road;
	}

	/**
	 * @return the cellId
	 */
	public String getCellId() {
		return cellId;
	}

	/**
	 * @param cellId
	 *            the cellId to set
	 */
	public void setCellId(String cellId) {
		this.cellId = cellId;
	}

	/**
	 * A dimension-less constant representing the ratio of the backward moving
	 * wave speed to the free flow speed
	 * 
	 * @return
	 */
	private double getAlpha() {
		return nt <= Qmax ? 1.0 : w / freeFlowSpeed;
	}

	/**
	 * Update the number of vehicles in each cell after simulation tick. Based
	 * on the law of conservation vehicles.
	 */
	public void updateNumberOfVehiclesInCell() {
		double inflow = 0;

		// merging cells
		if (predecessors.size() > 1) {
			for (Cell predecessor : predecessors)
				inflow += predecessor.outflow;
		} else {
			Cell predecessor = predecessors.get(0);
			if (predecessor instanceof DivergingCell)
				inflow = predecessor.outflow * SimulatorCore.turnRatios.get(road.getRoadId());
			else
				inflow = predecessor.outflow;
		}

		if (predecessors.size() == 1 && predecessors.get(0) instanceof SourceCell) {
			nt = (nt + inflow - outflow) < 0 ? 0 : nt + inflow - outflow;
		} else {
			nt = nt + inflow - outflow;
		}

	}

	/**
	 * Return the receiving potential of this cell.
	 * 
	 * @return
	 */
	public double getReceivePotential() {
		this.receivePotential = Math.min(this.Qmax, getAlpha() * (nMax - nt));
		return receivePotential;
	}

	/**
	 * return the sending potential of this cell.
	 * 
	 * @return
	 */
	public double getSendingPotential() {
		this.sendingPotential = Math.min(this.nt, this.Qmax);
		return sendingPotential;
	}

	@Override
	public String toString() {
		return cellId;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Cell) {
			Cell c = (Cell) o;
			if (c.cellId.equalsIgnoreCase(this.cellId)) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return cellId.hashCode();
	}

	/**
	 * Update the flow along all the connectors associated with cell
	 */
	public abstract void updateOutFlow();

	public void setRoad(Road road) {
		this.road = road;
	}

	/**
	 * Add cell to the predecessor only if its not present.
	 * 
	 * @param cell
	 */
	public void addPredecessor(Cell cell) {
		if (!predecessors.contains(cell))
			predecessors.add(cell);
	}

	/**
	 * Add cell to the predecessor only if its not present.
	 * 
	 * @param cell
	 */
	public void addSuccessor(Cell cell) {

		if (!successors.contains(cell)) {
			successors.add(cell);
		}

	}

	/**
	 * @return the nt the number of vehicles currently in the cell.
	 */
	public double getNumOfVehiclesInCell() {
		return nt;
	}

	/**
	 * @return the nMax the maximum number of vehicles that can be accommodated
	 *         in the cell.
	 */
	public double getnMax() {
		return nMax;
	}

	/**
	 * @return the length of the cell.
	 */
	public double getLength() {
		return length;
	}

	/**
	 * @return the numOfLanes of lanes in this cell.
	 */
	public int getNumOfLanes() {
		return numOfLanes;
	}

	/**
	 * @return the freeFlowSpeed of this cell.
	 */
	public double getFreeFlowSpeed() {
		return freeFlowSpeed;
	}

	/**
	 * @return the qmax the capacity of this cell in number of vehicles/cell.
	 */
	public double getQmax() {
		return Qmax;
	}

	/**
	 * @param qmax
	 *            the qmax to set
	 */
	public void setQmax(double qmax) {
		this.Qmax = qmax;
	}

	/**
	 * @return the predecessors
	 */
	public List<Cell> getPredecessors() {
		return predecessors;
	}

	/**
	 * @return the successors
	 */
	public List<Cell> getSuccessors() {
		return successors;
	}

}
