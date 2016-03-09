package simulator;

public class SimulationConstants {

	// Time gap
	public static Double TIME_GAP = 1.28;
	// The time in seconds by which the simulation is advanced.
	public static final int TIME_STEP = 2;
	// Time in simulation
	public static long time;

	public static final double PACE = 5.0;

	// Seed for the simulation
	public static final int SEED = 420;

	// The accident cell ID>
	public static final String ACCIDENT_CELL = "30651_1";

	// Number of lanes to be closed in the case of an accident
	public static final int LANES_CLOSED = 1;

	public static final double VEHICLE_LENGTH = 3.0;

	public static final double ALPHA_ANTIC = 0.15;

	// From the meta-net paper.
	public static final double AM = 2.34;

	// The rate at which vehicles move at the down stream section of a
	// congested region.
	public static double V_OUT_MIN = 4.0;

	// METANET ramp merging term
	public static double RAMP_DELTA = 0.37;

	// METANET constant term for on ramp merging term
	public static final double KAPPA = 0.452;

	// METANET constant term for on ramp lane dropping.
	public static double PHI = 2.7;

}
