package pie;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

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
import utils.EarthFunctions;

import com.vividsolutions.jts.geom.Coordinate;

public class PIESEMSimXMLGenerator {

	public static RoadNetworkModel roadModel;
	private static final int PIE_ROADS[] = { 30632, 30633, 30634, 30635, 30636, 30637,
			30638, 30639, 30640, 30641, 37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647,
			30648, 30649, 30650, 30651, 30580, 30581 };

	private static int reorder[] = { 28500, 31985, 31991, 28613, 29131, 28611, 29552, 29553, 28387,
			19116, 19117, 29005, 28597, 22009, 29152, 28594, 2355, 2356, 28595, 37982, 37980,
			37976, 30788, 30789, 30790, 28516, 28946, 28947, 28578, 38260, 29309, 29310, 28485, 82,
			28377 };

	private static Map<String, Long> laneRoadMapping = new LinkedHashMap<String, Long>();
	private static long roadIIDLong = 0;
	public static Map<String, List<Lane>> linkLaneMapping ;
	public static LaneModel laneModel;
	private static int newId = 83281;
	public static List<Road> pieChangi;
	private static List<Road> reorderList = new ArrayList<Road>();
	private static final int[] ramps = { 30790, 29131, 29005, 28947, 28377, 37980, 29553, 28594, 28595, 31991, 29310};
	
	static{
		roadModel = new QIRoadNetworkModel("jdbc:postgresql://172.25.187.111/abhinav", "abhinav",
				"qwert$$123", "qi_roads", "qi_nodes");

		int count = 0;
		for (Road road : roadModel.getAllRoadsMap().values()) {
			if (road.getPostalCode() != null)
				count++;
		}

		System.out.println("Total number of roads:" + roadModel.getAllRoadsMap().values().size()
				+ "\n Number of roads with postal codes:" + count);

		pieChangi = new ArrayList<>();
		for (int roadId : PIE_ROADS) {
			Road road = roadModel.getAllRoadsMap().get(roadId);
			pieChangi.add(road);
		}

		for (int roadId : reorder) {
			Road road = roadModel.getAllRoadsMap().get(roadId);
			reorderList.add(road);
		}

		// Reorder roads as exists on the map
		while (reorderList.size() != 0)
			reOrderRoads(pieChangi);

		// Need to do something about ramp roads add a small segment at the
		// end.

		for (int rampId : ramps) {
			for (Road road : pieChangi) {
				if (road.getRoadId() == rampId) {
					int size = road.getRoadNodes().size();
					RoadNode node1 = road.getRoadNodes().get(size - 2);
					RoadNode node2 = road.getRoadNodes().get(size - 1);
					double bearing = Math.toRadians(EarthFunctions.bearing(node1.getPosition(),
							node2.getPosition()));
					double length = road.getSegmentsLength()[size - 2];
					Coordinate pos = EarthFunctions.getPointAtDistanceAndBearing(
							node1.getPosition(), (length - 5), bearing);
					RoadNode newNode = new RoadNode(newId, pos.x, pos.y);
					road.getRoadNodes().add(size - 1, newNode);
					if (!roadModel.getAllNodes().containsKey(newId)) {
						roadModel.getAllNodes().put(newId, newNode);
						System.out.println("Created new node at length of "
								+ road.getSegmentsLength()[size - 1] + " for ramp" + rampId);
						roadModel.getAllRoadsMap().put(road.getRoadId(), road);
					}
					newId++;
					break;
				}
			}
		}

		// Set lane count for each road along P.I.E some what close to reality.

		try {
			BufferedReader	br = new BufferedReader(new FileReader(new File("Lanecount.txt")));
			while (br.ready()) {
				String line = br.readLine();
				String[] split = line.split("\t");
				Road road = roadModel.getAllRoadsMap().get(Integer.parseInt(split[0]));
				road.setLaneCount(Integer.parseInt(split[2]));
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		

		 laneModel = new LaneModel(roadModel);
		linkLaneMapping= laneModel.createLaneModel(pieChangi);
		
	}
	

	public static void main(String[] args) throws IOException {

		
		// Create XML documents
		OutputFormat format = OutputFormat.createPrettyPrint();
		Document roadNetworkDOM = exportAsXMLDomRoadNetwork(linkLaneMapping);

		FileOutputStream fos = new FileOutputStream("PIE_ROADS.xml");
		XMLWriter writer = new XMLWriter(fos, format);
		writer.write(roadNetworkDOM);
		writer.flush();

		Document routingNetwork = exportAsXMLDOMRouting(pieChangi);
		fos = new FileOutputStream("PIE_ROUTING.xml");
		writer = new XMLWriter(fos, format);
		writer.write(routingNetwork);
		writer.flush();

		Document intersectionProbability = exportAsXMLDOMIntersectionProbability(pieChangi);
		fos = new FileOutputStream("PIE_ITINERARY.xml");
		writer = new XMLWriter(fos, format);
		writer.write(intersectionProbability);
		writer.flush();

		Document intersections = exportAsXMLRampMeters();
		fos = new FileOutputStream("PIE_INTERSECTION.xml");
		writer = new XMLWriter(fos, format);
		writer.write(intersections);
		writer.flush();

		// boolean isValid = validateAgainstXSD(new
		// FileInputStream("PIE_ROADS.xml"),
		// new FileInputStream("semsim_roads_v0_9.xsd"));
		//
		// System.out.println(isValid);

	}

	private static Document exportAsXMLDOMIntersectionProbability(List<Road> pieChangiOrdered) {
		Document document = DocumentHelper.createDocument();
		Element root = document.addElement("TrafficGenerator");
		Element intersection = root.addElement("intersection");

		for (Road road : pieChangiOrdered) {
			RoadNode beginNode = road.getBeginNode();
			RoadNode endNode = road.getEndNode();
			List<Road> ins = new ArrayList<>();
			for (Road inRoad : beginNode.getInRoads()) {
				if (pieChangiOrdered.contains(inRoad) && !inRoad.equals(road))
					ins.add(inRoad);
			}

			List<Road> outs = new ArrayList<>();
			for (Road outRoad : endNode.getOutRoads()) {
				if (pieChangiOrdered.contains(outRoad))
					outs.add(outRoad);
			}

			if (ins.size() == 0) {
				Element source = root.addElement("source");
				source.addElement("iid").addText(
						String.valueOf(laneRoadMapping.get(road.getRoadId() + "_" + 1)));
				System.out.println(laneRoadMapping.get(road.getRoadId() + "_" + 1) + "\t"
						+ road.getRoadId());

				if (road.getRoadClass() == 0) {
					source.addElement("interArrivalTime").addText("1.5");
				} else {
					source.addElement("interArrivalTime").addText("3.0");
				}
			}

			if (outs.size() > 1) {
				Element subsection = intersection.addElement("subsection");
				subsection.addElement("sourceNode").addText(String.valueOf(endNode.getNodeId()));
				double x = 0.25;

				for (Road outRoad : outs) {

					Element destinationNode = subsection.addElement("destinationNode");
					RoadNode outNode = outRoad.getRoadNodes().get(1);
					destinationNode.addElement("iid").addText(String.valueOf(outNode.getNodeId()));
					if (outRoad.getKind().equalsIgnoreCase("Slip Road")
							|| outRoad.getKind().equalsIgnoreCase("Interchange")) {
						destinationNode.addElement("probability").addText(x + "");
					} else {
						destinationNode.addElement("probability").addText((1 - x) + "");
					}

				}

			}

		}

		return document;
	}

	private static Document exportAsXMLDOMRouting(List<Road> pieChangiOrdered) {
		Document document = DocumentHelper.createDocument();
		Element root = document.addElement("RoutingNetwork");
		Set<RoadNode> roadNodes = new HashSet<>();

		for (Road road : pieChangiOrdered) {

			for (int index = 0; index < road.getRoadNodes().size(); index++) {
				RoadNode roadNode = road.getRoadNodes().get(index);
				if (roadNodes.contains(roadNode))
					continue;
				roadNodes.add(roadNode);

				Element nodeElement = root.addElement("node")
						.addAttribute("nodeIID", String.valueOf(roadNode.getNodeId()))
						.addAttribute("nodeID", String.valueOf(roadNode.getNodeId()));

				Element locationElement = nodeElement.addElement("location");
				locationElement.addElement("lon").addText(String.valueOf(roadNode.getX()));
				locationElement.addElement("lat").addText(String.valueOf(roadNode.getY()));

				List<Road> ins = new ArrayList<>();
				for (Road inRoad : roadNode.getInRoads()) {
					if (pieChangiOrdered.contains(inRoad) && inRoad.getRoadId() != road.getRoadId())
						ins.add(inRoad);
				}

				List<Road> outs = new ArrayList<>();

				for (Road outRoad : roadNode.getOutRoads()) {
					if (pieChangiOrdered.contains(outRoad)
							&& outRoad.getRoadId() != road.getRoadId())
						outs.add(outRoad);
				}

				if (index == 0) {

					Element incoming = nodeElement.addElement("incoming");
					Element outgoing = nodeElement.addElement("outgoing");

					for (Road inRoad : ins) {
						incoming.addElement("edgeIID").addText(
								String.valueOf(laneRoadMapping.get(inRoad.getRoadId() + "_"
										+ (inRoad.getRoadNodes().size() - 1))));
					}

					for (Road outRoad : outs) {

						outgoing.addElement("edgeIID").addText(
								String.valueOf(laneRoadMapping.get(outRoad.getRoadId() + "_" + 1)));
					}

					outgoing.addElement("edgeIID").addText(
							String.valueOf(laneRoadMapping.get(road.getRoadId() + "_" + 1)));

				} else if (index == road.getRoadNodes().size() - 1) {

					Element incoming = nodeElement.addElement("incoming");
					Element outgoing = nodeElement.addElement("outgoing");

					incoming.addElement("edgeIID").addText(
							String.valueOf(laneRoadMapping.get(road.getRoadId() + "_"
									+ (road.getRoadNodes().size() - 1))));
					for (Road inRoad : ins) {
						incoming.addElement("edgeIID").addText(
								String.valueOf(laneRoadMapping.get(inRoad.getRoadId() + "_"
										+ (inRoad.getRoadNodes().size() - 1))));
					}

					for (Road outRoad : outs) {

						outgoing.addElement("edgeIID").addText(
								String.valueOf(laneRoadMapping.get(outRoad.getRoadId() + "_" + 1)));
					}
				} else {
					nodeElement
							.addElement("incoming")
							.addElement("edgeIID")
							.addText(
									String.valueOf(laneRoadMapping.get(road.getRoadId() + "_"
											+ index)));

					nodeElement
							.addElement("outgoing")
							.addElement("edgeIID")
							.addText(
									String.valueOf(laneRoadMapping.get(road.getRoadId() + "_"
											+ (index + 1))));
				}

				nodeElement.addElement("reach").addText("0");
				Element landMark = nodeElement.addElement("landmarks");
				landMark.addElement("toDistances");
				landMark.addElement("fromDistances");
			}

		}

		for (Road road : pieChangiOrdered) {
			for (int i = 0; i < road.getRoadNodes().size() - 1; i++) {

				RoadNode roadNode = road.getRoadNodes().get(i);
				RoadNode roadNodeNext = road.getRoadNodes().get(i + 1);
				Element edgeElement = root
						.addElement("edge")
						.addAttribute("roadLinkID", String.valueOf(road.getRoadId()))
						.addAttribute(
								"roadLinkIID",
								String.valueOf(laneRoadMapping.get(road.getRoadId() + "_" + (i + 1))));
				edgeElement.addElement("fromNodeIID").addText(String.valueOf(roadNode.getNodeId()));
				edgeElement.addElement("toNodeIID").addText(
						String.valueOf(roadNodeNext.getNodeId()));
				edgeElement.addElement("weight").addText(
						String.valueOf(road.getSegmentsLength()[i]));
				edgeElement.addElement("length").addText(
						String.valueOf(road.getSegmentsLength()[i]));
				edgeElement.addElement("functionalClass").addText(
						String.valueOf(road.getRoadClass()));

			}
		}

		return document;
	}

	public static Document exportAsXMLDomRoadNetwork(Map<String, List<Lane>> linkLaneMapping) {
		Document document = DocumentHelper.createDocument();
		Element root = document.addElement("RoadNetwork");
		root.addAttribute("version", "1.0");
		root.addAttribute("xsi:schemaLocation", "http://xenon.tum-create.edu.sg SEMSim_Data.xsd");
		root.add(new Namespace("xenon", "http://xenon.tum-create.edu.sg"));
		root.addAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");

		for (String roadIID : linkLaneMapping.keySet()) {
			List<Lane> lanes = linkLaneMapping.get(roadIID);
			for (Lane lane : lanes) {
				long roadLinkIId;
				if (laneRoadMapping.containsKey(lane.roadLinkIID)) {
					roadLinkIId = laneRoadMapping.get(lane.roadLinkIID);
				} else {
					roadLinkIId = ++roadIIDLong;
					laneRoadMapping.put(lane.roadLinkIID, roadLinkIId);
				}

				Element laneElement = root
						.addElement("lane")
						.addAttribute("laneID", String.valueOf(lane.id))
						.addAttribute("roadLinkIID", String.valueOf(roadLinkIId))
						.addAttribute("roadLinkID", String.valueOf(lane.associatedRoad.getRoadId()))
						.addAttribute("isConnectingLane", String.valueOf(lane.isConnectingLane));
				Element fromPoint = laneElement.addElement("fromPoint");
				fromPoint.addElement("lon").addText(String.valueOf(lane.fNode.getX()));
				fromPoint.addElement("lat").addText(String.valueOf(lane.fNode.getY()));

				Element toPoint = laneElement.addElement("toPoint");
				toPoint.addElement("lon").addText(String.valueOf(lane.tNode.getX()));
				toPoint.addElement("lat").addText(String.valueOf(lane.tNode.getY()));

				Element speedRange = laneElement.addElement("speedRange");
				speedRange.addElement("lowerLimit").addText(
						String.valueOf(lane.associatedRoad.getSpeedLimit()[0] * 5.00 / 18));
				speedRange.addElement("upperLimit").addText(
						String.valueOf(lane.associatedRoad.getSpeedLimit()[1] * 5.00 / 18));

				Element nextLanes = laneElement.addElement("nextLanes");
				for (Integer nextLaneId : lane.nextLaneIds)
					nextLanes.addElement("laneID").addText(String.valueOf(nextLaneId));
				laneElement.addElement("rightLaneID").addText(String.valueOf(lane.rightLaneId));
				laneElement.addElement("leftLaneID").addText(String.valueOf(lane.leftLaneId));
				Element tagElement = laneElement.addElement("tag");
				if (lane.associatedRoad.getPostalCode() != null)
					tagElement.addElement("pc").addText(lane.associatedRoad.getPostalCode());
			}

		}
		return document;

	}

	private static Document exportAsXMLRampMeters() {
		double[] queuePercentages = { 0.0, 0.0, 0.0, 0.13, 0.37, 0.72, 0.38, 0.39, 0.24, 0.0, 0.0 };

		Document document = DocumentHelper.createDocument();
		Element root = document.addElement("Intersections");
		root.addAttribute("version", "1.0");
		root.addAttribute("xsi:schemaLocation",
				"http://xenon.tum-create.edu.sg SEMSim_Intersections.xsd");
		root.add(new Namespace("xenon", "http://xenon.tum-create.edu.sg"));
		root.addAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");

		int index = 0;
		for (int roadId : ramps) {
			Road road = roadModel.getAllRoadsMap().get(roadId);
			Element intersection = root.addElement("Intersections");
			intersection.addAttribute("type", "SRampMeter");
			intersection.addAttribute("id", String.valueOf(roadId));
			intersection.addAttribute("queuePercentage", String.valueOf(queuePercentages[index]));
			Element phase = intersection.addElement("Phase");
			phase.addAttribute("duration", String.valueOf(45000));
			Element link = phase.addElement("link");
			link.addText(String.valueOf(laneRoadMapping.get(roadId + "_"
					+ (road.getRoadNodes().size() - 1))));
			Element rampLinks = intersection.addElement("RampLinks");

			StringBuffer buffer = new StringBuffer("");
			for (int i = 0; i < road.getRoadNodes().size() - 2; i++) {
				if (i < road.getRoadNodes().size() - 3)
					buffer.append(laneRoadMapping.get(roadId + "_" + (i + 1)) + ",");
				else
					buffer.append(laneRoadMapping.get(roadId + "_" + (i + 1)));
			}
			rampLinks.addText(buffer.toString());
			index++;
		}
		return document;

	}

	/**
	 * Reorder the unordered roads as exist on the map.
	 * 
	 * @param pieChangiOrdered
	 */
	public static void reOrderRoads(List<Road> pieChangiOrdered) {

		Iterator<Road> reIt = reorderList.iterator();

		while (reIt.hasNext()) {
			int index = -1;
			Road reshuffleRoad = reIt.next();

			for (Road road : pieChangiOrdered) {
				if (road.getBeginNode().getInRoads().contains(reshuffleRoad)
						|| road.getBeginNode().getOutRoads().contains(reshuffleRoad)) {
					index = pieChangiOrdered.indexOf(road);
					reIt.remove();
					break;
				}

				if (road.getEndNode().getInRoads().contains(reshuffleRoad)
						|| road.getEndNode().getOutRoads().contains(reshuffleRoad)) {
					index = pieChangiOrdered.indexOf(road) + 1;
					reIt.remove();
					break;
				}

			}

			if (index != -1) {
				pieChangiOrdered.add(index, reshuffleRoad);
			}

		}

	}

	static boolean validateAgainstXSD(InputStream xml, InputStream xsd) {
		try {
			SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = factory.newSchema(new StreamSource(xsd));
			Validator validator = schema.newValidator();
			validator.validate(new StreamSource(xml));
			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}

	}
}
