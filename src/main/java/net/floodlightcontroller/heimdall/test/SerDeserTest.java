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
import java.util.function.Function;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.python.antlr.PythonParser.return_stmt_return;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.linkdiscovery.Link;

public class SerDeserTest {

	
	public static String strLink = new String("Link [src=00:00:00:00:00:00:00:02 outPort=2, dst=00:00:00:00:00:00:00:04, inPort=3, latency=0]");
	
	public static Link l = new Link(DatapathId.of("00:00:00:00:01:02:03:02"), OFPort.of(100), DatapathId.of("00:00:00:00:05:03:01:01"), OFPort.of(15), U64.ZERO);
	
	public static void main(String[] args) {
		
		System.out.println("MY STRING LINK: " + strLink);
		System.out.println("MY LINK: " + l);

		System.out.println("Serializing LINK. ");
		String serLink = serializer_KEY_Link.apply(l);
		System.out.println("After Serializing LINK.\n " + serLink);
		
		System.out.println("DeSerializing LINK. ");
		Link desLink = deserializer_KEY_Link.apply(serLink);
		System.out.println("After DeSerializing LINK.\n " + desLink);
		
		
		
		/*System.out.println("Trying deser String Link.");
		
		Link l2 = deserializer_KEY_Link.apply(strLink);*/
		
	}

	
	
	static Function<Link, String> serializer_KEY_Link = (info) -> {
		return info.toKeyString();
	};
	
	static Function<String, Link> deserializer_KEY_Link = (String str) -> {
		
		System.out.println("deserializing_KEY_Link: " + str);
		if(str==null) {
			return new Link();
		}
		else {
			String[] v = str.split(";");
			/*System.out.println("V 0: "+ v[0]);
			System.out.println("V 1: "+ v[1].toString());
			System.out.println("V 2: "+ v[2]);
			System.out.println("V 3: "+ v[3]);*/
			
			 DatapathId src = DatapathId.of(""+v[0]);
			 OFPort srcPort = OFPort.of(Integer.parseInt(""+v[1]));
			 DatapathId dst = DatapathId.of(""+v[2]);;
			 OFPort dstPort = OFPort.of(Integer.parseInt(""+v[3]));;
			 
			 return new Link(src, srcPort, dst, dstPort, U64.ZERO);
		}
	
	};

}
