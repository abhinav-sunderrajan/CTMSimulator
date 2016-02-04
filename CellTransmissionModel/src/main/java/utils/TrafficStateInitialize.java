package utils;

import java.util.Iterator;

import main.SimulatorCore;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

import rnwmodel.Road;
import simulator.CellTransmissionModel;
import ctm.Cell;

/**
 * Initializes traffic state from an XML file.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class TrafficStateInitialize {

	public static Road[] pieMainRoads;

	/**
	 * Initialize traffic state
	 */
	static {
		String expresswayRoadList = "30634,30635,30636,30637,30638,30639,30640,30641,37981,30642,30643,38539,30644,30645,30646,30647,30648,30649,30650,30651,30580,30581";
		String roadArr[] = expresswayRoadList.split(",");
		pieMainRoads = new Road[roadArr.length];
		int i = 0;
		for (String roadStr : roadArr) {
			Integer roadId = Integer.parseInt(roadStr);
			pieMainRoads[i] = SimulatorCore.roadNetwork.getAllRoadsMap().get(roadId);
			i++;
		}

	}

	public static void parseXML() {
		try {
			Document document = SimulatorCore.SAX_READER.read("road_state.xml");
			Element trafficState = document.getRootElement().element("TrafficState");

			int roadIndex = 0;
			double distance = 0;
			int segmentIndex = 0;

			for (Iterator iter = trafficState.elementIterator("zone"); iter.hasNext();) {
				Element zone = (Element) iter.next();
				double from = Double.parseDouble(zone.attributeValue("from"));
				double to = Double.parseDouble(zone.attributeValue("to"));

				Element speedElement = zone.element("speed");
				Element densityElement = zone.element("density");

				double meanSpeed = Double.parseDouble(speedElement.attributeValue("mean"));
				double sdSpeed = Double.parseDouble(speedElement.attributeValue("sd"));
				int upperDensity = Integer.parseInt(densityElement.attributeValue("upper"));
				int lowerDensity = Integer.parseInt(densityElement.attributeValue("lower"));

				while (true) {
					double[] segmentLengths = pieMainRoads[roadIndex].getSegmentsLength();
					boolean breakpremature = false;

					for (int s = segmentIndex; s < segmentLengths.length; s++) {
						if (distance > to) {
							segmentIndex = s;
							breakpremature = true;
							break;
						} else {
							Cell cell = CellTransmissionModel.cellNetwork.getCellMap().get(
									pieMainRoads[roadIndex] + "_" + s);
							cell.setMeanSpeed(meanSpeed);
							cell.setSdSpeed(sdSpeed);
							int density = SimulatorCore.random
									.nextInt((upperDensity - lowerDensity) + 1) + lowerDensity;
							cell.setNumberOfvehicles((int) Math.round(density * cell.getLength()
									* 0.001));

							System.out.println(cell.getClass().getSimpleName() + "- id:"
									+ cell.getCellId() + " nt:" + cell.getNumOfVehicles()
									+ " mean-speed:" + cell.getMeanSpeed());
							if (cell.getnMax() < cell.getNumOfVehicles())
								System.err.println("Cell ID:" + cell.getCellId() + " nmax:"
										+ cell.getnMax() + " nt:" + cell.getNumOfVehicles());
						}
						distance += segmentLengths[s];
					}
					if (breakpremature) {
						break;
					} else {
						if (distance < to) {
							segmentIndex = 0;
							roadIndex++;
							continue;
						} else {
							break;
						}
					}
				}

			}

			System.out
					.println("Finished trafffic state intialization for all roads on P.I.E changi central");
		} catch (DocumentException e) {
			e.printStackTrace();
		}

	}
}
