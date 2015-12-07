package main;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import rnwmodel.QIRoadNetworkModel;
import rnwmodel.Road;
import rnwmodel.RoadNetworkModel;
import utils.DatabaseAccess;

public class RecursiveBinarySplittingPIE {

	int itersHeavy[] = { 3, 6, 9, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45 };
	private static Properties dbConnectionProperties;
	private static RoadNetworkModel roadNetwork;
	private static DatabaseAccess access;
	private static final int NUM_OF_FOLDS = 5;
	private static final int PIE_ROADS[] = { 30634, 30635, 30636, 38541, 30637, 30638, 30639,
			30640, 30641, 37981, 30642, 30643, 38539, 30644, 30645, 30646, 30647, 30648, 30649,
			30650, 30651, 30580, 30581 };
	private static final double[] ALPHA_ARR = { 1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 3.5 };

	// K-Fold cross validation.
	private static ArrayList<Double>[] distancesArray = (ArrayList<Double>[]) new ArrayList[NUM_OF_FOLDS];
	private static ArrayList<Double>[] speedsArray = (ArrayList<Double>[]) new ArrayList[NUM_OF_FOLDS];

	private static final DecimalFormat df = new DecimalFormat("#.###");

	public static void main(String args[]) throws FileNotFoundException, IOException, SQLException {

		df.setRoundingMode(RoundingMode.CEILING);
		dbConnectionProperties = new Properties();
		dbConnectionProperties
				.load(new FileInputStream("src/main/resources/connection.properties"));
		roadNetwork = new QIRoadNetworkModel(dbConnectionProperties, "qi_roads", "qi_nodes");
		access = new DatabaseAccess(dbConnectionProperties);

		int index = 0;

		for (int time_stamp = 900; time_stamp < 1200; time_stamp = time_stamp + 60) {

			String query = "SELECT speed,distance_along_road from semsim_output WHERE iteration_count="
					+ "47 AND time_stamp >="
					+ time_stamp
					+ " AND time_stamp <"
					+ (time_stamp + 60)
					+ " AND distance_along_road<=13000 ORDER BY distance_along_road";
			ResultSet rs = access.retrieveQueryResult(query);
			List<Double> distances = new ArrayList<>();
			List<Double> speeds = new ArrayList<>();
			while (rs.next()) {
				distances.add(rs.getDouble("distance_along_road"));
				speeds.add(rs.getDouble("speed"));
			}
			distancesArray[index] = (ArrayList<Double>) distances;
			speedsArray[index] = (ArrayList<Double>) speeds;
			index++;
		}

		System.out
				.println("Finished loading five continous time intervals of speeds and distances");

		// double[] bestAlphasCV = crossValidation();
		//
		// double bestAlpha = 0.0;
		// for (double alpha : bestAlphasCV)
		// bestAlpha += alpha;
		// bestAlpha = bestAlpha / bestAlphasCV.length;
		// System.out.println("Determined the best alphas after cross validation as "
		// + bestAlpha);

		// After having determined the alpha now fit and prune the best tree.

		List<Double> distances = new ArrayList<>();
		List<Double> speeds = new ArrayList<>();

		for (index = 0; index < distancesArray.length; index++) {
			distances.addAll(distancesArray[index]);
			speeds.addAll(speedsArray[index]);
		}

		BinaryTree tree = new BinaryTree(speeds, distances);
		tree.setAlpha(1.0);
		Set<BinaryTree> bestTree = new HashSet<>();
		bestTree.addAll(tree.getAllLeaves());
		double costTree = tree.fullTreeCost();
		for (int level = 14; level > 4; level--) {
			Set<BinaryTree> subTreeLeaves = tree.getSubTreeLeavesAtLevel(level);
			System.out.println(subTreeLeaves.size());
			double subTreeCost = tree.subTreeCost(level);
			if (subTreeCost < costTree) {
				bestTree.clear();
				bestTree.addAll(subTreeLeaves);
			}
			BinaryTree.subTreeLeaves.clear();
		}

		double[] cuts = new double[bestTree.size() - 1];
		int k = 0;
		for (BinaryTree leaf : bestTree) {
			if (k == bestTree.size() - 1)
				break;
			else {
				cuts[k] = leaf.getBounds()[1];
				k++;
			}
		}

		Arrays.sort(cuts);

		System.out.println("Best cuts after binary splitting and cross validation.");
		for (double cut : cuts) {
			System.out.print(df.format(cut) + ",");
		}
		System.out.println("");

		int koo[] = {};

	}

	private static double[] crossValidation() {

		double[] bestAlphas = new double[5];

		for (int validationIndex = 0; validationIndex < 5; validationIndex++) {
			List<Double> distancesTrain = new ArrayList<>();
			List<Double> speedsTrain = new ArrayList<>();
			List<Double> distancesValidation = new ArrayList<>();
			List<Double> speedsValidation = new ArrayList<>();

			// Create the training and validation sets for the cross validation.
			for (int index = 0; index < distancesArray.length; index++) {

				if (validationIndex == index) {
					distancesValidation.addAll(distancesArray[index]);
					speedsValidation.addAll(speedsArray[index]);
				} else {
					distancesTrain.addAll(distancesArray[index]);
					speedsTrain.addAll(speedsArray[index]);
				}
			}

			double[] cuts100 = new double[130];
			int k = 0;
			for (int cellLen = 100; cellLen < 13100; cellLen += 100) {
				cuts100[k] = cellLen;
				k++;
			}

			// Simply dividing cells into length 100m

			double mseCuts100 = mseCuts(cuts100, distancesTrain, speedsTrain);
			System.out.println("MSE 100 meters split:" + mseCuts100);

			// Divide into natural segments based on P.I.E topology.
			double cutsPIE[] = new double[PIE_ROADS.length];
			double sum = 0.0;
			for (int i = 0; i < PIE_ROADS.length; i++) {
				Road road = roadNetwork.getAllRoadsMap().get(PIE_ROADS[i]);
				sum += road.getWeight();
				cutsPIE[i] = sum;
			}

			double mseCutsPIE = mseCuts(cutsPIE, distancesTrain, speedsTrain);
			System.out.println("MSE P.I.E split:" + mseCutsPIE);

			// Binary tree

			BinaryTree tree = new BinaryTree(speedsTrain, distancesTrain);
			Set<BinaryTree> bestTree = new HashSet<>();
			bestTree.addAll(tree.getAllLeaves());

			double costTree = tree.fullTreeCost();
			System.out.println("Cost of full tree is:" + costTree + " number of leaves:"
					+ bestTree.size());
			double bestAlpha = 0.25;
			for (double alpha : ALPHA_ARR) {
				tree.setAlpha(alpha);
				for (int level = 5; level < 14; level++) {
					Set<BinaryTree> subTreeLeaves = tree.getSubTreeLeavesAtLevel(level);
					double subTreeCost = tree.subTreeCost(level);
					if (subTreeCost < costTree) {
						bestTree.clear();
						bestTree.addAll(subTreeLeaves);
						costTree = subTreeCost;
						bestAlpha = alpha;
					}
					BinaryTree.subTreeLeaves.clear();
				}

			}
			bestAlphas[validationIndex] = bestAlpha;
			double[] cutsTraining = new double[bestTree.size() - 1];
			k = 0;
			for (BinaryTree leaf : bestTree) {
				if (k == bestTree.size() - 1)
					break;
				else {
					cutsTraining[k] = leaf.getBounds()[1];
					k++;
				}
			}

			double mseCutsTree = mseCuts(cutsTraining, distancesValidation, speedsValidation);
			System.out.println("Best Alpha for this iteration is " + bestAlpha + " MSE is "
					+ mseCutsTree + " Number of leaves for the tree is:" + bestTree.size());

		}
		return bestAlphas;

	}

	private static double mseCuts(double[] cuts, List<Double> distances, List<Double> speeds) {

		double totalMSE = 0.0;
		int k = 0;
		List<Double> speedsList = new ArrayList<>();
		for (int i = 0; i < distances.size(); i++) {
			if (distances.get(i) < cuts[k]) {
				speedsList.add(speeds.get(i));

			} else {
				totalMSE += BinaryTree.evaluateRMS(speedsList);
				k++;
				speedsList.clear();
				speedsList.add(speeds.get(i));
				continue;
			}

		}

		totalMSE += BinaryTree.evaluateRMS(speedsList);
		return totalMSE;
	}
}
