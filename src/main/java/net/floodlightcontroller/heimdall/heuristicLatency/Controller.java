package net.floodlightcontroller.heimdall.heuristicLatency;
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
import java.util.ArrayList;

import org.projectfloodlight.openflow.types.DatapathId;

public class Controller{

	private int ctrlID;
	private int capT;
	private int capC;
	private ArrayList<DatapathId> switches; //<switch> 

	public Controller(){}
	
	public Controller(int id, 
			int capT,
			int capC,
			ArrayList<DatapathId> sws){

		this.ctrlID  = id;
		this.capT = capT;
		this.capC = capC;
		this.switches = sws;
	}
	public double getctrlID() {
		return this.ctrlID;
	}
	public void setctrlID(int id) {
		this.ctrlID = id;
	}
	
	public ArrayList<DatapathId> getSwitches() {
		return this.switches;
	}
	public void setmSwitches(ArrayList<DatapathId> sws) {
		this.switches = sws;
	}

	@Override
	public String toString() {
		
		String r = "CtrlID: "+ctrlID+
				"Sw: "+switches.toString();		
		return r;
	}
	
	public int getCapT() {
		return capT;
	}
	public void setCapT(int cap) {
		this.capT = cap;
	}
	
	public int getCapC() {
		return capC;
	}
	public void setCapC(int cap) {
		this.capC = cap;
	}
	public void incCapC() {
		this.capC++;
	}
	
	public int getSwitchesSize(){
		return switches.size();
	}


}	
