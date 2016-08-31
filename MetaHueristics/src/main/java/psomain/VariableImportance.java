package psomain;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import main.SimulatorCore;
import simulator.CellTransmissionModel;
import simulator.SimulationConstants;
import utils.ThreadPoolExecutorService;

public class VariableImportance {
	private static Random random;
	private Queue<Future<Double>> futures;
	private ThreadPoolExecutor executor;
	private Map<Integer, Double[]> futuresMap;

	private static final double ramp_min = 0.1;
	private static final double ramp_max = 0.65;
	private static final double pie_min = 0.85;
	private static final double pie_max = 1.0;
	private static final double phi_min = 1.8;
	private static final double phi_max = 3.6;
	private static final double delta_min = 0.02;
	private static final double delta_max = 0.6;
	private static final double v_out_min = 3.0;
	private static final double v_out_max = 6.0;
	private static final double timeGap_min = 1.2;
	private static final double timeGap_max = 1.5;

	private static final int NUM_OF_PARAM = 6;

	private static final int[] roadArr = { 30634, 30635, 30636, 30637, 30638, 30639, 30640, 30641,
			37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649, 30650, 30651,
			30580, 30581 };
	private static final DecimalFormat df = new DecimalFormat("#.##");
	private BufferedWriter bw;

	public VariableImportance() throws IOException {
		this.futures = new ConcurrentLinkedQueue<Future<Double>>();
		executor = ThreadPoolExecutorService.getExecutorInstance().getExecutor();
		futuresMap = new HashMap<Integer, Double[]>();
		bw = new BufferedWriter(
				new FileWriter(
						new File(
								"C:\\Users\\abhinav.sunderrajan\\Desktop\\MapMatch\\MapMatchingStats\\varimp-tts.txt")));
		bw.write("ramp_merge\tpie_merge\tphi\tdelta\tv_min\ttime_gap\tfitness\n");
	}

	/**
	 * 
	 * @author abhinav.sunderrajan
	 * 
	 */
	private class CTMSEMSimSimilarity implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					if (futures.isEmpty()) {
						Thread.sleep(1);
						continue;
					}

					Future<Double> future = futures.poll();
					Double fitness = future.get();
					Double[] params = futuresMap.get(future.hashCode());
					for (double param : params)
						bw.write(param + "\t");
					bw.write(fitness + "\n");
					bw.flush();
					futuresMap.remove(future.hashCode());
					future.cancel(true);

				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	}

	public static void main(String args[]) throws InterruptedException, IOException {
		random = new Random();
		df.setRoundingMode(RoundingMode.DOWN);
		SimulatorCore core = SimulatorCore.getInstance(1);

		VariableImportance pso = new VariableImportance();
		List<Integer> pieList = new ArrayList<>();
		for (Integer roadId : roadArr)
			pieList.add(roadId);

		pso.executor.submit(pso.new CTMSEMSimSimilarity());

		// Initialize the a population
		for (int i = 0; i < 1600; i++) {
			Double[] simParams = new Double[NUM_OF_PARAM];
			for (int j = 0; j < simParams.length; j++) {
				double val = -1;
				double min = -1;
				double max = -1;

				if (j == 0) {
					min = ramp_min;
					max = ramp_max;
				} else if (j == 1) {
					min = pie_min;
					max = pie_max;
				} else if (j == 2) {
					min = phi_min;
					max = phi_max;
				} else if (j == 3) {
					min = delta_min;
					max = delta_max;
				} else if (j == 4) {
					min = v_out_min;
					max = v_out_max;
				} else {
					min = timeGap_min;
					max = timeGap_max;
				}

				val = min + random.nextDouble() * (max - min);
				simParams[j] = Double.parseDouble(df.format(val));
				if (val > max || val < min)
					throw new IllegalStateException("wrong not in range. Min:" + min + " max:"
							+ max + " param:" + simParams[j]);

			}
			for (Integer roadId : core.getMergePriorities().keySet()) {
				if (pieList.contains(roadId))
					core.getMergePriorities().put(roadId, simParams[1]);
				else
					core.getMergePriorities().put(roadId, simParams[0]);

			}
			SimulationConstants.PHI = simParams[2];
			SimulationConstants.RAMP_DELTA = simParams[3];
			SimulationConstants.V_OUT_MIN = simParams[4];
			SimulationConstants.TIME_GAP = simParams[5];
			CellTransmissionModel ctm = new CellTransmissionModel(core, false, false, false, 2100);
			Future<Double> future = pso.executor.submit(ctm);
			pso.futures.add(future);
			pso.futuresMap.put(future.hashCode(), simParams);

		}

		System.out.println("Finished generating some data for analyses..");

	}

}
