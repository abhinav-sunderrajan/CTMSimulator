package tripgenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

/**
 * Create trips representing the spatio-temporal characteristics of Singapore's
 * traffic based on HITS survey data 2008. The class is also used to create and
 * return OD matrices at different time intervals of choice.
 * 
 * @author abhinav
 * 
 */
public class TripGenerator {

	private Map<String, Integer> totalTrafficByDistrict;
	private List<SArrival> itineraries;
	private Map<String, STempoSpatialDestinationDistribution> destinationDistributions;
	private Random random = new Random();
	private int numberOfAgents;
	private int numberofHours;
	private int maxHour;
	private static TripGenerator tripgen;
	private static final int HOUR_IN_SECS = 3600;
	static Map<String, String> districtsByPostalCode = new HashMap<String, String>();
	static Map<String, ArrayList<String>> postalCodesByDistrict = new HashMap<String, ArrayList<String>>();

	private TripGenerator(int numOfAgents, String fileLocation, int minHour,
			int maxHour) throws IOException {
		numberOfAgents = numOfAgents;
		totalTrafficByDistrict = new HashMap<String, Integer>();
		destinationDistributions = new LinkedHashMap<String, STempoSpatialDestinationDistribution>();
		BufferedReader br = new BufferedReader(new FileReader(new File(
				fileLocation)));
		List<String> content = new LinkedList<String>();

		while (br.ready()) {
			content.add(br.readLine());
		}

		br.close();

		this.resumeFromCSVContent(content);
		createAgentPopulation(minHour, maxHour);

	}

	/**
	 * Get singleton trip generator instance.
	 * 
	 * @param numOfAgents
	 *            number of agents to be generated.
	 * @param fileLocation
	 *            file location of the trip generator.
	 * @param minHour
	 *            the min hour (inclusive) to generate agents
	 * @param maxHour
	 *            the max hour (exclusive) to generate agents
	 * @throws IOException
	 */
	public static TripGenerator getTripGeneratorInstance(int numOfAgents,
			String fileLocation, int minHour, int maxHour) throws IOException {
		if (tripgen == null) {
			tripgen = new TripGenerator(numOfAgents, fileLocation, minHour,
					maxHour);
		}
		return tripgen;

	}

	private void resumeFromCSVContent(List<String> content) {
		String line = content.get(0);
		String[] temp = line.split(",");
		int idx = 0;
		int n = Integer.parseInt(temp[idx++]);
		for (int i = 0; i < n; ++i) {
			String[] temp2 = temp[idx++].split("=");
			String postalCode = temp2[0];

			districtsByPostalCode.put(postalCode, temp2[1]);

			ArrayList<String> postalCodes = postalCodesByDistrict.get(temp2[1]);
			if (postalCodes == null) {
				postalCodes = new ArrayList<String>();
				postalCodesByDistrict.put(temp2[1], postalCodes);
			}
			postalCodes.add(postalCode);
		}

		line = content.get(1);
		temp = line.split(",");
		idx = 0;
		n = Integer.parseInt(temp[idx++]);
		for (int i = 0; i < n; ++i) {
			String[] temp2 = temp[idx++].split("=");
			int totalDistrictTraffic = Integer.parseInt(temp2[1]);
			totalTrafficByDistrict.put(temp2[0], totalDistrictTraffic);
		}

		for (int i = 2; i < content.size(); ++i) {
			line = content.get(i);
			String[] temp2 = line.split(",");
			String districtName = temp2[0];
			String districtDistribution = temp2[1];
			STempoSpatialDestinationDistribution distribution = new STempoSpatialDestinationDistribution(
					districtName, districtDistribution);
			destinationDistributions.put(districtName, distribution);
		}
	}

	/**
	 * Method for creating trips between two hours of a day based on HITS data.
	 * 
	 * @param minHour
	 * @param maxHour
	 * @return
	 */
	private void createAgentPopulation(int minHour, int maxHour) {
		numberofHours = maxHour - minHour;
		this.maxHour = maxHour;
		double scaleFactor = getScalefactor(minHour, maxHour);
		itineraries = new ArrayList<SArrival>();
		for (int i = minHour; i < maxHour; i++) {
			for (Entry<String, STempoSpatialDestinationDistribution> entry : destinationDistributions
					.entrySet()) {
				long timeOfTheDay = i * HOUR_IN_SECS;
				STempoSpatialDestinationDistribution tsdd = entry.getValue();
				String origin = entry.getKey();
				if (!this.totalTrafficByDistrict.containsKey(origin)) {
					continue;
				}

				int districtTraffic = (int) (this.totalTrafficByDistrict
						.get(origin) * scaleFactor);
				double tdist = tsdd.getTDistribution(i);
				if (tdist != 0) {
					// determine the number of arrivals during that hour
					double numOfArrivalsAtThisHour = districtTraffic * tdist;
					// determine the mean interarrival time and lambda
					double meanIAT = (double) HOUR_IN_SECS
							/ numOfArrivalsAtThisHour;
					double lambda = 1.0 / meanIAT;
					for (int j = 0; j < numOfArrivalsAtThisHour; j++) {
						// determine the next departure time
						double u = random.nextDouble();
						double iat = -Math.log(u) / lambda;
						timeOfTheDay = timeOfTheDay + (long) iat;
						/** < the time of the first move scheduled */

						// determine destination
						String destination = null;
						Map<String, Double> ddist = tsdd.getDDistribution(i);
						assert (ddist != null);
						double v = random.nextDouble();
						double m = 0.0;
						for (String dest : ddist.keySet()) {
							double p = ddist.get(dest);
							m += p;
							if (v < m) {
								destination = dest;
								break;
							}
						}
						assert (destination != null);
						itineraries.add(new SArrival(timeOfTheDay * 1000,
								origin, destination));

					}

				}
			}
		}

		Collections.sort(itineraries);
	}

	/**
	 * Retrieve a list of itineraries.
	 * 
	 * @return the arrivals
	 */
	public List<SArrival> getItineraries() {
		return itineraries;
	}

	private double getScalefactor(int minHour, int maxHour) {
		double sum = 0.0;
		for (int i = minHour; i < maxHour; i++) {
			for (Entry<String, STempoSpatialDestinationDistribution> entry : destinationDistributions
					.entrySet()) {
				STempoSpatialDestinationDistribution tsdd = entry.getValue();
				String origin = entry.getKey();
				if (!this.totalTrafficByDistrict.containsKey(origin)) {
					continue;
				}

				int districtTraffic = (int) (this.totalTrafficByDistrict
						.get(origin));
				double tdist = tsdd.getTDistribution(i);
				if (tdist != 0) {
					// determine the number of arrivals during that hour
					double numOfArrivalsAtThisHour = districtTraffic * tdist;
					sum += numOfArrivalsAtThisHour;
				}
			}
		}
		return numberOfAgents / sum;
	}

	/**
	 * Retrieve the OD matrix.
	 * 
	 * @param timeResolutionInMinutes
	 * @return
	 */
	public ODMatrix getODM(int timeResolutionInMinutes) {
		ODMatrix odm = new ODMatrix(timeResolutionInMinutes, numberofHours);
		for (SArrival arrival : itineraries) {
			if (arrival.gettArrival() / (HOUR_IN_SECS * 1000) > maxHour - 1) {
				continue;
			}
			String from = arrival.getOriginPostalCode().substring(0, 2);
			String to = arrival.getDestinationPostalCode().substring(0, 2);
			odm.addEntry(arrival.gettArrival(), from, to);
		}

		return odm;
	}

	/**
	 * Returns a concurrent queue of arrivals for the given simulation time.
	 * 
	 * @param time
	 * @return
	 */
	public Set<SArrival> nextArrivals(long time) {
		Set<SArrival> arrivals = new HashSet<SArrival>();
		Iterator<SArrival> iterator = itineraries.iterator();

		while (iterator.hasNext()) {
			SArrival arrival = iterator.next();
			if (arrival.gettArrival() > time) {
				break;
			}
			arrivals.add(arrival);
			iterator.remove();
		}

		return arrivals;
	}
}
