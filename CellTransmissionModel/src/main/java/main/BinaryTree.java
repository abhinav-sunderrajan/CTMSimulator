package main;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BinaryTree {

	private static final double MIN_LENGTH = 3.0;
	private static int MIN_READINGS = 10;
	private int level;
	private BinaryTree[] children;

	private double[] bounds;
	private List<Double> speeds;
	private List<Double> distances;
	private double rmse;
	static Set<BinaryTree> allLeaves;
	static Set<BinaryTree> subTreeLeaves;
	private double alpha = 0.25;

	static {
		allLeaves = new HashSet<BinaryTree>();
		subTreeLeaves = new HashSet<BinaryTree>();
	}

	/**
	 * The root of the Binary Tree. Hence level is zero.
	 * 
	 * @param speeds
	 * @param distances
	 */
	BinaryTree(List<Double> speeds, List<Double> distances) {
		bounds = new double[2];
		bounds[1] = distances.get(distances.size() - 1);
		bounds[0] = 0;
		this.rmse = evaluateRMS(speeds);
		this.speeds = speeds;
		this.distances = distances;
		children = new BinaryTree[2];
		this.level = 0;
		if (bounds[1] - bounds[0] > MIN_LENGTH)
			split();
	}

	/**
	 * Children indicated by the level. Keep in mind that level of root node is
	 * 0.
	 * 
	 * @param level
	 *            the level starting from the root node which is at level 0.
	 * @param speeds
	 * @param distances
	 * @param bounds1
	 * @param bounds0
	 * @param rmse
	 */
	public BinaryTree(int level, List<Double> speeds, List<Double> distances, double bounds0,
			double bounds1, double rmse) {
		bounds = new double[2];
		bounds[1] = bounds1;
		bounds[0] = bounds0;
		this.rmse = rmse;
		this.level = level;
		this.speeds = speeds;
		this.distances = distances;
		children = new BinaryTree[2];
		if (bounds[1] - bounds[0] > MIN_LENGTH)
			split();

	}

	/**
	 * @return the speeds
	 */
	public List<Double> getSpeeds() {
		return speeds;
	}

	private void split() {
		double minVariance = rmse;
		double cut = 0.0;
		double min = bounds[0] + MIN_LENGTH;
		List<Double> speedChild1 = null;
		List<Double> distanceChild1 = null;
		double varianceChild1 = 0.0;

		List<Double> speedChild2 = null;
		List<Double> distanceChild2 = null;
		double varianceChild2 = 0.0;

		for (double bound = min; bound <= bounds[1] - MIN_LENGTH; bound += MIN_LENGTH) {
			List<Double> speeds1 = new ArrayList<>();
			List<Double> distances1 = new ArrayList<>();
			double var1 = 0.0;

			List<Double> speeds2 = new ArrayList<>();
			List<Double> distances2 = new ArrayList<>();
			double var2 = 0.0;

			for (int i = 0; i < distances.size(); i++) {
				if (distances.get(i) < bound) {
					speeds1.add(speeds.get(i));
					distances1.add(distances.get(i));
				} else {
					speeds2.add(speeds.get(i));
					distances2.add(distances.get(i));
				}
			}

			if (speeds1.size() < MIN_READINGS || speeds2.size() < MIN_READINGS) {
				continue;
			} else {
				var1 = evaluateRMS(speeds1);
				var2 = evaluateRMS(speeds2);
				if (var1 + var2 < minVariance) {
					minVariance = (var1 + var2);
					cut = bound;
					speedChild1 = speeds1;
					speedChild2 = speeds2;
					varianceChild1 = var1;
					varianceChild2 = var2;
					distanceChild1 = distances1;
					distanceChild2 = distances2;

				}
			}

		}

		if (cut == 0.0) {
			return;
		} else {

			// System.out.println("Cut at " + cut + " level " + level);
			children[0] = new BinaryTree(level + 1, speedChild1, distanceChild1, bounds[0], cut,
					varianceChild1);
			children[1] = new BinaryTree(level + 1, speedChild2, distanceChild2, cut, bounds[1],
					varianceChild2);
		}

	}

	/**
	 * Evaluates sum((xi-mu(xi))^2)
	 * 
	 * @param vars
	 * @return
	 */
	public static double evaluateRMS(List<Double> vars) {
		double average = 0.0;
		double rms = 0.0;
		int n = vars.size();
		for (double speed : vars)
			average += speed;

		average = average / n;
		for (double var : vars)
			rms += (var - average) * (var - average);

		return rms;

	}

	/**
	 * Return all leaves associated with the created QuadTree.
	 * 
	 * @return
	 */
	public Set<BinaryTree> getAllLeaves() {
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				children[i].getAllLeaves();
			} else {
				allLeaves.add(this);
			}
		}
		return allLeaves;
	}

	/**
	 * Returns the mean square error associated with all leaves in the tree.
	 * 
	 * @return
	 */
	public double fullTreeCost() {
		double totalMSE = 0.0;
		if (allLeaves.size() == 0) {
			getAllLeaves();
		}

		for (BinaryTree leaf : allLeaves) {
			totalMSE += leaf.rmse;
		}

		return totalMSE + alpha * allLeaves.size();
	}

	/**
	 * Prune based on cost complexity pruning.
	 * 
	 * @param alpha
	 */
	public double subTreeCost(int k) {

		double totalMSE = 0.0;
		for (BinaryTree leaf : subTreeLeaves) {
			totalMSE += leaf.rmse;
		}

		return totalMSE + alpha * subTreeLeaves.size();

	}

	/**
	 * Prune subtree based on the level of the tree. remember that root node
	 * starts at level 0.
	 * 
	 * @param k
	 * @return
	 */
	public Set<BinaryTree> getSubTreeLeavesAtLevel(int k) {
		for (int i = 0; i < children.length; i++) {
			if (children[i] == null) {
				subTreeLeaves.add(this);
			} else {
				if (children[i].level <= k)
					children[i].getSubTreeLeavesAtLevel(k);
				else
					subTreeLeaves.add(this);
			}
		}
		return subTreeLeaves;

	}

	/**
	 * @return the level
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * @return the bounds
	 */
	public double[] getBounds() {
		return bounds;
	}

	/**
	 * @return the alpha
	 */
	public double getAlpha() {
		return alpha;
	}

	/**
	 * @param alpha
	 *            the alpha to set
	 */
	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

}
