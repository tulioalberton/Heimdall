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

import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HeuristicNM {

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
	public HeuristicNM(HashMap<Integer, HashMap<Long, Long>> tl, double kIn, MapCS mIn) {
        logger = LoggerFactory.getLogger(HeuristicNM.class);

        ctrls = new ArrayList<>();
        toAssign = new ArrayList<>();

        ctrls.addAll(tl.keySet());

        Iterator<Integer> it = ctrls.iterator();
        while(it.hasNext()){
            Iterator<Long> it2 = tl.get(it.next()).keySet().iterator();
            while(it2.hasNext()) {
                Long sw = Long.parseLong("" + it2.next());
                if (!toAssign.contains(sw))
                    toAssign.add(sw);
            }
        }

        //toAssign.addAll(tl.get(ctrls.get(0)).keySet());

        tblL = tl;
		K = kIn;
		m = mIn;
	}

	public HashMap<Integer, ArrayList<Long>> CalcMap(int ctrlId) {

		HashMap<Integer, ArrayList<Long>> aFinal = new HashMap<>();
		aFinal.put(ctrlId, new ArrayList<Long>());
		

		logger.info("Controllers: {}", ctrls.toString());
		logger.info("Switches: {}", toAssign.toString());
		logger.info("tblL: {}, Type: {}", tblL.toString(), tblL.getClass().getTypeName());

		for (int s = 0; s < toAssign.size(); s++) {
                Long swId = Long.parseLong(""+toAssign.get(s));
				if (m.getMap().get(ctrlId).getSwitchesSize() + 1 <= m.getMap().get(ctrlId).getCapT()
						//&& !l.getElement(ctrlId, swId).equals("-")
						) {

					int check = min(swId, m, K);
					
					if (ctrlId == check) {
						m.getMap().get(ctrlId).getSwitches().add(DatapathId.of(swId));
						m.getMap().get(ctrlId).incCapC();
						aFinal.get(ctrlId).add(swId);

					} else if (check != -1) {
						m.getMap().get(check).getSwitches().add(DatapathId.of(swId));
						m.getMap().get(check).incCapC();
						
						/*if(!aFinal.containsKey(check))
							aFinal.put(check, new ArrayList<>());
						
						aFinal.get(check).add(swId);*/
						
					}else {
                        logger.info("Switch {}, could NOT be assigned, Ctrl {} capacities over..", swId, check);
                    }

					if (m.getMap().get(ctrlId).getSwitchesSize() >= m.getMap().get(ctrlId).getCapT()) {
						return aFinal;
					}

				}
           //logger.info("Map: it:{}, m:{}", s, m.getMap().get(ctrlId).toString());
		}
		logger.info("FinalMap: {}", aFinal.toString());;

		return aFinal;

	}

	public int min(Long swId, MapCS m, double K) {
		
		Long lt = 999999999L;
        int r = -1;

		Iterator<Integer> it = tblL.keySet().iterator();
		
		while (it.hasNext()) {
			int auxCtrl = it.next();
            try {
                Long latency = tblL.get(auxCtrl).get(swId);
                
                if ((latency + (K * m.getMap().get(auxCtrl).getSwitchesSize() )) < lt
                        && m.getMap().get(auxCtrl).getCapC() < m.getMap().get(auxCtrl).getCapT()
                        ) {
                    lt = latency;
                    r = auxCtrl;
                }
            }
            catch (java.lang.NullPointerException npe){
                logger.info("EXCEPTION... getmessage: {}, getCause:{}",
                		npe.getMessage(), 
                		npe.getCause());
                npe.printStackTrace();
                
            }
		}
        logger.info("Return: {}", r);
		return r;
	}

}
