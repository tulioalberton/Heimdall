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
import java.util.HashMap;

public class RecoverTracingTest {

	public RecoverTracingTest(){
		
	}

	public Integer recoverTracingTest(Object [] zkData, String pathKey){
		Integer value = (Integer) zkData[3];
		return value;
	}
	
}
