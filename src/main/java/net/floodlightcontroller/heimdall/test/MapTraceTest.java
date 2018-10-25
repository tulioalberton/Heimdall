package net.floodlightcontroller.heimdall.test;
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
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.function.Function;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.heimdall.ITarService;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.Tar;
import net.floodlightcontroller.heimdall.tracing.HashSetTracing;
import net.floodlightcontroller.heimdall.tracing.MapTracing;
import net.floodlightcontroller.linkdiscovery.Link;

public class MapTraceTest {

	protected static MapTracing<DatapathId, HashSetTracing<Link>> map;
	protected static ITarService tarService;
	
	
	static Function<HashSetTracing<Link>, String> serHST_Link = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	
	static Function<String, HashSetTracing<Link>> desHST_Link = (String str) -> {
		try {
			return new ObjectMapper().readValue(str, HashSetTracing.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	
	};
	static Function<HashSet<Link>, String> serHS_Link = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	
	static Function<String, HashSet<Link>> desHS_Link = (String str) -> {
		try {
			return new ObjectMapper().readValue(str, HashSet.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	public static void main(String[] args) {
		tarService = Tar.getInstance();
		
		System.out.println("NAME: "+tarService.getClass().getName());		
	/*	map = tarService.createMapTracing(
				new HashMap<DatapathId, HashSetTracing<Link>>(),
				"mapTest", 
				Scope.GLOBAL, 
				null, 
				serHST_Link, 
				desHST_Link);*/
		DatapathId dpid = DatapathId.of("01");
		
		map.put(dpid,
				tarService.createHashSetTracing(
				new HashSet<Link>(), 
				"mapTest", 
				Scope.GLOBAL, 
				null,
				serHST_Link, 
				desHST_Link)
				);
		//map.consolidateCoW(1L);
		
		Link l3 = new Link(DatapathId.of("03"), OFPort.of(1), DatapathId.of("01"), OFPort.of(2), U64.ZERO);
		map.get(dpid).add(new Link(DatapathId.of("01"), OFPort.of(1), DatapathId.of("03"), OFPort.of(2), U64.ZERO));
		map.get(dpid).add(new Link(DatapathId.of("02"), OFPort.of(1), DatapathId.of("02"), OFPort.of(2), U64.ZERO));
		map.get(dpid).add(l3);
		//map.get(dpid).consolidateCoW(1L);
		System.out.println("MAP size: "+map.get(dpid).size());
		
		map.get(dpid).remove(l3);
		//map.get(dpid).consolidateCoW(1L);
		System.out.println("MAP size: "+map.get(dpid).size());
		
		
	}

}
