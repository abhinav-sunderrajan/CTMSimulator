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
	protected double nt;
	// free flow speed
	protected double freeFlowSpeed;
	// backward propagation speed.

	// The number of vehicles that leave the cell in a time unit
	protected double outflow;

	protected double sendingPotential;

	protected double receivePotential;
	private double ntBefore;

	protected double meanSpeed;
	protected SimulatorCore core;
	protected double sdSpeed;
	protected double densityAntic;
	protected double density;
	protected double beta;
	protected double criticalDensity;
	protected double nMax;
	protected boolean initilalized;
	private static boolean applyRampMetering;
	private static List<Road> ramps;

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
		core = SimulatorCore.getInstance(1);
		this.road = core.getRoadNetwork().getAllRoadsMap().get(Integer.parseInt(split[0]));
		this.numOfLanes = road.getLaneCount();
		this.length = length;
		this.freeFlowSpeed = road.getSpeedLimit()[1] * (5.0 / 18);
		predecessors = new ArrayList<Cell>();
		successors = new ArrayList<Cell>();
		if (length > 0) {
			this.meanSpeed = freeFlowSpeed;
			double meanVehicleLength = SimulationConstants.VEHICLE_LENGTH
					+ core.getRandom().nextGaussian() * 0.1;
			double maxDesnsityPerlane = length
					/ (SimulationConstants.TIME_GAP * meanSpeed + meanVehicleLength);
			nMax = maxDesnsityPerlane * numOfLanes;
			this.sdSpeed = 0;
			// Initialize sending and receiving potentials for the very first
			// time.
			// The simulation will take care from here onwards,
			determineSendingPotential();
			determineReceivePotential();
			criticalDensity = nMax / (numOfLanes * length);
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

		// Update the number of vehicles in the cell.
		if (predecessors.size() > 1) {
			for (Cell predecessor : predecessors)
				inflow += predecessor.outflow;
		} else {
			Cell predecessor = predecessors.get(0);
			if (predecessor instanceof DivergingCell) {
				inflow = ((DivergingCell) predecessor).getOutFlowsMap().get(cellId);
			} else {
				inflow = predecessor.outflow;
			}
		}

		this.ntBefore = nt;
		nt = nt + inflow - outflow;

		if (nt < 0) {
			throw new IllegalStateException(
					"The number of vehicle in a cell cannot be less than zero..");
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
			densityAntic = SimulationConstants.ALPHA_ANTIC * density;
			for (Cell successor : successors) {
				if (successor instanceof SinkCell) {
					densityAntic = 0.0;
					break;
				}
				double turnRatio = 1.0;
				if (this instanceof DivergingCell)
					turnRatio = core.getTurnRatios().get(successor.getRoad().getRoadId());

				densityAntic += (1 - SimulationConstants.ALPHA_ANTIC)
						* (successor.nt / (successor.length * successor.numOfLanes)) * turnRatio;
			}
		}

	}

	/**
	 * Update the mean speed of the cell.
	 */
	public void updateMeanSpeed() {

		if (!(this instanceof SourceCell || this instanceof SinkCell)) {

			// update the density in the cell.
			density = nt / (length * numOfLanes);

			double vinTerm = -1;
			if (nt > 0) {

				double speedofIncomingVehicles = 0.0;
				for (Cell predecessor : predecessors) {
					if (predecessor instanceof DivergingCell) {
						speedofIncomingVehicles += ((DivergingCell) predecessor).getOutFlowsMap()
								.get(cellId) * predecessor.meanSpeed;
					} else if (predecessor instanceof SourceCell) {
						speedofIncomingVehicles += predecessor.outflow * meanSpeed;
					} else {
						speedofIncomingVehicles += predecessor.outflow * predecessor.meanSpeed;
					}
				}

				double speedofVehiclesremaining = (ntBefore - outflow) * meanSpeed;
				vinTerm = (speedofVehiclesremaining + speedofIncomingVehicles) / nt;
			} else {
				vinTerm = freeFlowSpeed;
			}

			vinTerm = Math.max(SimulationConstants.V_OUT_MIN, vinTerm);

			double successorDensityAntic = 0.0;
			for (Cell successor : successors) {
				double turnRatio = (core.getTurnRatios().get(successor.getRoad().getRoadId()) == null) ? 1.0
						: core.getTurnRatios().get(successor.getRoad().getRoadId());
				successorDensityAntic = +turnRatio * successor.densityAntic;
			}

			// if density difference is large then give more weightage to the
			// anticipated part of the term below.
			beta = (Math.abs(densityAntic - successorDensityAntic) >= 1) ? 0.2 : 0.8;
			double densityRatio = densityAntic / criticalDensity;

			// This is the on ramp merging term as suggested by METANET.

			double rampTerm = -1.0;
			if (predecessors.size() > 1) {
				MergingCell onRampCell = null;
				if (predecessors.get(0).numOfLanes == 2)
					onRampCell = (MergingCell) predecessors.get(0);
				else
					onRampCell = (MergingCell) predecessors.get(1);

				rampTerm = SimulationConstants.RAMP_DELTA * SimulationConstants.TIME_STEP
						* onRampCell.getOutflow() * meanSpeed
						/ (numOfLanes * length * (density + SimulationConstants.KAPPA));
			}

			// For the speed drop occurring due lane drops at on ramp P.I.E
			// merger.

			double laneDropTerm = -1.0;
			if (this instanceof MergingCell && numOfLanes == 2)
				laneDropTerm = (SimulationConstants.PHI * 1.0 * SimulationConstants.TIME_STEP
						* density * meanSpeed * meanSpeed)
						/ (length * numOfLanes * criticalDensity);

			this.meanSpeed = beta
					* vinTerm
					+ (1 - beta)
					* freeFlowSpeed
					* Math.exp((-1 / SimulationConstants.AM)
							* Math.pow(densityRatio, SimulationConstants.AM))
					+ core.getRandom().nextGaussian() * 0.0;

			// System.out.println(meanSpeed);

			// The noise added has a mean as determined and the standard
			// deviation as determined while traffic state is input.
			// this.meanSpeed = freeFlowSpeed
			// * Math.exp((-1 / SimulationConstants.AM)
			// * Math.pow((density / criticalDensity), SimulationConstants.AM))
			// + SimulatorCore.random.nextGaussian() * 3.0;
			// System.out.println(" <<>> " + meanSpeed);

			if (predecessors.size() > 1)
				this.meanSpeed = this.meanSpeed - rampTerm;

			if (this instanceof MergingCell && numOfLanes == 2) {
				this.meanSpeed = this.meanSpeed - laneDropTerm;
			}

			if (applyRampMetering) {
				double minSpeed = SimulationConstants.V_OUT_MIN;
				if (ramps.contains(road)) {
					minSpeed = 0.0;
				}
				if (this.meanSpeed <= minSpeed) {
					this.meanSpeed = minSpeed;
				}

			} else {
				if (this.meanSpeed <= SimulationConstants.V_OUT_MIN) {
					this.meanSpeed = SimulationConstants.V_OUT_MIN;
				}
			}

			this.meanSpeed = meanSpeed > freeFlowSpeed ? freeFlowSpeed : meanSpeed;
			double meanVehicleLength = SimulationConstants.VEHICLE_LENGTH
					+ core.getRandom().nextGaussian() * 0.1;
			double maxDesnsityPerlane = length
					/ (SimulationConstants.TIME_GAP * meanSpeed + meanVehicleLength);
			this.nMax = maxDesnsityPerlane * numOfLanes;

		}

	}

	/**
	 * Return the receiving potential of this cell.
	 * 
	 * @return
	 */
	public void determineReceivePotential() {
		this.receivePotential = nMax /* + outflow */- nt;

		if (receivePotential < 0) {
			receivePotential = 0;
		}

	}

	/**
	 * Computes and sets the sending potential of this cell.
	 * 
	 * @return
	 */
	public void determineSendingPotential() {

		double param1 = (nt * meanSpeed * SimulationConstants.TIME_STEP) / length;
		double param2 = (nt * SimulationConstants.V_OUT_MIN * SimulationConstants.TIME_STEP)
				/ length;
		this.sendingPotential = Math.max(param1, param2);
		this.sendingPotential = Math.min(nt, sendingPotential);

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
	public void setNumberOfvehicles(double nt) {
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

	/**
	 * @return the criticalDensity
	 */
	public double getCriticalDensity() {
		return criticalDensity;
	}

	/**
	 * @return the initilalized
	 */
	public boolean isInitilalized() {
		return initilalized;
	}

	/**
	 * @param initilalized
	 *            the initilalized to set
	 */
	public void setInitilalized(boolean initilalized) {
		this.initilalized = initilalized;
	}

	/**
	 * @return the applyRampMetering
	 */
	public static boolean isApplyRampMetering() {
		return applyRampMetering;
	}

	/**
	 * @param applyRampMetering
	 *            the applyRampMetering to set
	 */
	public static void setApplyRampMetering(boolean applyRampMetering) {
		Cell.applyRampMetering = applyRampMetering;
	}

	/**
	 * @return the ramps
	 */
	public static List<Road> getRamps() {
		return ramps;
	}

	/**
	 * @param ramps
	 *            the ramps to set
	 */
	public static void setRamps(List<Road> ramps) {
		Cell.ramps = ramps;
	}

}
