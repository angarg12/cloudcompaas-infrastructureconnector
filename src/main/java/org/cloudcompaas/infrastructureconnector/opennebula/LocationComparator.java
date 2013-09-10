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
