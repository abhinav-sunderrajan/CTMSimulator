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
	// Number of vehicles present in the cell at time t
	protected int nt;
	// free flow speed
	protected double freeFlowSpeed;
	// backward propagation speed.

	// The number of vehicles that leave the cell in a time unit
	protected double outflow;

	protected double sendingPotential;

	protected double receivePotential;
	private double ntBefore;
	private double weightedSpeedIncoming;

	protected double meanSpeed;

	protected double sdSpeed;
	protected double densityAntic;
	protected double density;
	protected double beta;

	/**
	 * The abstract cell class.
	 * 
	 * @param cellId
	 * @param length
	 * @param w
	 */
	public Cell(String cellId, double length) {
		this.cellId = cellId;
		String[] split = cellId.split("_");
		this.road = SimulatorCore.roadNetwork.getAllRoadsMap().get(Integer.parseInt(split[0]));
		this.numOfLanes = road.getLaneCount();
		this.length = length;
		this.freeFlowSpeed = road.getFreeFlowSpeed();
		predecessors = new ArrayList<Cell>();
		successors = new ArrayList<Cell>();

		if (length > 0) {
			this.meanSpeed = road.getFreeFlowSpeed();
			this.sdSpeed = 0;
			// Initialize sending and receiving potentials for the very first
			// time.
			// The simulation will take care from here onwards,
			determineSendingPotential();
			determineReceivePotential();
		}

	}

	/**
	 * @return the density
	 */
	public double getDensity() {
		return density;
	}

	/**
	 * @param density
	 *            the density to set
	 */
	public void setDensity(double density) {
		this.density = density;
	}

	/**
	 * @return the meanSpeed
	 */
	public double getMeanSpeed() {
		return meanSpeed;
	}

	/**
	 * @param meanSpeed
	 *            the meanSpeed to set
	 */
	public void setMeanSpeed(double meanSpeed) {
		this.meanSpeed = meanSpeed;
	}

	/**
	 * @return the sdSpeed
	 */
	public double getSdSpeed() {
		return sdSpeed;
	}

	/**
	 * @param sdSpeed
	 *            the sdSpeed to set
	 */
	public void setSdSpeed(double sdSpeed) {
		this.sdSpeed = sdSpeed;
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
	 * Update the number of vehicles in each cell after simulation tick. Based
	 * on the law of conservation vehicles.
	 */
	public void updateNumberOfVehiclesInCell() {
		double inflow = 0;
		weightedSpeedIncoming = 0.0;

		// Update the number of vehicles in the cell.
		if (predecessors.size() > 1) {
			for (Cell predecessor : predecessors) {
				inflow += predecessor.outflow;
				weightedSpeedIncoming += predecessor.outflow * predecessor.meanSpeed;
			}
		} else {
			Cell predecessor = predecessors.get(0);
			if (predecessor instanceof DivergingCell) {
				double turnRatio = SimulatorCore.turnRatios.get(road.getRoadId());
				inflow = predecessor.outflow * turnRatio;
			} else {
				inflow = predecessor.outflow;
			}
			weightedSpeedIncoming = predecessor.outflow * predecessor.meanSpeed;
		}

		this.ntBefore = nt;
		nt = (int) Math.round(nt + inflow - outflow);

		if (nt < 0) {
			nt = 0;
		}

	}

	/**
	 * Update the anticipated density for the next time step.
	 */
	public void updateAnticipatedDensity() {

		// update the density in the cell.
		density = nt / (length * numOfLanes);

		// Update anticipated density
		if (!(this instanceof SourceCell || this instanceof SinkCell)) {
			densityAntic = SimulationConstants.ALPHA_ANTIC * nt / (length * numOfLanes);
			for (Cell successor : successors) {
				double turnRatio = (SimulatorCore.turnRatios.get(successor.getRoad().getRoadId()) == null) ? 1.0
						: SimulatorCore.turnRatios.get(successor.getRoad().getRoadId());
				densityAntic += (1 - SimulationConstants.ALPHA_ANTIC) * successor.nt
						/ (successor.length * successor.numOfLanes) * turnRatio;
			}
		}

	}

	/**
	 * Update the mean speed of the cell.
	 */
	public void updateMeanSpeed() {

		if (!(this instanceof SourceCell || this instanceof SinkCell)) {

			double vinTerm = -1;
			if (nt > 0) {
				double speedofVehiclesremaining = (ntBefore - outflow) * meanSpeed;
				vinTerm = (speedofVehiclesremaining + weightedSpeedIncoming) / nt;
			} else {
				vinTerm = freeFlowSpeed;
			}

			double successorDensityAntic = 0.0;
			for (Cell successor : successors) {
				double turnRatio = (SimulatorCore.turnRatios.get(successor.getRoad().getRoadId()) == null) ? 1.0
						: SimulatorCore.turnRatios.get(successor.getRoad().getRoadId());
				successorDensityAntic = +turnRatio * successor.densityAntic;
			}

			beta = (Math.abs(densityAntic - successorDensityAntic) >= 1) ? 0.3 : 0.7;
			meanSpeed = beta
					* vinTerm
					+ (1 - beta)
					* freeFlowSpeed
					* Math.exp((-1 / SimulationConstants.AM)
							* Math.pow(nt / getnMax(), SimulationConstants.AM));

			if (nt > Math.round(getnMax()) || meanSpeed < 0)
				System.err.println("cell ID:" + cellId + " nt:" + nt + " nmax:"
						+ Math.round(getnMax()) + " mean speed:" + meanSpeed);

		}

	}

	/**
	 * Return the receiving potential of this cell.
	 * 
	 * @return
	 */
	public void determineReceivePotential() {
		this.receivePotential = getnMax() + outflow - nt;
		if (receivePotential < 0) {
			receivePotential = outflow;
		}
	}

	/**
	 * return the sending potential of this cell.
	 * 
	 * @return
	 */
	public void determineSendingPotential() {
		double param1 = nt * meanSpeed * SimulationConstants.TIME_STEP / length;
		double param2 = nt * SimulationConstants.V_OUT_MIN * SimulationConstants.TIME_STEP / length;
		this.sendingPotential = Math.max(param1, param2);
		outflow = sendingPotential;
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
	 * Set the number of vehicles in the cell.
	 * 
	 * @param nt
	 */
	public void setNumberOfvehicles(int nt) {
		this.nt = nt;
		this.density = nt / (length * numOfLanes);
	}

	/**
	 * @return the nt the number of vehicles currently in the cell.
	 */
	public double getNumOfVehicles() {
		return nt;
	}

	/**
	 * @return the nMax the maximum number of vehicles that can be accommodated
	 *         in the cell.
	 */
	public double getnMax() {

		double maxDesnsityPerlane = length
				/ (SimulationConstants.TIME_GAP * meanSpeed + SimulationConstants.VEHICLE_LENGTH);
		double nMax = maxDesnsityPerlane * numOfLanes;
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
