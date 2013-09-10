package org.cloudcompaas.infrastructureconnector.opennebula;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Random;

import org.cloudcompaas.common.communication.RESTComm;

/**
 * @author angarg12
 *
 */
public class ONEVMContextualizer {
	private int timeoutConnection = 10000;
	private int connectionRetries = 5;
	private int contextualizerTalkingPort = 7777;
	private long monitorInterval = 120000;
	private String idSla;
	private String[] epr;
	private String localSdtId;

	public ONEVMContextualizer(String[] epr_, String localSdtId_, String idSla_){
		epr = epr_;
		localSdtId = localSdtId_;
		idSla = idSla_;
	}
	
	public void execute() throws Exception {	
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/service/search?name=Catalog");
		String id_service = comm.get().getFirst("//id_service");
		comm.setUrl("/service_instance/search?service="+id_service);
		String[] catalogEpr = comm.get().get("//epr");
		Random rand = new Random();
		String randomCatalogEpr = catalogEpr[rand.nextInt(catalogEpr.length)];

		for(int i = 0; i < epr.length; i++){
			for(int j = 0; j < connectionRetries; j++){
				try{
					//System.out.println(randomEpr+" _"+epr[i]+"_ "+localSdtId+" "+idSla+" "+monitorWaitingTime);
					Socket sock = new Socket();
					SocketAddress saddr = new InetSocketAddress(epr[i], contextualizerTalkingPort);
					sock.connect(saddr);
					PrintWriter out = new PrintWriter(
							sock.getOutputStream(), true);
					out.println(randomCatalogEpr);
					out.println(epr[i]);
					out.println(localSdtId);
					out.println(idSla);
					out.println(monitorInterval);
					out.flush();
					out.close();
					sock.close();
					break;
				}catch(Exception e){
					e.printStackTrace();
					Thread.sleep(timeoutConnection);
				}
			}
		}
	}

	public int getTimeoutConnection() {
		return timeoutConnection;
	}

	public void setTimeoutConnection(int timeoutConnection) {
		this.timeoutConnection = timeoutConnection;
	}

	public int getConnectionRetries() {
		return connectionRetries;
	}

	public void setConnectionRetries(int connectionRetries) {
		this.connectionRetries = connectionRetries;
	}

	public int getContextualizerTalkingPort() {
		return contextualizerTalkingPort;
	}

	public void setContextualizerTalkingPort(int contextualizerTalkingPort) {
		this.contextualizerTalkingPort = contextualizerTalkingPort;
	}

	public long getMonitorInterval() {
		return monitorInterval;
	}

	public void setMonitorInterval(long monitorInterval) {
		this.monitorInterval = monitorInterval;
	}
}
