package org.cloudcompaas.infrastructureconnector;

import java.util.Properties;

import org.cloudcompaas.common.components.Component;
import org.cloudcompaas.common.components.Register;

/**
 * @author angarg12
 *
 */
public abstract class AInfrastructureConnector extends Component implements IInfrastructureConnector {
	public AInfrastructureConnector() throws Exception {
		Properties properties = new Properties();
		properties.load(getClass().getResourceAsStream("/conf/InfrastructureConnector.properties"));
		String service = properties.getProperty("service");
		String version = properties.getProperty("version");
		String epr = properties.getProperty("epr");
		Register register = new Register(Thread.currentThread(), service, version, epr);
		register.start();
	}
}
