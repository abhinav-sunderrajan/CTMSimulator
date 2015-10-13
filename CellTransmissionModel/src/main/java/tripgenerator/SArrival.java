package tripgenerator;

import java.util.ArrayList;
import java.util.Random;

/**
 * 
 * @author abhinav
 * 
 */
public class SArrival implements Comparable<SArrival> {
	// Time in seconds
	private long tArrival = -1;
	private String originPostalCode;
	private String destinationPostalCode;
	private Random random;

	/**
	 * A trip from origin to destination beginning at time tArrival
	 * 
	 * @param tArrival
	 *            time of the day in milliseconds with 00:00:00:000 as 0 ms
	 * @param origin
	 *            origin district
	 * @param destination
	 *            destination district
	 */
	public SArrival(long tArrival, String origin, String destination) {
		this.tArrival = tArrival;
		random = new Random();
		originPostalCode = getRandomPostalCodeInDistrict(origin);
		destinationPostalCode = getRandomPostalCodeInDistrict(destination);

	}

	@Override
	public String toString() {
		return originPostalCode + " to " + destinationPostalCode + " at "
				+ ((int) tArrival / 3600000) % 24 + ":"
				+ ((tArrival / 1000) % 3600) / 60;
	}

	@Override
	public int compareTo(SArrival o) {
		return (int) (this.tArrival - o.tArrival);
	}

	private String getRandomPostalCodeInDistrict(String district) {
		ArrayList<String> postalCodes = TripGenerator.postalCodesByDistrict
				.get(district);
		int index = random.nextInt(postalCodes.size());
		return postalCodes.get(index);
	}

	/**
	 * @return the originPostalCode
	 */
	public String getOriginPostalCode() {
		return originPostalCode;
	}

	/**
	 * @return the destinationPostalCode
	 */
	public String getDestinationPostalCode() {
		return destinationPostalCode;
	}

	/**
	 * @return the tArrival
	 */
	public long gettArrival() {
		return tArrival;
	}

}
