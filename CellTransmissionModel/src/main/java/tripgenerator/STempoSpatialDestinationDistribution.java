package tripgenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Spatio temporal traffic distribution for a district.
 * 
 * @author abhinav
 * 
 */
public class STempoSpatialDestinationDistribution {
	private String district = null;
	private double[] tdistribution = new double[24];
	private Map<String, Double>[] ddistribution = new Map[24];
	private StringBuffer buffer = new StringBuffer();

	/**
	 * Per district spatio-temporal traffic characteristics
	 * 
	 * @param district
	 *            the name of the district.
	 * @param content
	 *            content relating to the district for initializing the
	 *            spatio-temporal characteristics.
	 */
	public STempoSpatialDestinationDistribution(String district, String content) {
		this.district = district;

		String[] temp = content.split(";");
		int idx = 0;

		String cdistrict = temp[idx++];
		assert (cdistrict.equals(district));

		for (int i = 0; i < 24; ++i) {
			tdistribution[i] = Double.parseDouble(temp[idx++]);
			ddistribution[i] = new HashMap<String, Double>();

			int n = Integer.parseInt(temp[idx++]);
			for (int j = 0; j < n; ++j) {
				String[] temp2 = temp[idx++].split("=");
				double p = Double.parseDouble(temp2[1]);
				ddistribution[i].put(temp2[0], p);
			}
		}
	}

	public STempoSpatialDestinationDistribution(String district) {
		this.district = district;

		for (int i = 0; i < ddistribution.length; ++i) {
			ddistribution[i] = new HashMap<String, Double>();
		}
	}

	@Override
	public String toString() {
		buffer.append(district);
		for (int i = 0; i < 24; ++i) {
			buffer.append(";" + tdistribution[i] + ";" + ddistribution[i].size());

			for (String district : ddistribution[i].keySet()) {
				double p = ddistribution[i].get(district);
				buffer.append(";" + district + "=" + p);
			}
		}

		return buffer.toString();
	}

	public String district() {
		return district;
	}

	public void increaseTDistribution(int hour) {
		tdistribution[hour]++;
	}

	public void increaseDDistribution(int hour, String destination) {
		Double n = ddistribution[hour].get(destination);
		if (n == null)
			n = 1.0;
		else
			n++;

		ddistribution[hour].put(destination, n);
	}

	public double getTDistribution(int hour) {
		return tdistribution[hour];
	}

	public Map<String, Double> getDDistribution(int hour) {
		return ddistribution[hour];
	}

	public void normalise() {
		// normalise the tdistribution
		{
			double total = 0;
			for (double n : tdistribution) {
				total += n;
			}

			for (int i = 0; i < tdistribution.length; ++i) {
				tdistribution[i] /= total;
			}
		}

		// normalise the ddistributions
		for (Map<String, Double> ddist : ddistribution) {
			double total = 0;
			for (double n : ddist.values()) {
				total += n;
			}

			for (String key : ddist.keySet()) {
				double n = ddist.get(key);
				n /= total;
				ddist.put(key, n);
			}
		}
	}

}
