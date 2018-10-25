package net.floodlightcontroller.core;

/**
 * /**
 * Tulio Alberton Ribeiro
 * 
 * LaSIGE - Large-Scale Informatics Systems Laboratory
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
 */



import net.floodlightcontroller.core.module.IFloodlightService;

public interface IControllerRollbackService extends IFloodlightService {

	/**
	 * Add and remove Rollback Listeners. 
	 * @param listener
	 */
	public void addListener(IControllerRollbackListener listener);
	
	public void removeListener(IControllerRollbackListener listener);
	   
}
