package main;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

/**
 * An LMAX disruptor instance.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public abstract class DisruptorFactory<T> {

	protected EventHandler<T> handler;
	protected Disruptor<T> disruptor;
	private RingBuffer<T> ringBuffer;
	protected final static int RING_SIZE = 65536;
	protected static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

	@SuppressWarnings("unchecked")
	public DisruptorFactory() {
		// Initialize the handler first
		implementLMAXHandler();
		disruptor.handleEventsWith(handler);
		ringBuffer = disruptor.start();
	}

	/**
	 * Implement the LMAX disruptor handler for processing
	 * {@link MessageInternal} tuples.
	 */
	public abstract void implementLMAXHandler();

	/**
	 * @return the ringBuffer
	 */
	public RingBuffer<T> getRingBuffer() {
		return ringBuffer;
	}

}
