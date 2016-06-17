package roadsection;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import rnwmodel.Lane;
import rnwmodel.LaneModel;
import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;

/**
 * Generate
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class UpperSgoonXMLGen {

	private static RoadNetworkModel roadModel;
	private static Map<String, List<Lane>> linkLaneMapping;
	private static LaneModel laneModel;

	private static final int INTERSECTIONS[] = { 33354, 33409, 28922, 28914, 33361, 33403, 3406,
			16790, 33367, 33399, 17978, 15752 };

	private static final int UPPER_SGOON[] = { 33353, 43262, 28916, 28919, 28920, 28921, 33354,
			28922, 33409, 33410, 33411, 28913, 28912, 28923, 28924, 28925, 33356, 33408, 33407,
			33357, 33358, 33406, 33359, 33405, 16791, 33361, 16790, 33360, 33404, 16789, 16788,
			3408, 3409, 3407, 3406, 3405, 16792, 3404, 33362, 33363, 33401, 33364, 33400, 33365,
			33366, 17978, 15752, 33367, 33399, 15744, 43851, 15753, 17977, 15754, 33398, 33368,
			33369, 1784, 33370, 33371, 16793, 3403, 33403, 33402, 28915, 33355, 28914, 15755, 1783,
			17976, 16787, 3410, 16794, 3402, 16795, 3401 };

	static {
		roadModel = new QIRoadNetworkModel("jdbc:postgresql://172.25.187.111/abhinav", "abhinav",
				"qwert$$123", "qi_roads", "qi_nodes");
	}

	public static void main(String args[]) throws IOException {
		List<Road> upperSgoon = new ArrayList<Road>();
		List<Road> intersections = new ArrayList<Road>();

		for (int roadId : UPPER_SGOON) {
			Road road = roadModel.getAllRoadsMap().get(roadId);
			road.setLaneCount(2);
			upperSgoon.add(road);
		}
		for (int roadId : INTERSECTIONS) {
			Road road = roadModel.getAllRoadsMap().get(roadId);
			intersections.add(road);
		}

		laneModel = new LaneModel(roadModel);
		linkLaneMapping = laneModel.createLaneModel(upperSgoon, LaneModel.RoadTypes.URBAN);
		System.out.println("Created a lane model..");

		// Create XML documents
		OutputFormat format = OutputFormat.createPrettyPrint();
		Document roadNetworkDOM = PIESEMSimXMLGenerator.exportAsXMLDomRoadNetwork(linkLaneMapping);

		FileOutputStream fos = new FileOutputStream("UPPERSGOON_ROADS.xml");
		XMLWriter writer = new XMLWriter(fos, format);
		writer.write(roadNetworkDOM);
		writer.flush();

		Document routingNetwork = PIESEMSimXMLGenerator.exportAsXMLDOMRouting(upperSgoon);
		fos = new FileOutputStream("UPPERSGOON_ROUTING.xml");
		writer = new XMLWriter(fos, format);
		writer.write(routingNetwork);
		writer.flush();

		Document intersectionProbability = exportAsXMLDOMIntersectionProbability(upperSgoon);
		fos = new FileOutputStream("UPPERSGOON_ITINERARY.xml");
		writer = new XMLWriter(fos, format);
		writer.write(intersectionProbability);
		writer.flush();

		Document trafficLights = exportAsXMLDOMIntersections(upperSgoon);
		fos = new FileOutputStream("UPPERSGOON_INTERSECTION.xml");
		writer = new XMLWriter(fos, format);
		writer.write(trafficLights);
		writer.flush();

	}

	private static Document exportAsXMLDOMIntersections(List<Road> upperSgoon) {

		Document document = DocumentHelper.createDocument();
		Element root = document.addElement("Intersections");
		root.addAttribute("version", "1.0");
		root.addAttribute("xsi:schemaLocation",
				"http://xenon.tum-create.edu.sg SEMSim_Intersections.xsd");
		root.add(new Namespace("xenon", "http://xenon.tum-create.edu.sg"));
		root.addAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		int linkId = 0;
		for (int intersection = 0; intersection < 3; intersection++) {

			Element intersectionElem = root.addElement("Intersections");
			intersectionElem.addAttribute("type", "SStaticIntersection");
			intersectionElem.addAttribute("id", String.valueOf(intersection));

			for (int phase = 0; phase < 2; phase++) {
				Element phaseElement = intersectionElem.addElement("Phase");
				phaseElement.addAttribute("duration", String.valueOf(40000));
				phaseElement.addAttribute("order", String.valueOf(phase));
				do {
					Element link = phaseElement.addElement("link");
					link.addText(String.valueOf(PIESEMSimXMLGenerator.laneRoadMapping
							.get(INTERSECTIONS[linkId] + "_1")));
					linkId++;
				} while (linkId % 2 != 0);
			}

		}

		return document;

	}

	private static Document exportAsXMLDOMIntersectionProbability(List<Road> upperSgoon) {
		Document document = DocumentHelper.createDocument();
		Element root = document.addElement("TrafficGenerator");

		for (Road road : upperSgoon) {
			RoadNode beginNode = road.getBeginNode();
			RoadNode endNode = road.getEndNode();
			List<Road> ins = new ArrayList<>();
			for (Road inRoad : beginNode.getInRoads()) {
				if (upperSgoon.contains(inRoad) && !inRoad.equals(road))
					ins.add(inRoad);
			}

			List<Road> outs = new ArrayList<>();
			for (Road outRoad : endNode.getOutRoads()) {
				if (upperSgoon.contains(outRoad))
					outs.add(outRoad);
			}

			if (ins.size() == 0) {
				Element source = root.addElement("source");
				source.addElement("iid").addText(
						String.valueOf(PIESEMSimXMLGenerator.laneRoadMapping.get(road.getRoadId()
								+ "_" + 1)));
				System.out.println(PIESEMSimXMLGenerator.laneRoadMapping.get(road.getRoadId() + "_"
						+ 1)
						+ "\t" + road.getRoadId());

				if (road.getRoadClass() == 0) {
					source.addElement("interArrivalTime").addText("1.5");
				} else {
					source.addElement("interArrivalTime").addText("3.0");
				}
			}

		}

		return document;
	}

}
