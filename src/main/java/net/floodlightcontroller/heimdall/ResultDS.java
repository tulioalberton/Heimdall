package net.floodlightcontroller.heimdall;
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
import net.floodlightcontroller.heimdall.ITarService.DatastoreStatus;

public class ResultDS {

	private DatastoreStatus codeDS;
	private int removeCounter;
	private int writeCounter;
	private int readCounter;
	private int clearCounter;
	private int getCounter;
	private int otherCounter;
	
	public ResultDS(){
		codeDS= DatastoreStatus.READ_ONLY;
		removeCounter=0;
		writeCounter=0;
		readCounter=0;
		clearCounter=0;
		getCounter=0;
		otherCounter=0;	
	}

	public DatastoreStatus getDatastoreStatus() {
		return codeDS;
	}

	public void setDatastoreStatus(DatastoreStatus codeDS) {
		this.codeDS = codeDS;
	}

	public int getRemoveCounter() {
		return removeCounter;
	}

	public void incRemoveCounter() {
		this.removeCounter++;
	}

	public int getWriteCounter() {
		return writeCounter;
	}

	public void incWriteCounter() {
		this.writeCounter++;
	}

	public int getReadCounter() {
		return readCounter;
	}

	public void incReadCounter() {
		this.readCounter++;
	}

	public int getClearCounter() {
		return clearCounter;
	}

	public void incClearCounter() {
		this.clearCounter++;
	}

	public int getGetCounter() {
		return getCounter;
	}

	public void incGetCounter() {
		this.getCounter++;
	}

	public int getOtherCounter() {
		return otherCounter;
	}

	public void incOtherCounter() {
		this.otherCounter++;
	}
	
}
