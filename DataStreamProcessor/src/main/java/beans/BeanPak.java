package beans;

import org.msgpack.annotation.Message;

import com.lmax.disruptor.EventFactory;

@Message
public class BeanPak {

	public static EventFactory<? extends BeanPak> EVENT_FACTORY;
}
