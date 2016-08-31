package utils;


import com.lmax.disruptor.EventFactory;
import com.mongodb.BasicDBObject;

public class BSONDataType extends BasicDBObject {

	private static final long serialVersionUID = 1L;
	public static final EventFactory<BSONDataType> EVENT_FACTORY = new EventFactory<BSONDataType>() {
		public BSONDataType newInstance() {
			return new BSONDataType();
		}
	};

}