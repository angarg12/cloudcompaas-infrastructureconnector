package org.cloudcompaas.infrastructureconnector.opennebula;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import org.apache.wink.common.annotations.Scope;
import org.apache.wink.common.http.HttpStatus;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.ComparisonOperation;
import org.cloudcompaas.common.util.Evaluator;
import org.cloudcompaas.common.util.Range;
import org.cloudcompaas.common.util.StringComparator;
import org.cloudcompaas.common.util.XMLWrapper;
import org.cloudcompaas.infrastructureconnector.AInfrastructureConnector;

import org.ogf.schemas.graap.wsAgreement.AgreementPropertiesDocument;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.vm.VirtualMachine;

import es.upv.grycap.cloudcompaas.ExactType;
import es.upv.grycap.cloudcompaas.MetadataDocument;
import es.upv.grycap.cloudcompaas.PhysicalResourceType;
import es.upv.grycap.cloudcompaas.RangeType;
import es.upv.grycap.cloudcompaas.RangeValueType;
import es.upv.grycap.cloudcompaas.ValueType;
import es.upv.grycap.cloudcompaas.VirtualMachineDocument;
import es.upv.grycap.cloudcompaas.VirtualMachineType;

/**
 * @author angarg12
 *
 */
@Scope(Scope.ScopeType.SINGLETON)
@Path("/agreement")
public class ONEInfrastructureConnector extends AInfrastructureConnector {
	private static final int DEPLOY_RETRIES = 120;
	private static final int DEPLOY_WAIT = 10000;
	private LocationComparator comparator;
	private Collection<IaaSAgent> iaasagents = new Vector<IaaSAgent>();

	public ONEInfrastructureConnector() throws Exception {
		super();
		comparator = new LocationComparator();
		
		BufferedReader bis = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("vagents.xml")));
		String vagents = "";
		while(bis.ready()){
			vagents += bis.readLine();
		}
		XMLWrapper wrap = new XMLWrapper(vagents);
		XmlObject[] agents = wrap.getNodes("//iaasagent");
		for(int i = 0; i < agents.length; i++){
			Map<String,Collection<String>> extensions = new HashMap<String,Collection<String>>();

			XMLWrapper agentWrap = new XMLWrapper(agents[i]);
			String[] extNames = agentWrap.get("//@name");
			String[] extValues = agentWrap.get("//extension");
			for(int j = 0; j < extNames.length; j++){
				Collection<String> values = extensions.get(extNames[j]);
				if(values == null){
					values = new Vector<String>();
					values.add(extValues[j]);
					extensions.put(extNames[j], values);
				}else{
					values.add(extValues[j]);
				}
			}
			IaaSAgent current = new IaaSAgent();
			current.setId(i);

			current.setEpr(agentWrap.getFirst("//epr"));
			current.setExtensions(extensions);
			iaasagents.add(current);
		}
	}
	
	@POST
	public Response deploySLA(@HeaderParam("Authorization") String auth, String idSla) {
		try{
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		}catch(Exception e){
			e.printStackTrace();
			return Response
					.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
					.build();
		}
		int status = HttpStatus.INTERNAL_SERVER_ERROR.getCode();
		String vmtemplate = "";
		
		try {
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("sla/"+idSla);
    		XMLWrapper wrap = comm.get();
    		
			XmlOptions options = new XmlOptions();
		    options.setLoadStripWhitespace();
		    options.setLoadTrimTextBuffer();
			AgreementPropertiesDocument xmlsla = AgreementPropertiesDocument.Factory.parse(wrap.getFirst("//xmlsla"));

			if(xmlsla.getAgreementProperties().getName() == null){
				status = HttpStatus.BAD_REQUEST.getCode();
				throw new Exception("SLA name not defined.");
			}
	
			vmtemplate += "NAME = "+xmlsla.getAgreementProperties().getName()+" \n";
			
			ServiceDescriptionTermType[] terms = xmlsla.getAgreementProperties().getTerms().getAll().getServiceDescriptionTermArray();
			String hypervisor = null;
			String imageid = null; 
			String replicas = null;
			String sdt_id = null;
			
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
					sdt_id = terms[i].getName();
					VirtualMachineDocument vm = VirtualMachineDocument.Factory.parse(terms[i].getDomNode().getFirstChild());
					imageid = String.valueOf(vm.getVirtualMachine().getOperatingSystem().getOSId());
					hypervisor = vm.getVirtualMachine().getOperatingSystem().getHypervisor().toString();
					vmtemplate = processResources(vm.getVirtualMachine(), vmtemplate);
				}
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:Metadata")){
					MetadataDocument metadata = MetadataDocument.Factory.parse(terms[i].getDomNode().getFirstChild());
					if(metadata.getMetadata().getReplicas().getRangeValue().sizeOfExactArray() > 0){
						replicas = metadata.getMetadata().getReplicas().getRangeValue().getExactArray(0).getStringValue();
					} else if(metadata.getMetadata().getReplicas().getRangeValue().sizeOfRangeArray() > 0){
						replicas = metadata.getMetadata().getReplicas().getRangeValue().getRangeArray(0).getLowerBound().getStringValue();
					} else {
						status = HttpStatus.BAD_REQUEST.getCode();
						throw new Exception("Initial number of replicas not defined.");
					}
				}
			}
			
			if(hypervisor == null){
				status = HttpStatus.BAD_REQUEST.getCode();
				throw new Exception("Hypervisor not defined.");
			}
	
			if(imageid == null){
				status = HttpStatus.BAD_REQUEST.getCode();
				throw new Exception("Virtual Container not defined.");
			}

			if(replicas == null){
				status = HttpStatus.BAD_REQUEST.getCode();
				throw new Exception("Initial number of replicas not defined.");
			}
			
			Iterator<IaaSAgent> iva = iaasagents.iterator();
			Collection<IaaSAgent> newiaasagents = new Vector<IaaSAgent>();
			while(iva.hasNext()){
				IaaSAgent agent = iva.next();
				if(agent.getExtension("hypervisor") == null) continue;
				if(agent.getExtension("hypervisor").contains(hypervisor)){
					newiaasagents.add(agent);
				}
			}
			iaasagents = newiaasagents;	
	
			if(iaasagents.size() < 1){
				throw new Exception("No available Virtualization Agent meets the required constraints.");
			}
			
			if(hypervisor.equalsIgnoreCase("vmware")){
				vmtemplate += "DISK = [\n"+
				"source = \"vmrc://"+imageid+"\",\n"+
		        "target = \"sda\",\n"+
		        "bus = \"scsi\",\n"+
		        "driver = \"lsilogic\"\n"+
		        "]\n";
			}else if(hypervisor.equalsIgnoreCase("kvm")){
				vmtemplate += "DISK = [\n"+
				"IMAGE_ID   = "+imageid+"\n"+
		        "]\n";
			}else{
				throw new Exception("Hypervisor not supported: "+hypervisor);
			}
			vmtemplate += "GRAPHICS=[  LISTEN=\"0.0.0.0\",  TYPE=\"vnc\" ]";
			int initialreplicas = Integer.parseInt(replicas);
	        VirtualMachine[] vmarray = new VirtualMachine[initialreplicas];
	
			IaaSAgent agent = (IaaSAgent) iaasagents.toArray()[0];
			if(agent.getExtension("credentials") == null || agent.getExtension("credentials").size() == 0){
				throw new Exception("The virtualization agent does not provide credentials.");
			}
			String authentication = (String) agent.getExtension("credentials").toArray()[0];
			String onerpc = agent.getEpr();
			Client one = new Client(authentication,onerpc);

			for(int u = 0; u < initialreplicas; u++){
				OneResponse res = VirtualMachine.allocate(one, vmtemplate);
				if(res.isError()){
					throw new Exception(res.getErrorMessage());
				}
				int vmid = -1;
				if(res.isError() == false){
					vmid = Integer.parseInt(res.getMessage());
				}
				vmarray[u] = new VirtualMachine(vmid, one);
				Thread.sleep(5000);
			}
			
	        String[] vminstancesid = new String[initialreplicas];

    		Properties properties = new Properties();
	        for(int u = 0; u < initialreplicas; u++){
	        	properties.setProperty("id_iaasagent", String.valueOf(agent.getId()));
	        	properties.setProperty("id_vm_instance_local_iaasagent", vmarray[u].getId());
	        	properties.setProperty("local_sdt_id", sdt_id);
	        	properties.setProperty("id_sla", idSla);
		        
	        	comm.setUrl("vm_instance");
	        	comm.setContentType("text/xml");
	        	String payload;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				properties.storeToXML(baos, null);
				payload = baos.toString();
				
				vminstancesid[u] = comm.post(payload).getFirst("id_vm_instance");
	        }
			
	        int i;
	        for(i = 0; i < DEPLOY_RETRIES; i++){
	        	Thread.sleep(DEPLOY_WAIT);
	        	boolean alldeployed = true;
	        	for(int u = 0; u < initialreplicas; u++){
		        	vmarray[u].info();
		        	System.out.println(vmarray[u].state()+" "+vmarray[u].status()+" "+vmarray[u].lcmState());

		        	System.out.println(vmarray[u].xpath("//IP"));
		        	if(vmarray[u].status() == null){ 
		        		continue;
	        		}else if(vmarray[u].status().equalsIgnoreCase("fail") == true){
		        		throw new Exception("Virtual Machine failed to deploy.");
		        	}else if(vmarray[u].status().equalsIgnoreCase("unkn") == true){
						throw new Exception("Virtual Machine in unknown status.");
		        	}else if(vmarray[u].status().equalsIgnoreCase("done") == true){
						throw new Exception("Virtual Machine has been finalized.");
		        	}else if(vmarray[u].status().equalsIgnoreCase("runn") == false){
		        		alldeployed = false;
		        	}
	        	}
	        	if(alldeployed == true) break;
	        }
	        if(i == DEPLOY_RETRIES){
	        	throw new Exception("Virtual Machine deployment process timed out.");
	        }
	
	        String[] eprs = new String[initialreplicas];
	        String eprsXml = "<eprs>";

    		properties = new Properties();
	        for(int u = 0; u < vminstancesid.length; u++){
	        	eprs[u] = vmarray[u].xpath("//IP");
		        eprsXml += "<epr>"+eprs[u]+"</epr>";
		        
	        	properties.setProperty("epr", eprs[u]);
		        
	        	comm.setUrl("vm_instance/"+vminstancesid[u]);
	        	comm.setContentType("text/xml");
	        	String payload;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				properties.storeToXML(baos, null);
				payload = baos.toString();
				
				comm.put(payload);
	        }
	        eprsXml += "</eprs>";
	        
			ONEVMContextualizer vmcont = new ONEVMContextualizer(eprs, sdt_id, idSla);
			vmcont.execute();
			
	        return Response
	        .status(HttpStatus.OK.getCode())
	        .entity(eprsXml)
	        .build();
		}catch(Exception e){
			e.printStackTrace();
			return Response
			.status(status)
			.entity(e.getMessage())
			.build();
		}
	}

	@POST
	@Path("{id}/{servicename}/ServiceTermState/Metadata/Replicas/RangeValue/Exact")
	@Consumes("text/plain")
	public Response deployReplicas(@HeaderParam("Authorization") String auth, @PathParam("id") String idSla, @PathParam("servicename") String serviceName, String replicas) {
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		int numReplicas = Integer.parseInt(replicas);
		int status = HttpStatus.INTERNAL_SERVER_ERROR.getCode();
		String vmtemplate = "";
		
		try {
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("sla/"+idSla);
    		XMLWrapper wrap = comm.get();
    		
			XmlOptions options = new XmlOptions();
		    options.setLoadStripWhitespace();
		    options.setLoadTrimTextBuffer();
			AgreementPropertiesDocument xmlsla = AgreementPropertiesDocument.Factory.parse(wrap.getFirst("//xmlsla"));

			if(xmlsla.getAgreementProperties().getName() == null){
				status = HttpStatus.BAD_REQUEST.getCode();
				throw new Exception("SLA name not defined.");
			}
	
			vmtemplate += "NAME = "+xmlsla.getAgreementProperties().getName()+" \n";
			
			ServiceDescriptionTermType[] terms = xmlsla.getAgreementProperties().getTerms().getAll().getServiceDescriptionTermArray();
			String hypervisor = null;
			String imageid = null; 
			String sdt_id = null;
			
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
					sdt_id = terms[i].getName();
					VirtualMachineDocument vm = VirtualMachineDocument.Factory.parse(terms[i].getDomNode().getFirstChild());
					imageid = String.valueOf(vm.getVirtualMachine().getOperatingSystem().getOSId());
					hypervisor = vm.getVirtualMachine().getOperatingSystem().getHypervisor().toString();
					vmtemplate = processResources(vm.getVirtualMachine(), vmtemplate);
				}
			}
			
			if(hypervisor == null){
				status = HttpStatus.BAD_REQUEST.getCode();
				throw new Exception("Hypervisor not defined.");
			}
	
			if(imageid == null){
				status = HttpStatus.BAD_REQUEST.getCode();
				throw new Exception("Virtual Container not defined.");
			}
			
			Iterator<IaaSAgent> iva = iaasagents.iterator();
			Collection<IaaSAgent> newiaasagents = new Vector<IaaSAgent>();
			while(iva.hasNext()){
				IaaSAgent agent = iva.next();
				if(agent.getExtension("hypervisor") == null) continue;
				if(agent.getExtension("hypervisor").contains(hypervisor)){
					newiaasagents.add(agent);
				}
			}
			iaasagents = newiaasagents;	
	
			if(iaasagents.size() < 1){
				throw new Exception("No available Virtualization Agent meets the required constraints.");
			}
			
			if(hypervisor.equalsIgnoreCase("vmware")){
				vmtemplate += "DISK = [\n"+
				"source = \"vmrc://"+imageid+"\",\n"+
		        "target = \"sda\",\n"+
		        "bus = \"scsi\",\n"+
		        "driver = \"lsilogic\"\n"+
		        "]\n";
			}else if(hypervisor.equalsIgnoreCase("kvm")){
				vmtemplate += "DISK = [\n"+
				"IMAGE_ID   = "+imageid+"\n"+
		        "]\n";
			}else{
				throw new Exception("Hypervisor not supported: "+hypervisor);
			}
			
	        VirtualMachine[] vmarray = new VirtualMachine[numReplicas];
	
			IaaSAgent agent = (IaaSAgent) iaasagents.toArray()[0];
			if(agent.getExtension("credentials") == null || agent.getExtension("credentials").size() == 0){
				throw new Exception("The virtualization agent does not provide credentials.");
			}
			String authentication = (String) agent.getExtension("credentials").toArray()[0];
			String onerpc = agent.getEpr();
			Client one = new Client(authentication,onerpc);

			for(int u = 0; u < numReplicas; u++){
				OneResponse res = VirtualMachine.allocate(one, vmtemplate);
				if(res.isError()){
					throw new Exception(res.getErrorMessage());
				}
				int vmid = -1;
				if(res.isError() == false){
					vmid = Integer.parseInt(res.getMessage());
				}
				vmarray[u] = new VirtualMachine(vmid, one);
			}

	        int i;
	        for(i = 0; i < DEPLOY_RETRIES; i++){
	        	Thread.sleep(DEPLOY_WAIT);
	        	boolean alldeployed = true;
	        	for(int u = 0; u < numReplicas; u++){
		        	vmarray[u].info();
		        	System.out.println(vmarray[u].state()+" "+vmarray[u].status()+" "+vmarray[u].lcmState());
		        	System.out.println(vmarray[u].xpath("//IP"));
		        	if(vmarray[u].status().equalsIgnoreCase("fail") == true){
		        		throw new Exception("Virtual Machine failed to deploy.");
		        	}else if(vmarray[u].status().equalsIgnoreCase("unkn") == true){
		        		throw new Exception("Virtual Machine in unknown status.");
		        	}else if(vmarray[u].status().equalsIgnoreCase("done") == true){
		        		throw new Exception("Virtual Machine has been finalized.");
		        	}else if(vmarray[u].status().equalsIgnoreCase("runn") == false){
		        		alldeployed = false;
		        	}
	        	}
	        	if(alldeployed == true) break;
	        }
	        if(i == DEPLOY_RETRIES){
	        	throw new Exception("Virtual Machine deployment process timed out.");
	        }

	        String[] eprs = new String[numReplicas];
	        String eprsXml = "<eprs>";

    		Properties properties = new Properties();
	        for(int u = 0; u < numReplicas; u++){
	        	eprs[u] = vmarray[u].xpath("//IP");
		        eprsXml += "<epr>"+eprs[u]+"</epr>";
		        
	        	properties.setProperty("id_iaasagent", String.valueOf(agent.getId()));
	        	properties.setProperty("epr", eprs[u]);
	        	properties.setProperty("id_vm_instance_local_iaasagent", vmarray[u].getId());
	        	properties.setProperty("local_sdt_id", sdt_id);
	        	properties.setProperty("id_sla", idSla);
		        
	        	comm.setUrl("vm_instance");
	        	comm.setContentType("text/xml");
	        	String payload;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				properties.storeToXML(baos, null);
				payload = baos.toString();
				
				comm.post(payload);
	        }
	        
	        eprsXml += "</eprs>";
	        
			ONEVMContextualizer vmcont = new ONEVMContextualizer(eprs, sdt_id, idSla);
			vmcont.execute();
	        
	        return Response
	        .status(HttpStatus.OK.getCode())
	        .entity(eprsXml)
	        .build();
		}catch(Exception e){
			e.printStackTrace();
			return Response
			.status(status)
			.entity(e.getMessage())
			.build();
		}
	}

	@DELETE
	@Path("{id}")
	public Response undeploySLA(@HeaderParam("Authorization") String auth, @PathParam("id") String idSla){
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		int status = HttpStatus.INTERNAL_SERVER_ERROR.getCode();
		try{
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("vm_instance/search?id_sla="+idSla);
    		XMLWrapper wrap = comm.get();
			
			for(int i = 0; i < wrap.get("//id_vm_instance").length; i++){
				Iterator<IaaSAgent> itva = iaasagents.iterator();
				while(itva.hasNext()){
					IaaSAgent agent = itva.next();
					if(agent.getId() == Integer.parseInt(wrap.get("//id_iaasagent")[i])){
						if(agent.getExtension("credentials") == null || agent.getExtension("credentials").size() == 0){
							throw new Exception("The virtualization agent does not provide credentials.");
						}
						String credentials = (String) agent.getExtension("credentials").toArray()[0];

						Client one = new Client(credentials, agent.getEpr());

						VirtualMachine vm = new VirtualMachine
							(Integer.parseInt(wrap.get("//id_vm_instance_local_iaasagent")[i])
							, one);
						OneResponse res = vm.finalizeVM();
						if(res.getErrorMessage() != null){
							throw new Exception(res.getErrorMessage());
						}
						break;
					}
				}
			}
		}catch(Exception e){
			return Response
			.status(status)
			.entity(e.getMessage())
			.build();
		}
        return Response
        .status(HttpStatus.OK.getCode())
        .build();
	}
	
	@DELETE
	@Path("{id}/{servicename}/{eprs}")
	public Response undeployReplicas(@HeaderParam("Authorization") String auth, 
			@PathParam("id") String idSla, @PathParam("servicename") String serviceName, 
			@PathParam("eprs") String eprsPlain) {

		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		int status = HttpStatus.INTERNAL_SERVER_ERROR.getCode();
		try{
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("/vm_instance/search?id_sla="+idSla);
    		XMLWrapper wrap = comm.get();

        	String[] idIaasagent = wrap.get("//id_iaasagent");
        	String[] idVmInstanceLocalIaasagent = wrap.get("//id_vm_instance_local_iaasagent");
        	String[] idVmInstance = wrap.get("//id_vm_instance");
        	String[] epr = wrap.get("//epr");
        	String[] eprs = eprsPlain.split("\n");
        	
			for(int i = 0; i < epr.length; i++){
				for(int j = 0; j < eprs.length; j++){
					if(epr[i] == epr[j]){
						Iterator<IaaSAgent> itva = iaasagents.iterator();
						while(itva.hasNext()){
							IaaSAgent agent = itva.next();
							if(agent.getId() == Integer.parseInt(idIaasagent[i])){
								if(agent.getExtension("credentials") == null || agent.getExtension("credentials").size() == 0){
									throw new Exception("The virtualization agent does not provide credentials.");
								}
								String credentials = (String) agent.getExtension("credentials").toArray()[0];
		
								Client one = new Client(credentials, agent.getEpr());
		
								VirtualMachine vm = new VirtualMachine(Integer.parseInt(idVmInstanceLocalIaasagent[i]), one);
								OneResponse res = vm.finalizeVM();
								if(res.getErrorMessage() != null){
									throw new Exception(res.getErrorMessage());
								}
								
								comm.setUrl("/vm_instance/"+idVmInstance[i]);
								comm.delete();
								comm.setUrl("/monitoring_information/search?epr="+epr[i]);
								comm.delete();
								/** FIXME
								 * This is a poor hack, since we are deleting all references for VC and VS that reside in this VM.
								 * HOWEVER this is not how this is supposed to work. The correct behaviour is that in order to undeploy a
								 * VC or VS replica, we ask the PaaS and SaaS Connectors respectively to undeploy the replicas, and those are the ones in charge
								 * of deleting associated data. With this poor hack we bypass the need to define an ad-hoc SaaS Manage, since the SaaS client (or 
								 * PaaS that host it) is not yet ready).
								 */
								comm.setUrl("/vr_instance/search?epr="+epr[i]+"*");
								comm.delete();
								comm.setUrl("/service_instance/search?epr="+epr[i]+"*");
								comm.delete();
								comm.setUrl("/monitoring_information/search?epr="+epr[i]+"*");
								comm.delete();
								/**
								 * 
								 */
								
								break;
							}
						}
						break;
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
			return Response
			.status(status)
			.entity(e.getMessage())
			.build();
		}
		
        return Response
        .status(HttpStatus.OK.getCode())
        .build();
	}

	private String processResources(VirtualMachineType xmlsla, String vmtemplate) throws Exception {
		PhysicalResourceType[] resources = xmlsla.getPhysicalResourceArray();

		if(resources.length < 1){
			throw new Exception("PhysicalResource not defined.");
		}

		for(int i = 0; i < resources.length; i++){
			if(resources[i].getName().equals("Cores")){
				vmtemplate = processCores(resources[i], vmtemplate);
			} else if(resources[i].getName().equals("Memory")){
				vmtemplate = processMemory(resources[i], vmtemplate);
			} else if(resources[i].getName().equals("Location")){
				processLocation(resources[i]);
			} else if(resources[i].getName().equals("ArcGISLicense")){
				processArcGISLicense(resources[i]);
			} else if(resources[i].getName().equals("Network")){
				vmtemplate = processNetwork(resources[i], vmtemplate);
			} else if(resources[i].getName().equals("Architecture")){
				vmtemplate = processArchitecture(resources[i], vmtemplate);
			} else {
				throw new Exception("PhysicalResource not supported: "+resources[i].getName());
			}
		}
		return vmtemplate;
	}

	private String processCores(PhysicalResourceType xmlsla, String vmtemplate) throws Exception {
		RangeValueType cores = xmlsla.getPhysicalResourceValue().getRangeValue();
		if(cores == null){
			throw new Exception("PhysicalResource value not defined: Cores");
		}
		
		Range range = new Range(cores);
		try{
			int numcores = getCores(range);
			vmtemplate += "CPU    = "+numcores+" \n";
		}catch(Exception ce){
			throw new Exception("PhysicalResource processing failed: Cores");
		}
		return vmtemplate;
	}
	
	private int getCores(Range range) throws Exception {
		for(int i = 16; i >= 1; i--){
			if(range.isInRange(i)){
				return i;
			}
		}
		throw new Exception("PhysicalResource Cores not available for defined Range.");
	}
	
	private String processMemory(PhysicalResourceType xmlsla, String vmtemplate) throws Exception {
		RangeValueType value = xmlsla.getPhysicalResourceValue().getRangeValue();
		if(value == null){
			throw new Exception("PhysicalResource value not defined: Memory");
		}
		String unit = xmlsla.getPhysicalResourceUnit();

		if(unit.equalsIgnoreCase("mb") == false &&
				unit.equalsIgnoreCase("gb") == false){
			throw new Exception("PhysicalResource unit for Memory not supported: "+unit);
		}
		if(unit.equalsIgnoreCase("gb")){
			ExactType[] exacts = value.getExactArray();
			for(int i = 0; i < exacts.length; i++){
				exacts[i].setLongValue(exacts[i].getLongValue()*1024);
				if(exacts[i].getEpsilon() != 0){
					exacts[i].setEpsilon(exacts[i].getEpsilon()*1024);
				}
			}
			RangeType[] ranges = value.getRangeArray();
			for(int i = 0; i < ranges.length; i++){
				if(ranges[i].getLowerBound() != null){
					ranges[i].getLowerBound().setLongValue(ranges[i].getLowerBound().getLongValue()*1024);
				}
				if(ranges[i].getUpperBound() != null){
					ranges[i].getUpperBound().setLongValue(ranges[i].getUpperBound().getLongValue()*1024);
				}
			}
			if(value.getLowerBoundedRange() != null){
				value.getLowerBoundedRange().setLongValue(value.getLowerBoundedRange().getLongValue()*1024);
			}
			if(value.getUpperBoundedRange() != null){
				value.getUpperBoundedRange().setLongValue(value.getUpperBoundedRange().getLongValue()*1024);
			}
		}

		Range range = new Range(value);
		try{
			int nummemory = getMemory(range);
			vmtemplate += "MEMORY    = "+nummemory+" \n";
		}catch(Exception ce){
			throw new Exception("PhysicalResource processing failed: Memory");
		}
		return vmtemplate;
	}
	
	private int getMemory(Range range) throws Exception {
		for(int i = 16384; i >= 128; i-=128){
			if(range.isInRange(i)){
				return i;
			}
		}
		throw new Exception("PhysicalResource Memory not available for defined Range.");
	}
	
	private void processLocation(PhysicalResourceType xmlsla) throws Exception {
		ValueType value = xmlsla.getPhysicalResourceValue();
		if(value == null){
			throw new Exception("No value defined for resource: Location.");
		}

		Evaluator eval = new Evaluator();
		Iterator<IaaSAgent> iva = iaasagents.iterator();
		Collection<IaaSAgent> newiaasagents = new Vector<IaaSAgent>();
		while(iva.hasNext()){
			IaaSAgent agent = iva.next();
			Collection<String> location = agent.getExtension("location");
			if(location == null) continue;
			Iterator<String> it = location.iterator();
			while(it.hasNext()){
				String iaasagentlocation = it.next();
				if(eval.Evaluate(value.getAll().getDomNode(), iaasagentlocation, comparator, ComparisonOperation.EQUAL) == true){
					newiaasagents.add(agent);	
					break;
				}
			}
		}
		iaasagents = newiaasagents;	
		if(iaasagents.size() == 0){
			throw new Exception("No available Virtualization Agent meets the required constraints: Location.");
		}	
	}
	
	private String processNetwork(PhysicalResourceType xmlsla, String vmtemplate) throws Exception {
		ValueType value = xmlsla.getPhysicalResourceValue();
		if(value == null){
			throw new Exception("No value defined for resource: Network.");
		}
		String network = null;
		Evaluator eval = new Evaluator();
		Iterator<IaaSAgent> iva = iaasagents.iterator();
		Collection<IaaSAgent> newiaasagents = new Vector<IaaSAgent>();
		while(iva.hasNext()){
			IaaSAgent agent = iva.next();
			Collection<String> networks = agent.getExtension("network");
			if(networks == null) continue;
			Iterator<String> it = networks.iterator();
			while(it.hasNext()){
				String iaasagentnetwork = it.next();
				if(eval.Evaluate(value.getAll().getDomNode(), iaasagentnetwork, new StringComparator(), ComparisonOperation.EQUAL) == true){
					newiaasagents.add(agent);	
					network = iaasagentnetwork;
					break;
				}
			}
		}
		iaasagents = newiaasagents;	
		if(iaasagents.size() == 0){
			throw new Exception("No available Virtualization Agent meets the required constraints: Network.");
		}
		vmtemplate += "NIC=[ NETWORK_ID="+network+" ] \n";
		return vmtemplate;
	}
	
	private String processArchitecture(PhysicalResourceType xmlsla, String vmtemplate) throws Exception {
		ValueType value = xmlsla.getPhysicalResourceValue();
		if(value == null){
			throw new Exception("No value defined for resource: Architecture.");
		}

		String architecture = null;
		Evaluator eval = new Evaluator();
		Iterator<IaaSAgent> iva = iaasagents.iterator();
		Collection<IaaSAgent> newiaasagents = new Vector<IaaSAgent>();
		while(iva.hasNext()){
			IaaSAgent agent = iva.next();
			Collection<String> architectures = agent.getExtension("architecture");
			if(architectures == null) continue;
			Iterator<String> it = architectures.iterator();
			while(it.hasNext()){
				String iaasagentarchitecture = it.next();
				if(eval.Evaluate(value.getAll().getDomNode(), iaasagentarchitecture, new StringComparator(), ComparisonOperation.EQUAL) == true){
					newiaasagents.add(agent);
					architecture = iaasagentarchitecture;
					break;
				}
			}
		}
		iaasagents = newiaasagents;	
		if(iaasagents.size() == 0){
			throw new Exception("No available Virtualization Agent meets the required constraints: Architecture.");
		}
		vmtemplate += "OS = [ ARCH = \""+architecture+"\" ] \n";
		return vmtemplate;
	}
	
	private void processArcGISLicense(PhysicalResourceType xmlsla) throws Exception {
		Iterator<IaaSAgent> iva = iaasagents.iterator();
		Collection<IaaSAgent> newiaasagents = new Vector<IaaSAgent>();
		while(iva.hasNext()){
			IaaSAgent agent = iva.next();
			Collection<String> location = agent.getExtension("location");
			if(location == null) continue;
			Iterator<String> it = location.iterator();
			while(it.hasNext()){
				String iaasagentlocation = it.next();
				if(comparator.compare(iaasagentlocation, "UPV", ComparisonOperation.EQUAL) == true){
					newiaasagents.add(agent);
					break;
				}
			}
		}
		iaasagents = newiaasagents;	
		if(iaasagents.size() == 0){
			throw new Exception("No available Virtualization Agent meets the required constraints: ArcGISLicense.");
		}
	}


}
