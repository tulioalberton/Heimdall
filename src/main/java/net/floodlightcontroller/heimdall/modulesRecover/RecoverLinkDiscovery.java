package net.floodlightcontroller.heimdall.modulesRecover;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.std.MapProperty;

import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.heimdall.tracing.HashSetTracing;
import net.floodlightcontroller.heimdall.tracing.MapTracing;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;

public class RecoverLinkDiscovery  {
	
	private static Logger logger;
	private ObjectMapper mapper; 
	
	public RecoverLinkDiscovery(){
		mapper = new ObjectMapper();
		logger = LoggerFactory.getLogger(RecoverLinkDiscovery.class);	
	}
	
	public Map<DatapathId, HashSet<Link>> recoverLDSwitchLink(Object [] zkData, String pathKey){
		Map<DatapathId, HashSet<Link>> switchLinks = new HashMap<DatapathId, HashSet<Link>>();
		
		switch (zkData[1].toString()) {
		case "put(K,V)":
			logger.debug("Recovering LD:portLinks: {}, zkData[3]:{}",zkData[2],zkData[3]);
			String[] sData = zkData[2].toString().split(";");
			DatapathId key = null;
			switchLinks.put(key, new HashSet<Link>());
			List<HashMap<String,ArrayList<String>>> al = (ArrayList<HashMap<String, ArrayList<String>>>) zkData[3];
			for (int i = 0; i < al.size(); i++) {
				Map<String,ArrayList<String>> map = (HashMap<String, ArrayList<String>>) al.get(i);
				DatapathId src = DatapathId.of(map.get("src").get(0));
				OFPort srcPort = OFPort.of(Integer.parseInt(map.get("srcPort").get(0)));
				DatapathId dst = DatapathId.of(map.get("dst").get(0));
				OFPort dstPort = OFPort.of(Integer.parseInt(map.get("dstPort").get(0)));
				Link l2 = new Link(src, srcPort, dst, dstPort, U64.ZERO);
				switchLinks.get(key).add(l2);
				logger.debug("Link Discovery SwitchLink recovered: {}", l2);
			}			
			return switchLinks;
			default:
		}
		return switchLinks;
	}
	
	public Map<NodePortTuple, HashSet<Link>> recoverLDPortLinks(Object [] zkData, String pathKey){
		Map<NodePortTuple, HashSet<Link>> portLinks = new HashMap<NodePortTuple, HashSet<Link>>();
		switch (zkData[1].toString()) {
		case "put(K,V)":
			logger.debug("Recovering LD:portLinks: {}, zkData[3]:{}",zkData[2],zkData[3]);
			String[] sData = zkData[2].toString().split(";");
			NodePortTuple key = new NodePortTuple(DatapathId.of(sData[0]), OFPort.of(Integer.parseInt(sData[1])));
			portLinks.put(key, new HashSet<Link>());
			List<HashMap<String,ArrayList<String>>> al = (ArrayList<HashMap<String, ArrayList<String>>>) zkData[3];
			for (int i = 0; i < al.size(); i++) {
				Map<String,ArrayList<String>> map = (HashMap<String, ArrayList<String>>) al.get(i);
				DatapathId src = DatapathId.of(map.get("src").get(0));
				OFPort srcPort = OFPort.of(Integer.parseInt(map.get("srcPort").get(0)));
				DatapathId dst = DatapathId.of(map.get("dst").get(0));
				OFPort dstPort = OFPort.of(Integer.parseInt(map.get("dstPort").get(0)));
				Link l2 = new Link(src, srcPort, dst, dstPort, U64.ZERO);
				portLinks.get(key).add(l2);
				logger.debug("Link Discovery PortLink recovered: {}", l2);
			}			
			return portLinks;
			default:
		}
		return portLinks;
	}
	
	public Object[] recoverLDLinks(Object [] zkData, String pathKey){
		Object[] linkAndLinkInfo= new Object[2];
		
		// zkData[0] = tableName
		// zkData[1] = modification type
		// zkData[2] = key of modification
		// zkData[3, ..., n] = values
		switch (zkData[1].toString()) {
			case "put(K,V)":
				logger.debug("Recovering Link Discovery Links: {}",zkData[2]);

				Object keySplit[] = zkData[2].toString().split(";");
				
				DatapathId src = DatapathId.of(""+keySplit[0]);
				OFPort srcPort = OFPort.of(Integer.parseInt(keySplit[1].toString()));
				
				DatapathId dst = DatapathId.of(""+keySplit[2]);
				OFPort dstPort = OFPort.of(Integer.parseInt(keySplit[3].toString()));
				
				U64 latency = U64.of(Long.parseLong(""+keySplit[4].toString()));
				
				//U64 latency = U64.parseHex(keySplit[4].toString());
				
				Link link = new Link(src, srcPort, dst, dstPort, latency);
				
				String[] valueSplit = zkData[3].toString().split(";");
				Date firstSeenTime = new Date(Long.parseLong(""+valueSplit[0]));
				
				Date currentLatency=null; 
				try {
					currentLatency= new Date(Long.parseLong(""+valueSplit[1]));
				} catch (Exception e) {
					logger.debug("currentLatency=null: {}",zkData[3]);
					currentLatency = new Date();
				}
				Date unicastValidTime =	new Date(Long.parseLong(""+valueSplit[2]));
				
				Date multicastValidTime=null;
				try {
					multicastValidTime = new Date(Long.parseLong(""+valueSplit[3]));	
				} catch (Exception e) {
					logger.debug("multicastValidTime=null: {}",zkData[3]);
					multicastValidTime = new Date();
				}
				
				LinkInfo lInfo = new LinkInfo(firstSeenTime, unicastValidTime, multicastValidTime);
				
				lInfo.setFirstSeenTime(firstSeenTime);
				lInfo.setMulticastValidTime(multicastValidTime);
				lInfo.setUnicastValidTime(unicastValidTime);
				
				//LinkInfo lInfo = new LinkInfo(new Date(), new Date(), new Date());
				
				linkAndLinkInfo[0] = link;
				linkAndLinkInfo[1] = lInfo;
				
				break;
			default:
		}
		return linkAndLinkInfo;
	}
	

}
