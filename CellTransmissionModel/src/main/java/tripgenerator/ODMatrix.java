package tripgenerator;

import java.util.Map;
import java.util.TreeMap;

/**
 * Class representing an OD matrix.
 * 
 * @author abhinav
 * 
 */
public class ODMatrix {

	private Map<Integer, TreeMap<String, Integer>> odMatrix;
	private int intervals;
	private int timeResolutionInMinutes;

	/**
	 * Initialize the OD matrix. The number of intervals in the OD
	 * matrix=numberofHours*60/timetimeResolutionInMinutes
	 * 
	 * @param timeResolutionInMinutes
	 *            the time resolution in minutes to generate the OD matrix.
	 * @param numberofHours
	 *            the number of hours for which the OD matix is generated.
	 */
	public ODMatrix(int timeResolutionInMinutes, int numberofHours) {
		intervals = (numberofHours * 60) / timeResolutionInMinutes;
		odMatrix = new TreeMap<Integer, TreeMap<String, Integer>>();
		this.timeResolutionInMinutes = timeResolutionInMinutes;
		for (int i = 0; i < intervals; i++) {
			odMatrix.put(i, new TreeMap<String, Integer>());
		}
	}

	/**
	 * Adds an entry to the OD matrix
	 * 
	 * @param timeInMillis
	 *            time in milliseconds
	 * @param origin
	 * @param destination
	 */
	public void addEntry(long timeInMillis, String origin, String destination) {
		int bucket = ((int) timeInMillis / (timeResolutionInMinutes * 60))
				% intervals;

		int timeInSecs = (int) (timeInMillis / 1000);
		TreeMap<String, Integer> odCount = odMatrix.get(bucket);
		String od = new String(String.format("%02d", (timeInSecs / 3600) % 24)
				+ ":" + String.format("%02d", (timeInSecs % 3600) / 60) + ":"
				+ String.format("%02d", timeInSecs % 60) + "|" + origin + "|"
				+ destination);

		if (odMatrix.get(bucket).size() == 0) {
			odCount.put(od, 1);
		} else {
			if (odMatrix.get(bucket).containsKey(od)) {
				int count = odMatrix.get(bucket).get(od);
				odMatrix.get(bucket).put(od, ++count);
			} else {
				odMatrix.get(bucket).put(od, 1);
			}
		}
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer("");

		for (Integer bucket : odMatrix.keySet()) {
			buffer.append("--------------------" + bucket
					+ "------------------------\n");
			TreeMap<String, Integer> odCount = odMatrix.get(bucket);
			for (String od : odCount.keySet()) {
				buffer.append(od + " " + odCount.get(od) + "\n");
			}

		}

		return buffer.toString();
	}

	/**
	 * Return as many OD matrices as the the number of intervals specified.
	 * 
	 * @return the odMatrix
	 */
	public Map<Integer, TreeMap<String, Integer>> getOdMatrix() {
		return odMatrix;
	}

}
