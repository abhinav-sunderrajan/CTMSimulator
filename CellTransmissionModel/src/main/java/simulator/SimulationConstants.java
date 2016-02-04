package simulator;

public class SimulationConstants {

	// minimum distance headway (average vehicle length + average minimum gap)
	public static final double LEFF = 7.5;
	// Time gap
	public static final Double TIME_GAP = 1.4;
	// The time in seconds by which the simulation is advanced.
	public static final int TIME_STEP = 2;
	// Time in simulation
	public static long time;

	public static final double PACE = 5.0;

	// Seed for the simulation
	public static final int SEED = 998;

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
	public static final double V_OUT_MIN = 7.0;

}
