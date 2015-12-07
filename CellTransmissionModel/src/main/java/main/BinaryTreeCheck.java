package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class BinaryTreeCheck {

	public static void main(String args[]) {
		List<Double> distances = new ArrayList<Double>();
		List<Double> speeds = new ArrayList<Double>();
		Random random = new Random(100);

		double speed1[] = { 18.37980, 19.14135, 20.45091, 20.98688, 19.45811, 19.21805, 19.49676,
				19.37183, 21.85703, 19.56528, 19.16722, 20.59768, 19.35093, 20.39549, 20.24915,
				19.63107, 18.75017, 20.53995, 19.69024, 18.28352, 18.72118, 20.97676, 21.39252,
				19.66107, 20.11097, 20.15912, 19.16882, 19.09954, 19.48266, 19.81139, 17.23615,
				20.27326, 20.60228, 21.26406, 20.93870, 19.93298, 18.93031, 20.50536, 17.71201,
				21.21556, 18.79566, 20.78555, 20.90001, 20.13228, 18.07863, 20.91417, 18.78125,
				21.85608, 19.09343, 18.58633, 19.93370, 19.03998, 19.86258, 20.04676, 19.94410,
				20.82649, 19.54057, 21.51675, 20.08619, 20.75776, 20.62379, 17.83392, 21.08070,
				20.25384, 21.41700, 21.46101, 19.51811, 21.03229, 20.40716, 19.05197, 18.91330,
				20.48172, 18.85398, 20.42609, 20.91082, 20.77693, 18.12077, 20.40886, 21.20087,
				19.14694, 19.60810, 18.39333, 20.70461, 19.71280, 19.54970, 18.70660, 19.36558,
				19.64955, 18.23497, 19.03794, 20.88373, 21.10662, 21.53972, 19.95299, 20.83647,
				19.57522, 19.21464, 20.62102, 21.22450, 20.98003 };

		double[] speed2 = { 13.732632, 20.979294, 17.349451, 9.077061, 8.470893, 23.697918,
				19.434429, 16.763399, 30.945012, 10.548965, 18.491723, 27.481064, 17.506922,
				8.324268, 30.695630, 20.268136, 14.663194, 13.058195, 19.023481, 5.168338,
				10.214615, 13.426660, 10.224501, 10.315752, 8.932095, 19.822270, 11.337754,
				1.058995, 8.003219, 20.580162, 6.833137, 25.602612, 23.070320, 28.560217,
				21.880743, 17.442611, 11.628226, 20.059080, 20.676388, 25.148347, 18.983421,
				19.215888, 31.278160, 12.679142, 16.977407, 9.325764, 9.500332, 27.039160,
				15.447060, 18.983525, 11.387193, 35.171360, 27.941614, 27.370283, 10.066422,
				21.865143, 18.638674, 28.649607, 30.283689, 21.697087, 25.675980, 19.336394,
				12.408375, 15.551091, 16.075122, 39.105265, 26.525452, 22.677994, 19.065914,
				18.598523, 23.093758, 33.196354, 20.856652, 37.285357, 20.257490, 18.598189,
				24.886148, 16.136635, 10.491471, 20.862693, 28.017278, 9.108932, 16.864145,
				41.324563, 16.741018, 26.709206, 24.705015, 11.371343, 15.551404, 21.625884,
				20.138274, 35.790644, 29.012130, 20.871006, 24.776689, 25.170125, 17.252413,
				23.310811, 8.951370, 3.551606 };

		double dist = 0.0;
		for (int i = 0; i < 200; i++) {
			dist += 1.0;
			distances.add(dist);
		}

		for (double speed : speed1) {
			speeds.add(speed);
		}
		for (double speed : speed2) {
			speeds.add(speed);
		}

		BinaryTree tree = new BinaryTree(speeds, distances);
		Set<BinaryTree> bestTree = tree.getAllLeaves();
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
			System.out.print(cut + ",");
		}
		System.out.println("");

	}
}