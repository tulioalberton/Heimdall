package net.floodlightcontroller.core;

import java.util.HashMap;
import java.util.TreeMap;

import org.projectfloodlight.openflow.protocol.OFMessage;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.heimdall.tracing.Update;

/**
 * Tulio Alberton Ribeiro
 * 
 * LaSIGE - Large-Scale Informatics Systems Laboratory
 * 
 * 03/2016
 * 
 * Without warrant
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may \n
 *    not use this file except in compliance with the License. You may obtain \n
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         
 *   Each module registered will be informed about re-initiated packetIn.      
 */

public interface IControllerRollbackListener{

	public void onSelfRollback(long threadId);
	
	public void onBatchRollback(TreeMap<Long, Update> orderedFinalPacketIn);
	
	public void onDataStoreRollback(TreeMap<Long, Update> orderedFinalPacketIn);
	
	public String getName();
	
}
