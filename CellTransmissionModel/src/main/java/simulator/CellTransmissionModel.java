package simulator;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import main.SimulatorCore;

import org.apache.log4j.Logger;

import rnwmodel.Road;
import viz.CTMSimViewer;
import ctm.Cell;
import ctm.CellNetwork;
import ctm.MergingCell;
import ctm.OrdinaryCell;
import ctm.SinkCell;
import ctm.SourceCell;

/**
 * Main singleton class which initializes and advances a PIE scale macroscopic
 * Cell transmission based traffic simulation.
 * 
 * @see <a
 *      href="http://en.wikipedia.org/wiki/Cell_Transmission_Model">http://en.wikipedia.org/wiki/Cell_Transmission_Model</a>
 * 
 * @author abhinav
 * 
 * 
 */

public class CellTransmissionModel implements Runnable {

	public static CellNetwork cellNetwork;
	public List<Road> ramps;
	public long simulationTime;
	private long startTime;
	private long endTime;
	private CTMSimViewer viewer;
	private Map<Cell, Color> cellColorMap;
	private Collection<Road> pieChangiOrdered;
	private boolean haveVisualization;
	private boolean simulateAccident;
	private boolean applyRampMetering;
	private List<RampMeter> meteredRamps;
	private List<Cell> modifiedCells = new ArrayList<>();

	// Static variables
	private static final String ACCIDENT_CELL = "30651_1";
	private static CellTransmissionModel simulator;
	private static final Logger LOGGER = Logger.getLogger(CellTransmissionModel.class);

	/**
	 * Initialize the Cell transmission model by creating the cell network from
	 * the roads.
	 */
	private void intializeCTM(Collection<Road> roadCollection, boolean haveAccident,
			boolean applyMetering, boolean haveViz) {

		this.ramps = new ArrayList<>();
		cellColorMap = new ConcurrentHashMap<Cell, Color>();
		this.meteredRamps = new ArrayList<RampMeter>();
		this.haveVisualization = haveViz;
		this.simulateAccident = haveAccident;
		this.applyRampMetering = applyMetering;
		this.pieChangiOrdered = roadCollection;
		SimulatorCore.df.setRoundingMode(RoundingMode.CEILING);

		LOGGER.info("Create a cell based network for the roads.");
		cellNetwork = new CellNetwork(pieChangiOrdered, ramps);

		for (Road ramp : ramps) {
			RampMeter rampMeter = new RampMeter(ramp);
			rampMeter.setQueuePercentage(1.0);
			meteredRamps.add(rampMeter);
		}

		if (haveVisualization) {
			viewer = CTMSimViewer.getCTMViewerInstance("CTM Model", SimulatorCore.roadNetwork,
					cellColorMap, SimulatorCore.dbConnectionProperties);
		}

	}

	/**
	 * Gets the CTM simulator instance.
	 * 
	 * @param roadCollection
	 *            the roads to be simulated.
	 * @param haveAccident
	 *            simulate an accident?
	 * @param applyMetering
	 *            apply ramp metering ?
	 * @param haveViz
	 *            have a visualization?
	 * @return the CTM simulator instance.
	 */
	public static CellTransmissionModel getSimulatorInstance(Collection<Road> roadCollection,
			boolean haveAccident, boolean applyMetering, boolean haveViz) {
		if (simulator == null) {
			simulator = new CellTransmissionModel(roadCollection, haveAccident, applyMetering,
					haveViz);
		}
		return simulator;
	}

	private CellTransmissionModel(Collection<Road> roadCollection, boolean haveAccident,
			boolean applyMetering, boolean haveViz) {

		intializeCTM(roadCollection, haveAccident, applyMetering, haveViz);

		// Color coding the visualization
		if (haveVisualization) {
			for (Cell cell : cellNetwork.getCellMap().values()) {
				if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
					Color color = null;
					if (cell instanceof OrdinaryCell) {
						// Gray for ordinary cell.
						color = new Color(137, 112, 122, 225);
					} else if (cell instanceof MergingCell) {
						// Green for merging cell.
						color = new Color(51, 255, 153, 225);
					} else {
						// Blue for diverging cell.
						color = new Color(51, 51, 255, 225);
					}
					cellColorMap.put(cell, color);
				}
			}

		}

		startTime = Long.parseLong(SimulatorCore.configProperties.getProperty("start.hour"));
		endTime = Long.parseLong(SimulatorCore.configProperties.getProperty("end.hour"));

	}

	/**
	 * @return the cellNetwork
	 */
	public CellNetwork getCellNetwork() {
		return cellNetwork;
	}

	@Override
	public void run() {
		try {
			Cell accidentCell = cellNetwork.getCellMap().get(ACCIDENT_CELL);
			double qMaxAccCell = accidentCell.getQmax();
			long time = System.currentTimeMillis();
			double totalWaitingTime = 0.0;
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File("ctmsim_acc.txt")));
			for (simulationTime = startTime; simulationTime <= endTime; simulationTime += SimulationConstants.TIME_STEP) {

				double totalTravelTime = 0.0;

				// Simulate an accident.
				if (simulateAccident) {
					if (simulationTime >= 1600 && simulationTime < 4300) {
						System.err.println(simulationTime + " : ACCIDENT START!!");
						accidentCell.setQmax(qMaxAccCell * 0.75);
					}

					if (simulationTime == 4300) {
						System.err.println(simulationTime + " : ACCIDENT CLEARS!!");
						accidentCell.setQmax(qMaxAccCell);
					}
				}

				// Applying ramp metering
				if (applyRampMetering && simulationTime % (SimulationConstants.TIME_STEP * 4) == 0) {
					for (RampMeter rampMeter : meteredRamps) {
						if (rampMeter.peakDensityReached()) {
							rampMeter.setGreen(true);
							rampMeter.setRed(false);
						} else {
							rampMeter.setGreen(false);
							rampMeter.setRed(true);
							// If the ramp meter is red then the vehicles do end
							// up waiting.
							totalWaitingTime += SimulationConstants.TIME_STEP * 4;
						}

						rampMeter.regulateFlow();
					}
				}

				// Update the outflows for all cells
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateOutFlow();

				// Now update the number of vehicles in each cell.
				for (Cell cell : cellNetwork.getCellMap().values())
					cell.updateNumberOfVehiclesInCell();

				// Reset all modified cells to their defaiult model values
				if (simulationTime % (SimulationConstants.TIME_STEP * 2) == 0) {
					for (Cell cell : modifiedCells)
						cell.reset();
					modifiedCells.clear();
				}

				// This loop performs two functions.
				// 1) Computes cell statistics such as density and average speed
				// used to compute travel time and waiting times.

				for (Cell cell : cellNetwork.getCellMap().values()) {

					// travel time computation along the freeway
					if (!(cell instanceof SinkCell || cell instanceof SourceCell)) {
						double cellSpeed = cell.getFreeFlowSpeed();
						// v=v_freeFlow-(v_freeFlow/jam_density)*density
						// v=v_freeFlow* ln(jam_density/density)

						double cellDensity = cell.getNumOfVehiclesInCell() / cell.getnMax();

						if (cellDensity > 1.0)
							throw new IllegalStateException(
									"There can never be more than nMax vehicles in cell"
											+ cell.getCellId());

						if (cell.getNumOfVehiclesInCell() > 0) {
							cellSpeed = cell.getFreeFlowSpeed() * Math.log(1.0 / cellDensity);
						}

						if (cellSpeed > cell.getFreeFlowSpeed())
							cellSpeed = cell.getFreeFlowSpeed();

						if (cell.getRoad().getName().contains("P.I.E (Changi)")) {
							if (cellSpeed > 0.5)
								totalTravelTime += cell.getLength() / cellSpeed;
							else
								totalWaitingTime += SimulationConstants.TIME_STEP;

							// File writing.
							if (simulationTime >= 800) {
								String split[] = cell.getCellId().split("_");
								bw.write(split[0] + "," + split[1] + "," + cellSpeed + ","
										+ simulationTime + "\n");

							}

						}

						if (haveVisualization)
							cellColorMap.put(cell, CTMSimViewer.numberToColor(1.0 - cellDensity));

						// Introduce some kind of variations
						// As noticed if the cell speed is less cars tend to
						// bunch together and the reduce their time and distance
						// headway.
						if (cellSpeed < 5.0) {
							cell.introduceStochasticty(
									uniform(SimulationConstants.LEFF * 0.4,
											SimulationConstants.LEFF * 0.6),
									uniform(SimulationConstants.TIME_GAP * 0.6,
											SimulationConstants.TIME_GAP * 0.8));

						} else {
							// Modify the dynamics of 1/3rd of all cells by
							// changing their time and distance headway every
							// three time steps.
							if (simulationTime % (SimulationConstants.TIME_STEP * 9) == 0) {
								if (SimulatorCore.random.nextDouble() < 0.6) {
									// Do not mess with the accident cell and
									// the metered ramp cells.
									boolean isMetercell = false;
									for (RampMeter rampMeter : meteredRamps) {
										if (rampMeter.getMeterCell().equals(cell)) {
											isMetercell = true;
											break;
										}
									}

									if (!(isMetercell || accidentCell.equals(cell))) {
										cell.introduceStochasticty(
												uniform(SimulationConstants.LEFF * 0.5,
														SimulationConstants.LEFF * 1.5),
												uniform(SimulationConstants.TIME_GAP * 0.8,
														SimulationConstants.TIME_GAP * 1.4));
										modifiedCells.add(cell);
									}

								}
							}

						}

					}

				}

				// Repaint the map frame with sleep.
				if (haveVisualization) {
					viewer.getMapFrame().repaint();
					Thread.sleep(50);
				}

				// System.out.println("travel time is "
				// + SimulatorCore.df.format(totalTravelTime / (60.0)) +
				// " mins");

			}

			System.out.println("Total waiting time is "
					+ SimulatorCore.df.format((totalWaitingTime / (60.0))) + " minutes");

			bw.flush();
			bw.close();

			LOGGER.info("Finished " + ((endTime - startTime) / 60.0) + " minute simulation in :"
					+ (System.currentTimeMillis() - time) + " ms");
			Thread.sleep(100);
			// if (haveVisualization)
			// viewer.getMapFrame().dispose();

		} catch (InterruptedException e) {
			LOGGER.error("Error waiting  for simulation time to advance.", e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Returns a random real number uniformly in [a, b).
	 * 
	 * @param a
	 *            the left end-point
	 * @param b
	 *            the right end-point
	 * @return a random real number uniformly in [a, b)
	 * @throws IllegalArgumentException
	 *             unless <tt>a < b</tt>
	 */
	public static double uniform(double a, double b) {
		if (!(a < b))
			throw new IllegalArgumentException("Invalid range");
		return a + SimulatorCore.random.nextDouble() * (b - a);
	}

}
