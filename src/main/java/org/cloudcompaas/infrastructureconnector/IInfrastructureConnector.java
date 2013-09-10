package org.cloudcompaas.infrastructureconnector;

import javax.ws.rs.core.Response;

/**
 * @author angarg12
 *
 */
public interface IInfrastructureConnector {
	public Response deploySLA(String auth, String idSla);
	public Response deployReplicas(String auth, String idSla, String serviceName, String numReplicas);
	public Response undeploySLA(String auth, String idSla);
	public Response undeployReplicas(String auth, String idSla, String serviceName, String eprs);
}
