package net.floodlightcontroller.simpleft;
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
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class UtilDurable {
	private static Logger logger;
	
	public UtilDurable(){
		logger = LoggerFactory.getLogger(UtilDurable.class);
	}
	
	public OFRoleReply setSwitchRole(IOFSwitch sw, OFControllerRole role) {
		try {	
			ListenableFuture<OFRoleReply> future = sw.writeRequest(sw.getOFFactory().buildRoleRequest()
					.setGenerationId(U64.ZERO)
					.setRole(role)
					.build());
			return future.get(10, TimeUnit.SECONDS);

		} catch (Exception e) {
			logger.error("Failure setting switch {} role to {}.", sw.toString(), role.toString());
			logger.error(e.getMessage());
		}
		return null;
	}

}
