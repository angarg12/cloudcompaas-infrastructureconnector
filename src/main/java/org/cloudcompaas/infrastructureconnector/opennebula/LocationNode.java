package org.cloudcompaas.infrastructureconnector.opennebula;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

/**
 * @author angarg12
 *
 */
public class LocationNode {
	private Collection<LocationNode> parents;
	private String name;
	
	LocationNode(String n){
		name = n;
		parents = new Vector<LocationNode>();
	}
	
	public void addParent(LocationNode p){
		parents.add(p);
	}
	
	public boolean isContainedIn(String location){
		if(name.equalsIgnoreCase(location)) return true;
		Iterator<LocationNode> i = parents.iterator();
		while(i.hasNext()){
			LocationNode lc = i.next();
			if(lc.isContainedIn(location) == true) return true;
		}
		return false;
	}
	
	public String getName(){
		return name;
	}
}