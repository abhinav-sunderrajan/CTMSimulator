package utils;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.accum.Max;
import org.nd4j.linalg.factory.Nd4j;

public class TBD {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		double a[] = { 1, 2, 3, 4, 5, 6, 7, 8 };
		INDArray arr = Nd4j.create(a);
		double max = Nd4j.getExecutioner().execAndReturn(new Max(arr)).getFinalResult()
				.doubleValue();
		System.out.println(max);
	}
}
