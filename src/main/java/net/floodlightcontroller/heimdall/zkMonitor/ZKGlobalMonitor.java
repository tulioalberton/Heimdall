package net.floodlightcontroller.heimdall.zkMonitor;
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
 * The heimdall.heuristic.Controller class, defines the capacity of each controller, and shall be the same. 
 * 
 */
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.heimdall.PersistentDomain;
import net.floodlightcontroller.heimdall.Tar;
import net.floodlightcontroller.heimdall.heuristicLatency.HeuristicCentralized;
import net.floodlightcontroller.heimdall.heuristicLatency.MapCS;

public class ZKGlobalMonitor implements Watcher {
	
	private static Logger log;
	private ObjectMapper mapper = new ObjectMapper();
	private HashMap<Integer, ArrayList<Long>> lastMap;
	private ZooKeeper zk=null;
	
	public ZKGlobalMonitor() {
		log = LoggerFactory.getLogger(ZKGlobalMonitor.class);		
		lastMap = new HashMap<>();
	}
	
	public void setZK(ZooKeeper zk){
		this.zk = zk;
	}

	@Override
	public void process(WatchedEvent event) {
		
		if(event.getPath() == null)
			return;		
		
		String latencyIndividualDir = PersistentDomain.getInstance().getDir("latencyIndividual");
		String latencyConsolidate = PersistentDomain.getInstance().getDir("latencyConsolidate");
		String rootNode = PersistentDomain.getInstance().getDir("");//the default case returns rootNode
		
		KeeperState ks = event.getState();
		EventType et = event.getType();
		
		
		if(event.getPath().startsWith(latencyIndividualDir)){
			log.trace("Individual latency detected. Starting time");
			long startTime = System.nanoTime();			
			if(Tar.getInstance().imLeader()){
				lastMap = execAlgAsMaster(latencyIndividualDir, lastMap);
			}
			
			if (log.isTraceEnabled()) {				
				DecimalFormat df = new DecimalFormat("#.########");
				df.setRoundingMode(RoundingMode.HALF_UP);
				df.setMinimumFractionDigits(3);
				df.setMaximumFractionDigits(9);
				long endTime = System.nanoTime();
				long elapsed = endTime - startTime;
				log.trace("Time elapsed: {} (seconds)", df.format(elapsed / 1000000000.0));
			}
		}
		if (event.getPath().startsWith(latencyConsolidate)){
			log.trace("Consolidated latency detected.");
			if(!Tar.getInstance().imLeader())
				lastMap = execAlgAsReplica(latencyConsolidate, lastMap);
		}		
		if(event.getPath().equals(rootNode+"/masterLeader") 
				&& et.equals(EventType.NodeDeleted)){
			log.info("Leader fell down. Trying become leader.");
			if(Tar.getInstance().bootStrapping())
				lastMap = execAlgAsMaster(latencyIndividualDir, lastMap);
			
		}
		if(event.getPath().equals(rootNode+"/activeCtrl")){
			log.info("Controller crash/join event.");
			
			if(Tar.getInstance().imLeader())
				lastMap = execAlgAsMaster(latencyIndividualDir, lastMap);
			
		}
				
		if(event.getPath().equals(rootNode+"/concurrenceAware")){
			log.info("Concurrence aware detected.");
		}
		
	}
	
	public HashMap<Integer, ArrayList<Long>> execAlgAsMaster(
				String latencyIndividualDir, 
				HashMap<Integer, ArrayList<Long>> lastMap){
		
			
			HashMap<Integer, HashMap<Long, Long>> tblLatency = new HashMap<>();
			StringBuffer sb = new StringBuffer();
			
			ArrayList<Integer> ctrlsOut = new ArrayList<>();
			ArrayList<Long> toAssignOut = new ArrayList<>();
			byte[] dataToRecover = null;
			HashMap<Long, Long> zkData2 = new HashMap<Long, Long>();
			List<String> latencyCtrl = new ArrayList<>();
			try {
				latencyCtrl = zk.getChildren(latencyIndividualDir, this);
			} catch (KeeperException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			Iterator<String> it = latencyCtrl.iterator();
			while (it.hasNext()) {
				int ctrl = Integer.parseInt(it.next());
				tblLatency.put(ctrl, new HashMap<Long, Long>());
				ctrlsOut.add(ctrl);
				
				try {
					dataToRecover = zk.getData(latencyIndividualDir+"/"+ctrl, this, null);
					zkData2 = mapper.readValue(dataToRecover, new TypeReference<HashMap<Long, Long>>() {});

					Iterator<Long> it2 = zkData2.keySet().iterator();
					while(it2.hasNext()){
						Long sw = Long.parseLong(""+it2.next());
						Long lt = zkData2.get(sw);
						tblLatency.get(ctrl).put(sw, lt);

						if (!toAssignOut.contains(sw))
							toAssignOut.add(sw);
					}

				}
				catch (JsonMappingException  | 
		        		JsonParseException | 
		        		KeeperException | 
		        		InterruptedException e) {
		            e.printStackTrace();
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
						
			}
			
			if(log.isDebugEnabled())
				sb.append("\n   ======================================================================");
			
			if(log.isTraceEnabled()) {
				sb.append("\n\ttblLatency: {}" + tblLatency.toString());
				sb.append("\n\tctrlsOut: {}" + ctrlsOut.toString());
				sb.append("\n\ttoAssignOut: {}" + toAssignOut.toString());
			}
			MapCS m = new MapCS(ctrlsOut, toAssignOut.size());			
			HeuristicCentralized h = new HeuristicCentralized(tblLatency, 0, m);			
			HashMap<Integer, ArrayList<Long>> newMap = h.CalcMap(lastMap);
			lastMap.putAll(newMap);			
			
			if(log.isDebugEnabled()) {
				sb.append("\n\tMaster Exec. Algorithm, Result: " + newMap.toString());
				sb.append("\n   ======================================================================");
				log.debug("{}",sb);
			}
			
			
			if(!newMap.isEmpty())
				Tar.getInstance().activateSwitches(newMap);
		
			PersistentDomain.getInstance().updateLatencyConsolidateData(tblLatency);
           
            return newMap;				
	}
	
	public HashMap<Integer, ArrayList<Long>>  execAlgAsReplica(
			String latencyIndividualDir, 
			HashMap<Integer, ArrayList<Long>> lastMap){
		
		StringBuffer sb = new StringBuffer();
		HashMap<Integer, HashMap<Long, Long>> tblLatency = new HashMap<>();
        byte[] dataToRecover = null;
        ArrayList<Integer> ctrlsOut = new ArrayList<>();
        ArrayList<Long> toAssignOut = new ArrayList<Long>();
        HashMap<Integer, HashMap<Long, Long>> zkData;
        String latencyC = PersistentDomain.getInstance().getDir("latencyConsolidate");
        try {
            dataToRecover = zk.getData(latencyC, this, null);
            zkData = mapper.readValue(dataToRecover, 
            		new TypeReference<HashMap<Integer, HashMap<Long, Long>> >() {});

            Iterator<Integer> it = zkData.keySet().iterator();

            while (it.hasNext()){
                int ctrl = it.next();
                tblLatency.put(ctrl, new HashMap<>(zkData.get(ctrl)));
                ctrlsOut.add(ctrl);
                Iterator<Long> it2 = zkData.get(ctrl).keySet().iterator();

                while(it2.hasNext()){
                    Long sw = Long.parseLong(""+it2.next());
                    if (!toAssignOut.contains(sw))
                        toAssignOut.add(sw);
                }
            }
        } catch (JsonMappingException  | 
        		JsonParseException | 
        		KeeperException | 
        		InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        if(log.isDebugEnabled())
			sb.append("\n   ======================================================================");
		
		
        
        MapCS m = new MapCS(ctrlsOut, toAssignOut.size());
        HeuristicCentralized h = new HeuristicCentralized(tblLatency, 0, m);
        
        if(log.isTraceEnabled()) {
			sb.append("\n\ttblLatency: {}" + tblLatency.toString());
			sb.append("\n\tctrlsOut: {}" + ctrlsOut.toString());
			sb.append("\n\ttoAssignOut: {}" + toAssignOut.toString());
			sb.append("\n\tControllerID: "+Tar.getInstance().getControllerId()+", Map: "+m.toString());
        }
        
        HashMap<Integer, ArrayList<Long>> newMap = h.CalcMap(lastMap);
        lastMap.putAll(newMap);
        
        if(log.isDebugEnabled()) {
        	sb.append("\n\tReplica Exec. Algorithm, Result: " + newMap.toString());
        	sb.append("\n   ======================================================================");
        	log.debug("{}",sb);
        }
        
        Tar.getInstance().activateSwitches(newMap);
        return newMap;		
	}
	
}
