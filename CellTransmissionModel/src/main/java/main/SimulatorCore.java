package main;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.accum.Max;
import org.nd4j.linalg.factory.Nd4j;

import rl.DeepQLearning;
import rl.ExperienceReplay;
import rl.JobRunner;
import rl.TrafficLights;
import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;
import simulator.CellTransmissionModel;
import simulator.SimulationConstants;
import strategy.WarmupCTM;
import utils.RoadRepairs;
import utils.ThreadPoolExecutorService;

public class SimulatorCore {

	// kept public for now.
	private Properties dbConnectionProperties;
	private RoadNetworkModel roadNetwork;
	private Map<Integer, Double> turnRatios;
	private Map<Integer, Double> mergePriorities;
	private Map<Integer, Double> flowRates;
	private ThreadPoolExecutor executor;
	private Map<Integer, Road> pieChangi;
	private static SimulatorCore instance;
	private static final Logger LOGGER = Logger.getLogger(SimulatorCore.class);
	private static int epochs = 540;
	private static final JobRunner jobRunner = new JobRunner();
	private static final int SIM_TIME = 1500;

	// public static variables.
	public static Random SIMCORE_RANDOM;
	public static final SAXReader SAX_READER = new SAXReader();
	public static final DecimalFormat df = new DecimalFormat("#.###");
	public static final int PIE_MAIN_ROADS[] = { 30633, 30634, 30635, 30636, 30637, 30638, 30639,
			30640, 30641, 37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649,
			30650, 30651, 30580, 30581 };
	public static final int PIE_ALL_ROADS[] = { 30633, 30634, 82, 28377, 30635, 28485, 30636,
			29310, 30637, 28578, 30638, 28946, 28947, 30639, 28516, 30640, 30790, 30641, 37976,
			37981, 37980, 30642, 37982, 30643, 38539, 28595, 30644, 29152, 28594, 30645, 28597,
			30646, 29005, 30647, 28387, 30648, 29553, 30649, 28611, 30650, 28613, 29131, 30651,
			31991, 30580, 28500, 30581 };

	private void initialize() {
		try {
			// Initialization.

			df.setRoundingMode(RoundingMode.CEILING);
			executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
			dbConnectionProperties = new Properties();
			dbConnectionProperties.load(new FileInputStream("connection.properties"));
			roadNetwork = new QIRoadNetworkModel(dbConnectionProperties, "qi_roads", "qi_nodes");

			pieChangi = new HashMap<Integer, Road>();
			for (int roadId : PIE_ALL_ROADS) {
				Road road = roadNetwork.getAllRoadsMap().get(roadId);
				pieChangi.put(roadId, road);
			}

			BufferedReader br = new BufferedReader(new FileReader(new File("Lanecount.txt")));

			while (br.ready()) {
				String line = br.readLine();
				String[] split = line.split("\t");
				if (pieChangi.containsKey(Integer.parseInt(split[0])))
					pieChangi.get(Integer.parseInt(split[0])).setLaneCount(
							Integer.parseInt(split[2]));
			}
			br.close();

			// Repair the roads
			repair(pieChangi.values());

			// Test repair
			for (Road road : pieChangi.values()) {
				double minLength = (road.getSpeedLimit()[1] * (5 / 18.0))
						* SimulationConstants.TIME_STEP;
				for (int i = 0; i < road.getSegmentsLength().length; i++) {
					if (road.getSegmentsLength()[i] < minLength) {
						throw new IllegalStateException(
								"cell length cannot be less than mimimum value for road:"
										+ road.getRoadId());
					}
				}
			}

			turnRatios = new HashMap<Integer, Double>();
			mergePriorities = new HashMap<Integer, Double>();
			flowRates = new LinkedHashMap<Integer, Double>();

			mergeTurnAndInterArrivals(pieChangi.values());
		} catch (FileNotFoundException e) {
			LOGGER.error("Unable to find the properties file", e);
		} catch (IOException e) {
			LOGGER.error("Error reading config file", e);
		}

	}

	private SimulatorCore() {
		initialize();
	}

	/**
	 * Get instance of the simulator.
	 * 
	 * @param seed
	 * @return
	 */
	public static SimulatorCore getInstance(long seed) {
		if (instance == null) {
			SIMCORE_RANDOM = new Random(seed);
			instance = new SimulatorCore();
		}

		return instance;

	}

	/**
	 * This method serves to ensure that none of the road segments which
	 * ultimately form the cells have a length smaller than the minimum length
	 * of V0*delta_T.
	 * 
	 * @param pieChangi
	 */
	public static void repair(Collection<Road> pieChangi) {

		System.out.println("Repairing roads..");
		// Need to do some repairs the roads are not exactly perfect.
		// 1) Ensure that none of the roads have a single cell this is
		// prone to errors. hence add an extra node in the middle of these
		// single segment roads.
		int nodeId = Integer.MAX_VALUE;
		for (Road road : pieChangi) {
			if (road.getSegmentsLength().length == 1) {
				double x = (road.getBeginNode().getX() + road.getEndNode().getX()) / 2.0;
				double y = (road.getBeginNode().getY() + road.getEndNode().getY()) / 2.0;
				road.getRoadNodes().add(1, new RoadNode(nodeId--, x, y));
			}

		}

		// The main constraint of CTM is that the length of cell i li>v*delta_T
		// This loop ensures that no cell is smaller than v*delta_T.
		for (Road road : pieChangi) {
			double minLength = (road.getSpeedLimit()[1] * (5 / 18.0))
					* SimulationConstants.TIME_STEP;

			int numOfSegments = road.getSegmentsLength().length;

			// If number of segments is equal to two which is usually off and on
			// ramps
			if (numOfSegments == 2)
				RoadRepairs.repairTwoSegmentRoads(road, minLength, pieChangi);

			if (numOfSegments > 2)
				RoadRepairs.repairMultiSegmentRoads(road, minLength, pieChangi);

		}

		RoadRepairs.breakLongSegments(pieChangi, --nodeId);

	}

	public static void main(String args[]) throws InterruptedException, ExecutionException,
			IOException {

		final SimulatorCore core = SimulatorCore.getInstance(System.currentTimeMillis());
		core.executor.submit(jobRunner);
		final boolean applyrampMetering = true;
		int numOfCells = WarmupCTM.getNumberOfPhysicalCells(core);
		boolean training = args[0].equalsIgnoreCase("train");
		boolean viz = args[1].equalsIgnoreCase("viz");

		if (viz) {
			core.setRandomFlowRates();
			Set<String> cellState = WarmupCTM.initializeCellState(core, 3);
			CellTransmissionModel ctm = new CellTransmissionModel(core, false, false, viz, SIM_TIME);
			ctm.intializeTrafficState(cellState);
			Future<Double> future = core.executor.submit(ctm);
			int jobId = future.hashCode();
			jobRunner.addJob(future);
			while (!jobRunner.getResultMap().containsKey(jobId))
				Thread.sleep(10);
			Double delay = jobRunner.getResultMap().get(jobId);
			System.out.println("The delay experienced by all vehicles =" + delay
					+ "\nFinished visualization exit..");
			System.out.println(core.flowRates);
			core.executor.shutdown();
			System.exit(0);
		}

		if (applyrampMetering) {
			final int numberOfRamps = 4;
			Map<Integer, String> actionMap = TrafficLights.getActionMap(numberOfRamps);
			double learningRate = 0.0009;
			double delayScale = 5.0e-6;
			double regularization = 2.0e-4;

			if (training) {
				if (args.length > 2) {
					epochs = Integer.parseInt(args[2]);
					learningRate = Double.parseDouble(args[3]);
					delayScale = Double.parseDouble(args[4]);
					regularization = Double.parseDouble(args[5]);
					System.out.println("Learning-Rate:" + learningRate + "\t Delay Scale"
							+ delayScale + "\t Regularization:" + regularization);

				}
				final DeepQLearning learning = new ExperienceReplay(numOfCells, actionMap.size(),
						learningRate, regularization);
				ExperienceReplay expRL = ((ExperienceReplay) learning);

				final Set<String> cellState = WarmupCTM.initializeCellState(core, 3);
				Double noRMDelay = getNoRMDelay(cellState, core, 5);

				// Get Q states for Q value evaluation.
				CellTransmissionModel ctm = new CellTransmissionModel(core, false, false, false,
						1200);
				CellTransmissionModel.generateStates(true, numOfCells);
				ctm.intializeTrafficState(cellState);
				core.executor.submit(ctm).get();
				final List<INDArray> states = CellTransmissionModel.getStates();
				CellTransmissionModel.generateStates(false, numOfCells);

				// Set up training

				CellTransmissionModel.setUpTraining(numOfCells, actionMap, learning, delayScale);

				// Replay Buffer.

				int trial = 0;
				final double decrement = 125.0 / (ExperienceReplay.getBuffersize() + epochs * 125.0);
				if (expRL.getReplayList().size() < ExperienceReplay.getBuffersize()) {
					while (expRL.getReplayList().size() < ExperienceReplay.getBuffersize()) {
						// Begin random flow rate set up
						core.setRandomFlowRates();
						double epsilon = learning.getEpsilon() - decrement;
						learning.setEpsilon(epsilon);
						Set<String> warmUp = WarmupCTM.initializeCellState(core, 3);
						noRMDelay = getNoRMDelay(warmUp, core, 3);
						// End random flow rate set up

						ctm = new CellTransmissionModel(core, false, true, false, SIM_TIME);
						ctm.intializeTrafficState(warmUp);
						ctm.setNoRMDelay(noRMDelay);
						Future<Double> future = core.executor.submit(ctm);
						jobRunner.addJob(future);
						trial++;
						if (expRL.getReplayList().size() % 1000 == 0)
							System.out.println("Experience replay size:"
									+ expRL.getReplayList().size());
					}
					while (trial != jobRunner.getJobCount())
						Thread.sleep(5000);

					// Is it worth going in for reinforcement learning?
					double sucessRatio = expRL.getSucessCount()
							/ ((double) jobRunner.getResultMap().size());
					jobRunner.getResultMap().clear();
					if (sucessRatio < 0.1) {
						System.out.println("Not much success better ditch ramp metering : "
								+ sucessRatio);
						System.exit(0);
					} else {
						System.out.println("Go for RL have success percentage of "
								+ (sucessRatio * 100.0) + "%");
					}

					// Store experience replay buffer to file
					OutputStream file = new FileOutputStream("exp-rl.ser");
					OutputStream buffer = new BufferedOutputStream(file);
					ObjectOutput output = new ObjectOutputStream(buffer);
					System.out.println("Start writing experience replay to  file.");
					output.writeObject(expRL.getReplayList());
					output.close();
					System.out.println("Finish writing experience replay to  file.");
				}

				if (learning.getEpsilon() == 1.0) {
					learning.setEpsilon(1.0 - ExperienceReplay.getBuffersize() / 125.0 * decrement);
				}

				expRL.setBeginTraining(true);

				System.out.println("replay buffer, full start training..");
				BufferedWriter bw = new BufferedWriter(
						new FileWriter(new File("train-results.txt")));

				bw.write("Epoch\tR.M Delay\tNo R.M delay\tQ Value\tepsilon\tMean flow rates\n");

				for (int epoch = 1; epoch <= epochs; epoch++) {

					// Set random seed
					SimulatorCore.SIMCORE_RANDOM.setSeed(System.currentTimeMillis());
					// Begin random flow rate set up
					core.setRandomFlowRates();
					Set<String> warmUp = WarmupCTM.initializeCellState(core, 3);
					noRMDelay = getNoRMDelay(warmUp, core, 3);
					// End random flow rate set up

					ctm = new CellTransmissionModel(core, false, true, false, SIM_TIME);
					ctm.intializeTrafficState(warmUp);
					ctm.setNoRMDelay(noRMDelay);
					Future<Double> future = core.executor.submit(ctm);
					Double rmDelay = future.get();
					future.cancel(true);

					double avg = 0.0;
					for (INDArray s : states) {
						INDArray actions = expRL.getTargetModel().output(s, true);
						avg += Nd4j.getExecutioner().execAndReturn(new Max(actions))
								.getFinalResult().doubleValue();
					}
					avg /= states.size();
					bw.write(epoch + "\t" + rmDelay + "\t" + noRMDelay + "\t" + avg + "\t"
							+ learning.getEpsilon() + "\t" + core.flowRates + "\n");
					double epsilon = learning.getEpsilon() - (1.0 / (epochs + trial));
					if (epsilon > 0.05)
						learning.setEpsilon((epsilon));
					if (epoch % 10 == 0)
						bw.flush();
				}
				bw.close();

				// Write neural network to file.
				File tempFile = new File("dl4j-net.nn");
				ModelSerializer.writeModel(expRL.getModel(), tempFile, true);
				System.out.format("Write to file: %s\n", tempFile.getCanonicalFile());

			} else {
				File tempFile = new File("dl4j-net.nn");
				MultiLayerNetwork model = null;
				if (tempFile.exists() && !tempFile.isDirectory()) {
					System.out.println("Loading neural network from file");
					model = ModelSerializer.restoreMultiLayerNetwork(tempFile);
				} else {
					System.out.println("NN file not found exit..");
					System.exit(0);
				}

				CellTransmissionModel.testModel(model, numOfCells, actionMap, true);

				System.out
						.println("No RM delay\tWith RM\t No RM mainline\t RM mainline\tFlow rates");
				for (int trial = 0; trial < 50; trial++) {
					SimulatorCore.SIMCORE_RANDOM.setSeed(System.currentTimeMillis());
					core.setRandomFlowRates();
					Set<String> warmUp = WarmupCTM.initializeCellState(core, 3);

					CellTransmissionModel ctm = new CellTransmissionModel(core, false, false,
							false, SIM_TIME);
					ctm.intializeTrafficState(warmUp);
					Future<Double> future = core.executor.submit(ctm);
					Double noRMDelay = future.get();
					Double noRMMailine = ctm.getMainPIEDelay();
					future.cancel(true);

					ctm = new CellTransmissionModel(core, false, true, false, SIM_TIME);
					ctm.intializeTrafficState(warmUp);
					future = core.executor.submit(ctm);
					Double rmDelay = future.get();
					Double rmMailine = ctm.getMainPIEDelay();
					future.cancel(true);

					System.out.println(noRMDelay + "\t" + rmDelay + "\t" + noRMMailine + "\t"
							+ rmMailine + "\t" + core.flowRates);
					System.out
							.println("________________________________________________________________________________________");

				}
			}

		}

		core.executor.shutdownNow();
		if (!core.executor.awaitTermination(100, TimeUnit.MICROSECONDS)) {
			System.out.println("Still waiting...");
			System.exit(0);
		}

	}

	private static Double getNoRMDelay(Set<String> cellState, SimulatorCore core, int count)
			throws InterruptedException, ExecutionException {
		Double noRMDelay = 0.0;
		for (int noRM = 0; noRM < count; noRM++) {
			CellTransmissionModel ctm = new CellTransmissionModel(core, false, false, false,
					SIM_TIME);
			ctm.intializeTrafficState(cellState);
			Future<Double> future = core.executor.submit(ctm);
			Double discountedDelay = future.get();
			noRMDelay += discountedDelay;
		}
		return noRMDelay / count;
	}

	/**
	 * Set the merge priorities, turn ratios and inter-arrival times for the
	 * roads to be simulated.
	 * 
	 * @param pieChangi
	 */
	private void mergeTurnAndInterArrivals(Collection<Road> pieChangi) {
		try {
			Document document = SAX_READER.read("road_state.xml");

			// Flow at sources
			Element flow = document.getRootElement().element("Flow");
			for (Iterator<?> i = flow.elementIterator("source"); i.hasNext();) {
				Element source = (Element) i.next();
				flowRates.put(Integer.parseInt(source.attributeValue("id")),
						Double.parseDouble(source.getStringValue()));
			}

			// Merge priorities at on-ramps
			Element mergePriority = document.getRootElement().element("MergePriorities");
			for (Iterator<?> i = mergePriority.elementIterator("merge"); i.hasNext();) {
				Element merge = (Element) i.next();
				List<Element> roadElementList = merge.elements("road");
				for (Element roadElement : roadElementList) {
					mergePriorities.put(Integer.parseInt(roadElement.attributeValue("id")),
							Double.parseDouble(roadElement.getStringValue()));

				}

			}

			// Turn ratios at off-ramps
			Element turnRatioElements = document.getRootElement().element("TurnRatios");
			for (Iterator<?> i = turnRatioElements.elementIterator("turn"); i.hasNext();) {
				Element turn = (Element) i.next();
				List<Element> roadElementList = turn.elements("road");
				for (Element roadElement : roadElementList) {
					turnRatios.put(Integer.parseInt(roadElement.attributeValue("id")),
							Double.parseDouble(roadElement.getStringValue()));
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Set random flow rates.
	 */
	private void setRandomFlowRates() {
		double maxFlow = 0.0;
		double minFlow = 0.0;
		for (Integer roadId : flowRates.keySet()) {
			if (roadId == 30633) {
				maxFlow = 4000;
				minFlow = 3700;
			} else if (roadId == 28946 || roadId == 29152 || roadId == 29005 || roadId == 31991) {
				maxFlow = 1100;
				minFlow = 950;
			} else {
				maxFlow = 1600;
				minFlow = 1400;
			}

			double flow = minFlow + (maxFlow - minFlow) * SIMCORE_RANDOM.nextDouble();
			flow = (double) Math.round(flow);
			flowRates.put(roadId, flow);
		}

	}

	/**
	 * @return the dbConnectionProperties
	 */
	public Properties getDbConnectionProperties() {
		return dbConnectionProperties;
	}

	/**
	 * @return the roadNetwork
	 */
	public RoadNetworkModel getRoadNetwork() {
		return roadNetwork;
	}

	/**
	 * @return the turnRatios
	 */
	public Map<Integer, Double> getTurnRatios() {
		return turnRatios;
	}

	/**
	 * @return the mergePriorities
	 */
	public Map<Integer, Double> getMergePriorities() {
		return mergePriorities;
	}

	/**
	 * @return the flowRates
	 */
	public Map<Integer, Double> getFlowRates() {
		return flowRates;
	}

	/**
	 * @return the pieRoads
	 */
	public static int[] getPieAllRoads() {
		return PIE_ALL_ROADS;
	}

	/**
	 * @return the pieChangi
	 */
	public Map<Integer, Road> getPieChangi() {
		return pieChangi;
	}

	/**
	 * @return the executor
	 */
	public ThreadPoolExecutor getExecutor() {
		return executor;
	}

}
