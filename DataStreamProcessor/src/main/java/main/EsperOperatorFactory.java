package main;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;

import esper.Subscriber;

/**
 * An esper engine instance factory.
 * 
 * @author abhinav.sunderrajan
 * 
 */
public class EsperOperatorFactory {

	private EPServiceProvider cep;
	private EPAdministrator cepAdm;
	private Configuration cepConfig;
	private EPRuntime cepRT;
	private EPStatement cepStatement;

	/**
	 * An instance of the esper engine.
	 * 
	 * @param esperEngineName
	 */
	public EsperOperatorFactory(String esperEngineName) {
		cepConfig = new Configuration();
		cepConfig.getEngineDefaults().getThreading().setListenerDispatchPreserveOrder(false);
		cep = EPServiceProviderManager.getProvider(esperEngineName + "_" + this.hashCode(),
				cepConfig);
		cepRT = cep.getEPRuntime();
		cepAdm = cep.getEPAdministrator();
	}

	/**
	 * Create the esper data stream processing
	 * 
	 * @param query
	 * @param subscriber
	 */
	public void createEsperOperatorWithSQL(String query, Subscriber subscriber) {
		cepStatement = cepAdm.createEPL(query);
		cepStatement.setSubscriber(subscriber, "processOutputStream");
	}

	/**
	 * @return the cep run time.
	 */
	public EPRuntime getCepRT() {
		return cepRT;
	}

}
