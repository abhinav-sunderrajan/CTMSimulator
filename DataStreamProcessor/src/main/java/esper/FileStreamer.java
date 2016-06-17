package esper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;

import main.DisruptorFactory;
import main.MiddlewareCore;
import rnwmodel.Road;
import roadsection.PIESEMSimXMLGenerator;
import beans.BeanPak;
import beans.CellStateBean;

public class FileStreamer {

	private BufferedReader reader;
	private DisruptorFactory<? extends BeanPak> disruptorInstance;

	public FileStreamer(String directoryPath, DisruptorFactory<? extends BeanPak> disruptorInstance)
			throws FileNotFoundException {
		File dir = new File(directoryPath);
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".csv");
			}
		});
		reader = new BufferedReader(new FileReader(files[0]));
		this.disruptorInstance = disruptorInstance;
	}

	public void startSEMSimStream() throws InterruptedException {
		try {
			int prevTime = 1;
			while (reader.ready()) {
				String line = reader.readLine();
				String[] split = line.split(",");

				if (split[1].contains(".") || split[2].equals("-1")) {
					continue;
				}

				int time = (int) (Long.parseLong(split[0]) / 1000);
				if (time > 7200)
					break;
				String roadSegment = MiddlewareCore.getRoadIIDMapping().get(
						Long.parseLong(split[2]));
				String[] roadSegmentSplit = roadSegment.split("_");
				Integer roadId = Integer.parseInt(roadSegmentSplit[0]);
				Road road = PIESEMSimXMLGenerator.roadModel.getAllRoadsMap().get(roadId);
				Integer segment = Integer.parseInt(roadSegmentSplit[1]);

				if ((time - prevTime) > 0) {
					Thread.sleep((time - prevTime) * 1000);
					prevTime = time;
				}

				long sequence = disruptorInstance.getRingBuffer().next();
				CellStateBean bean = (CellStateBean) disruptorInstance.getRingBuffer()
						.get(sequence);
				bean.setCellId(road + "_" + segment);
				bean.setTime(time);
				bean.setSpeed(Double.parseDouble(split[5]));
				disruptorInstance.getRingBuffer().publish(sequence);
			}
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}

	}
}
