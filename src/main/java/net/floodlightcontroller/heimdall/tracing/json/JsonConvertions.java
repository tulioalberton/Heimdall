package net.floodlightcontroller.heimdall.tracing.json;
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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Date;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.AttachmentPoint;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.devicemanager.internal.Entity;

public class JsonConvertions {

	protected static Logger log = LoggerFactory.getLogger(JsonConvertions.class);
	private JsonConvertions() {}
	
	public static Device deserDevice(DeviceManagerImpl deviceManager, IEntityClassifierService classifier, 
			Long deviceKey, String json) {
		try {
			ArrayList<Entity> entities  = jsonToArrayEntity(json);
			IEntityClass entityClass = classifier.classifyEntity(entities.get(0));
			log.debug("Entities size: {}", entities.size());
			
			return new Device(deviceManager, deviceKey, entities.get(0), entityClass);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Entity deserEntity(String json) {
		return null;
	}
	
	private static ArrayList<Entity> jsonToArrayEntity(String json) throws IOException {
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp;
		
		Device device=null;
		MacAddress macAddress=MacAddress.NONE;
		IPv4Address ipv4Address=IPv4Address.NONE;
		IPv6Address ipv6Address=IPv6Address.NONE;
		VlanVid vlan=VlanVid.ZERO;
		DatapathId switchDPID=null;
		OFPort switchPort=null; 
		Date lastSeenTimestamp = new Date();
		AttachmentPoint ap=null;
		 
		ArrayList<Entity> entity = new ArrayList<>();
		// = new Entity(macAddress, vlan, ipv4Address, ipv6Address, switchDPID, switchPort, lastSeenTimestamp);
		
		try {
			jp = f.createParser(json);
			jp.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
			
		} catch (JsonParseException e) {
			throw new IOException(e);
		}

		jp.nextToken();
		if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected START_OBJECT");
		}
		String key = jp.getCurrentName();
		String value=null;

		while (jp.nextToken() != JsonToken.END_OBJECT) {
			key = jp.getCurrentName();
			value=null;
			if (jp.getText().equals("") || key==null)
				continue;

			
			if(jp.getCurrentToken() == JsonToken.FIELD_NAME)
				jp.nextToken();
			if(jp.getCurrentToken() == JsonToken.START_ARRAY)
				jp.nextToken();
			
			switch (key) {
			case "entityClass":						
				log.debug("Detected: {}", key);
				break;
			case "mac":
				value = jp.getValueAsString();
				macAddress=MacAddress.of(jp.getValueAsString());
				log.debug("Detected: {} field, Value:{}.", key, value);
				break;
			case "ipv4":
				value = jp.getValueAsString();
				if(jp.getValueAsString()!=null)
					ipv4Address=IPv4Address.of(jp.getValueAsString());
				log.debug("Detected: {} field, Value:{}.", key, value );
				break;
			case "ipv6":
				value = jp.getValueAsString();
				if(value!=null)
					ipv6Address=IPv6Address.of(jp.getValueAsString());
				
				log.debug("Detected: {} field, Value:{}.", key, value );
				break;
			case "vlan":
				vlan=VlanVid.ofVlan(jp.getValueAsInt());
				value = jp.getValueAsString();
				log.debug("Detected: {} field, Value:{}.", key, value);
				break;
			
			case "attachmentPoint":
				value = jp.getValueAsString();
				log.debug("Detected: attachmentPoint field, Value:{}.", key, value);
				
				while (jp.nextToken() != JsonToken.END_ARRAY) {
					
					if(jp.getCurrentToken() == JsonToken.FIELD_NAME)
						jp.nextToken();
					
					key = jp.getCurrentName();
					value=null;
					if (jp.getText().equals("") || key==null)
						continue;
					
					switch (key) {
					case "switch":
						value=jp.getValueAsString();
						switchDPID= DatapathId.of(value);
						log.debug("Detected inside AP: {} field, Value:{}.", key, value);
						break;
					case "port":
						value=jp.getValueAsString();
						switchPort=OFPort.of(jp.getValueAsInt());
						log.debug("Detected inside AP: {} field, Value:{}.", key, value);
						break;
					default:
						log.debug("Detected: {} field, Value:{}.", key, value);
						break;
					}
					
					if(switchDPID !=null && switchPort!=null){
						entity.add(new Entity(macAddress, vlan, ipv4Address, ipv6Address, switchDPID, switchPort, lastSeenTimestamp));
						log.debug("End AttachmentPoint, switch:{} port:{}", switchDPID, switchPort);
						switchDPID = null;
						switchPort = null;
					}
					
					if(value!=null)
						jp.nextToken();
					
					if(jp.getCurrentToken() == JsonToken.END_OBJECT){
						log.debug("End AttachmentPoint.");
					}	
				}
				break;
			case "lastSeen":
				value=jp.getValueAsString();
				lastSeenTimestamp=new Date(jp.getValueAsLong());
				log.debug("Detected inside AP: {} field, Value:{}.", key, value);
				break;
			
			
			default:
				log.debug("NOT Detected: {} field, Value:{}.", key, value);
				break;
			}
			
			if(jp.getCurrentToken() == JsonToken.END_ARRAY)
				jp.nextToken();
			if(value!=null)
				jp.nextToken();
		}
		log.debug("Detected: {} field", jp.getCurrentToken());
		
		return entity;
	}


	/*public static void main(String[] args) throws InterruptedException, IOException {

		String jsonDevice1 = "{\"entityClass\":\"DefaultEntityClass\",\"mac\":[\"26:1f:4d:b6:45:3a\"],\"ipv4\":[\"10.0.0.2\"],\"ipv6\":[],\"vlan\":[\"0x0\"],\"attachmentPoint\":[{\"switch\":\"00:00:00:00:00:00:00:01\",\"port\":\"1\"},{\"switch\":\"00:00:00:00:00:00:00:02\",\"port\":\"1\"},{\"switch\":\"00:00:00:00:00:00:00:03\",\"port\":\"2\"},{\"switch\":\"00:00:00:00:00:00:00:04\",\"port\":\"3\"}],\"lastSeen\":1479021690137},{\"entityClass\":\"DefaultEntityClass\",\"mac\":[\"26:1f:4d:b6:45:3a\"],\"ipv4\":[\"10.0.0.2\"],\"ipv6\":[],\"vlan\":[\"0x0\"],\"attachmentPoint\":[{\"switch\":\"00:00:00:00:00:00:00:01\",\"port\":\"1\"},{\"switch\":\"00:00:00:00:00:00:00:02\",\"port\":\"1\"},{\"switch\":\"00:00:00:00:00:00:00:03\",\"port\":\"2\"},{\"switch\":\"00:00:00:00:00:00:00:04\",\"port\":\"3\"},{\"switch\":\"00:00:00:00:00:00:00:05\",\"port\":\"3\"}],\"lastSeen\":1479021690341}\"";
		String jsonDevice2  = "{entityClass=DefaultEntityClass, mac=[00:00:00:00:00:01], ipv4=[10.0.0.1], ipv6=[], vlan=[0x0], attachmentPoint=[{switch=00:00:00:00:00:00:00:02, port=1}], lastSeen=1485884681144}";

		JsonConvertions jc = new JsonConvertions();
		ArrayList<Entity> entities;
		
		entities = jc.jsonToArrayEntity(jsonDevice1);
		log.info("FNC:1\nInput 1: {}.\nOutput: {}", jsonDevice1, entities.toString());
		
		//entities = jc.fnc1(jsonDevice2);
		//log.info("FNC:1\nInput 2: {}.\nOutput: {}", jsonDevice2, entities.toString());
		
		//log.info("FNC:2 Input 1: {}.\nOutput: {}", jsonDevice1);
		//jc.fnc2(jsonDevice1);
		
		log.info("FNC:2 Input 2: {}.\nOutput: {}", jsonDevice2);
		jc.fnc2(jsonDevice2);
					
	}*/
	
	
}
