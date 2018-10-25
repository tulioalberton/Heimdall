package net.floodlightcontroller.heimdall.heuristicLatency;
/**
 * 
 * Tulio Alberton Ribeiro.
 * 
 * LaSIGE | Large-Scale Informatics Systems Laboratory
 * 
 * FCUL - Department of Informatics, Faculty of Sciences, University of Lisbon.
 * 
 * http://lasige.di.fc.ul.pt/
 * 
 * 03/2016
 * 
 * Without warrant
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         
 *
 * @param <K>
 * @param <V>
 *            
 * The heimdall.heuristic.Controller class, defines the capacity of each controller, and shall be the same. 
 * 
 */
import java.util.ArrayList;
import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;

public class MapCS {

	HashMap<Integer, Controller> map;
	
	public MapCS(ArrayList<Integer> ctrls, int swSize){		
		map = new HashMap<>();
		int capT;
		
		if(swSize > ctrls.size())
			capT = (int) (swSize/ctrls.size() +1 );
			//capT = 128;
		else
			capT = swSize;
		
			
		for (int i = 0; i < ctrls.size(); i++) {
			map.put(ctrls.get(i), new Controller(
					ctrls.get(i), 
					capT, 
					0, 
					new ArrayList<DatapathId>()));			
		}
	}

	public HashMap<Integer, Controller> getMap() {
		return map;
	}
	public void setMap(HashMap<Integer, Controller> map) {
		this.map = map;
	}
	public void addSw2Ctrl(int view, int ctrl, DatapathId sw){
		this.map.get(ctrl).getSwitches().add(sw);		
	}
	
}
