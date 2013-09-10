/*******************************************************************************
 * Copyright (c) 2013, Andrés García García All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * (2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * (3) Neither the name of the Universitat Politècnica de València nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
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
