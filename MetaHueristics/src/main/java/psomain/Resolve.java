package psomain;

import org.la4j.vector.functor.VectorFunction;

/**
 * Implement this method to set the parameters within the sepecified range.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public abstract class Resolve implements VectorFunction {

	protected double min;
	protected double max;

	/**
	 * @return the min
	 */
	public double getMin() {
		return min;
	}

	/**
	 * @param min
	 *            the min to set
	 */
	public void setMin(double min) {
		this.min = min;
	}

	/**
	 * @return the max
	 */
	public double getMax() {
		return max;
	}

	/**
	 * @param max
	 *            the max to set
	 */
	public void setMax(double max) {
		this.max = max;
	}

}