package org.cloudcompaas.infrastructureconnector.opennebula;

import java.util.Collection;
import java.util.Map;

/**
 * @author angarg12
 *
 */
public class IaaSAgent {
	private int id;
	private String epr;
	private Map<String,Collection<String>> extensions;

	public void setId(int id){
		this.id = id;
	}
	
	public int getId(){
		return id;
	}
	
	public void setEpr(String epr) {
		this.epr = epr;
	}
	
	public String getEpr() {
		return epr;
	}
	
	public void setExtensions(Map<String,Collection<String>> extensions) {
		this.extensions = extensions;
	}
	
	public Map<String,Collection<String>> getExtensions() {
		return extensions;
	}
	
	public Collection<String> getExtension(String key){
		return extensions.get(key);
	}
}
