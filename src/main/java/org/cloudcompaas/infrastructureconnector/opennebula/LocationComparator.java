package org.cloudcompaas.infrastructureconnector.opennebula;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import org.cloudcompaas.common.util.Comparator;
import org.cloudcompaas.common.util.ComparisonOperation;

/**
 * @author angarg12
 *
 */
public class LocationComparator implements Comparator {
	Collection<LocationNode> locations;
	
	public LocationComparator(){
		try {
			locations = new Vector<LocationNode>();
			BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("location.txt")));
			String line;
			line = br.readLine();
			while(line != null){
				String[] pairs = line.split("=");
				LocationNode child = null;
				Iterator<LocationNode> i = locations.iterator();
				while(i.hasNext()){
					LocationNode lc = i.next();
					if(lc.getName().equalsIgnoreCase(pairs[0].trim()) == true){
						child = lc;
						break;
					}
				}
				if(child == null){
					child = new LocationNode(pairs[0].trim());
					locations.add(child);
				}
				
				LocationNode parent = null;
				i = locations.iterator();
				while(i.hasNext()){
					LocationNode lc = i.next();
					if(lc.getName().equalsIgnoreCase(pairs[1].trim()) == true){
						parent = lc;
						break;
					}
				}
				if(parent == null){
					parent = new LocationNode(pairs[1].trim());
					locations.add(parent);
				}
				
				child.addParent(parent);
				line = br.readLine();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public boolean compare(Object element1, Object element2,
			ComparisonOperation operation) {
		switch(operation){
			case EQUAL: return equals(element1, element2);
		}
		return false;
	}
	
	public boolean equals(Object element1, Object element2){
		Iterator<LocationNode> i = locations.iterator();
		while(i.hasNext()){
			LocationNode lc = i.next();
			if(lc.getName().equalsIgnoreCase((String) element1) == true){
				return lc.isContainedIn((String) element2);
			}
		}
		return false;
	}


}