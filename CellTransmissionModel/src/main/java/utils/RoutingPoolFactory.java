package utils;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import rnwmodel.RoadNetworkModel;
import utils.RoutingDjikstra;

public class RoutingPoolFactory extends BasePooledObjectFactory<RoutingDjikstra> {

	private RoadNetworkModel rnwModel;

	public RoutingPoolFactory(RoadNetworkModel rnwModel) {
		this.rnwModel = rnwModel;

	}

	@Override
	public RoutingDjikstra create() throws Exception {
		return new RoutingDjikstra(rnwModel);
	}

	@Override
	public PooledObject<RoutingDjikstra> wrap(RoutingDjikstra obj) {
		return new DefaultPooledObject<RoutingDjikstra>(obj);
	}

	@Override
	public void passivateObject(PooledObject<RoutingDjikstra> pooledObject) {
		pooledObject.getObject().reset();
	}

}
