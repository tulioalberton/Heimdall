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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.devicemanager.internal.Device;

public class DeviceDeserializer  extends JsonDeserializer<net.floodlightcontroller.devicemanager.internal.Device>{

	@Override
	public Device deserialize(JsonParser jsonParser, DeserializationContext arg1)
			throws IOException, JsonProcessingException {
		// TODO Auto-generated method stub		
		Device device=null;		
		//ObjectMapper mapper = new ObjectMapper();
		
		System.out.println("\n\n\n\n\nAM I HERE?????");
		
		try {
			device = jsonParser.readValueAs(new TypeReference<Device>() {} );
	     } catch (JsonParseException jpe) {
	         jpe.printStackTrace();
			
	     } catch (IOException ioe) {
				ioe.printStackTrace();
			}
		System.out.println("\n\n\nmacAddress=" + device.getMACAddressString());

		return device;
	}

}
