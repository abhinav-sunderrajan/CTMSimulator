package viz;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import rnwmodel.RoadNode;
import utils.EarthFunctions;
import ctm.Cell;

/**
 * Quantum inventions road network viewer.
 * 
 * @author abhinav
 * 
 */
public class CTMSimViewer extends RoadNetworkVisualizer {

	private BufferedImage background = null;
	private int backgroundXOffset = -1;
	private int backgroundYOffset = -1;
	private Map<Cell, Color> cellColorMap;

	private static CTMSimViewer viewerInstance;

	private static final float S = 0.9f; // Saturation
	private static final float B = 0.9f; // Brightness
	private static final float MAX_VALUE = 1.0f;

	public static Color numberToColor(final double value) {
		if (value < 0) {
			return Color.getHSBColor(0.0f, S, B);
		} else if (value > MAX_VALUE) {
			return Color.getHSBColor(0.4f, S, B);
		} else {
			return Color.getHSBColor((float) (0.4 * value / MAX_VALUE), S, B);
		}

	}

	/**
	 * Only one viewer instance.
	 * 
	 * @param title
	 * @param model
	 * @param cellColorMap
	 * @param dbConnectionProperties
	 * @return
	 */
	public static CTMSimViewer getCTMViewerInstance(String title, RoadNetworkModel model,
			Map<Cell, Color> cellColorMap, Properties dbConnectionProperties) {

		if (viewerInstance == null) {
			viewerInstance = new CTMSimViewer(title, model, cellColorMap, dbConnectionProperties);
		}

		return viewerInstance;

	}

	private CTMSimViewer(String title, RoadNetworkModel model, Map<Cell, Color> cellColorMap,
			Properties dbConnectionProperties) {
		super(title, dbConnectionProperties, model);
		this.cellColorMap = cellColorMap;
	}

	@Override
	public void updateView() {

		if (model.getAllNodes().isEmpty())
			return;

		int pwidth = panel.getWidth();
		int pheight = panel.getHeight();

		image = new BufferedImage(pwidth, pheight, BufferedImage.TYPE_INT_RGB);

		Graphics g = image.getGraphics();
		Graphics2D g2 = (Graphics2D) g;
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, pwidth, pheight);

		synchronized (lock) {

			if (background != null) {
				g2.drawImage(background, backgroundXOffset, backgroundYOffset, null);
			}

			g2.setColor(Color.DARK_GRAY);
			for (Road link : visibleRoads) {

				String roadType = link.getRoadType();
				if (roadType.contains("EXPRESSWAY")) {
					g2.setStroke(new BasicStroke(4));
					// drawRoad(link, g2);
				} else if (roadType.contains("EXCHANGE") || roadType.contains("HIGHWAY")) {
					g2.setStroke(new BasicStroke(3));
				} else if (roadType.contains("MAJOR ROAD")) {
					g2.setStroke(new BasicStroke(2));
				} else {
					g2.setStroke(new BasicStroke(1));
				}

				drawRoad(link, g2);
			}

			for (Entry<Cell, Color> entry : cellColorMap.entrySet())
				drawBox(entry.getKey(), entry.getValue());

		}

		if (selectedRoad != null) {
			g2.setColor(Color.PINK);
			drawRoad(selectedRoad, g2);

		}

		synchronized (lock) {
			for (Road link : selectedRoads) {
				g2.setColor(Color.BLUE);
				g2.setStroke(new BasicStroke(4));
				drawRoad(link, g2);
			}
		}

	}

	public void drawBox(Cell cell, Color color) {

		Graphics g = image.getGraphics();
		Graphics2D g2d = (Graphics2D) g;

		String cellId = cell.getCellId();
		String split[] = cellId.split("_");
		int roadId = Integer.parseInt(split[0]);
		int segment = Integer.parseInt(split[1]);
		RoadNode segmentNode1 = model.getAllRoadsMap().get(roadId).getRoadNodes().get(segment);
		RoadNode segmentNode2 = model.getAllRoadsMap().get(roadId).getRoadNodes().get(segment + 1);
		g2d.setColor(color);

		int xo = panel.getWidth() / 2;
		int yo = panel.getHeight() / 2;

		int x0 = xo + (int) (zoom * (segmentNode1.getPosition().x - offset[0]));
		int y0 = yo + (int) (zoom * (-segmentNode1.getPosition().y - offset[1]));

		int x1 = xo + (int) (zoom * (segmentNode2.getPosition().x - offset[0]));
		int y1 = yo + (int) (zoom * (-segmentNode2.getPosition().y - offset[1]));

		double dx = x1 - x0;
		double dy = y1 - y0;

		Rectangle rectangle = new Rectangle(x0, y0, (int) Math.sqrt(dx * dx + dy * dy), 10);

		// Compute the bearing with respect to east, that is why -Math.PI/2.
		double alpha = Math.toRadians(EarthFunctions.bearing(segmentNode1.getPosition(),
				segmentNode2.getPosition())) - Math.PI / 2.0;

		AffineTransform transform = new AffineTransform();
		transform.rotate(alpha, x0, y0);

		Shape rotatedRect = transform.createTransformedShape(rectangle);
		g2d.setColor(color);
		g2d.fill(rotatedRect);
		g2d.setColor(Color.black);
		g2d.draw(rotatedRect);
	}
}
