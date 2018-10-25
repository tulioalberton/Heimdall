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
import java.util.Iterator;
import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeuristicCentralized {

	private static Logger logger;
	private ArrayList<Integer> ctrls;
	private ArrayList<Long> toAssign;
	private double K;
	private static MapCS m;
	private HashMap<Integer, HashMap<Long, Long>> tblL;

	/**
	 * @param kIn
	 *            load factor input
	 * @param mIn
	 *            oldMap-new map
	 */
	public HeuristicCentralized(HashMap<Integer, HashMap<Long, Long>> tl, double kIn, MapCS mIn) {
		logger = LoggerFactory.getLogger(HeuristicCentralized.class);

		ctrls = new ArrayList<>();
		toAssign = new ArrayList<>();

		ctrls.addAll(tl.keySet());

		Iterator<Integer> it = ctrls.iterator();
		while (it.hasNext()) {
			Iterator<Long> it2 = tl.get(it.next()).keySet().iterator();
			while (it2.hasNext()) {
				Long sw = Long.parseLong("" + it2.next());
				if (!toAssign.contains(sw))
					toAssign.add(sw);
			}
		}

		// toAssign.addAll(tl.get(ctrls.get(0)).keySet());

		tblL = tl;		
		K = kIn;
		m = mIn;
	}

	public HashMap<Integer, ArrayList<Long>> CalcMap(HashMap<Integer, ArrayList<Long>> lastMap) {

		HashMap<Integer, ArrayList<Long>> aFinal = new HashMap<>();
		
		Iterator<Integer> it = ctrls.iterator();
		while (it.hasNext()) {
			Integer controller = (Integer) it.next();
			aFinal.put(controller, new ArrayList<Long>());	
		}
		
		
		logger.trace("Controllers: {}", ctrls.toString());
		logger.trace("Switches to Assign: {}", toAssign.toString());
		logger.trace("LastMap: {}", lastMap.toString());
		logger.trace("NewMap: {}", tblL.toString());
		
		Iterator<Integer> itCtrl = tblL.keySet().iterator();		
		while (itCtrl.hasNext()) {
			int controllerId = itCtrl.next();
			
			HashMap<Long, Long> switches = tblL.get(controllerId);
			
			if(lastMap.containsKey(controllerId)){
				Iterator<Long> itSw = switches.keySet().iterator();
				
				while (itSw.hasNext()) {
					Long swId = itSw.next();
					
					if(lastMap.get(controllerId).contains(swId)){
						//logger.debug("Switch newMap = OldMap: {}", swId);
						tblL.get(controllerId).replace(swId, -100L);
					}
				}
			}
		}		
		
		logger.trace("NewMap Edited: {}", tblL.toString());
		

		for (int s = 0; s < toAssign.size(); s++) {
			Long swId = Long.parseLong("" + toAssign.get(s));

			int ctrlId = min(swId, m, K);
			
			if(m.getMap().containsKey(ctrlId)){
				logger.trace("Adding to map: Switch: {}", DatapathId.of(swId));
				m.getMap().get(ctrlId).getSwitches().add(DatapathId.of(swId));			
				m.getMap().get(ctrlId).incCapC();
				aFinal.get(ctrlId).add(swId);
			}
			else{
				logger.debug("NOT Adding to map: Switch: {}, Ctrl: {}", DatapathId.of(swId), ctrlId);
			}
		}
		logger.debug("Final Map Recomputed: {}", aFinal.toString());
		

		return aFinal;

	}

	public int min(Long swId, MapCS m, double K) {

		Long lt = 999999999L;
		int r = -1;

		Iterator<Integer> it = tblL.keySet().iterator();
		
		while (it.hasNext()) {
			int ctrlId = it.next();
			try {
				Long latency = tblL.get(ctrlId).get(swId);

				if ((latency + (K * m.getMap().get(ctrlId).getSwitchesSize())) < lt
						&& m.getMap().get(ctrlId).getCapC() < m.getMap().get(ctrlId).getCapT()) {
					lt = latency;
					r = ctrlId;
				}
			} catch (java.lang.NullPointerException npe) {
				logger.trace("EXCEPTION... getmessage: {}, getCause:{}", npe.getMessage(), npe.getCause());
				//npe.printStackTrace();

			}
		}
		//logger.debug("Return: {}", r);
		return r;
	}

}
