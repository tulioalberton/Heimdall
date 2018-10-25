package net.floodlightcontroller.heimdall.tracing;
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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.zookeeper.data.Stat;

import net.floodlightcontroller.heimdall.ITarService.Operation;
import net.floodlightcontroller.heimdall.ITarService.Scope;

public class Update {
	
	private static final AtomicLong nextCounter = new AtomicLong();

	private Long txId;
	private Long order;
	private Long threadId;
	
	private String dataStructure;
	private Operation op=Operation.NOP; 
	private String key=null;
	private String v1=null;
	private String v2=null;
	private Scope scope;
	private Stat stat=new Stat();
	private String secondLevel=null;
		
	/**
	 * Used for JSON de-serialize.
	 */	
	public Update(){}
	
	public Update(Update up) {
		this.order = up.getTimeStamp();
		this.txId = up.getTxId();
		this.threadId = up.getThreadId();
		this.dataStructure = up.getDataStructure();
		this.op = up.getOp();
		this.key = up.getKey();
		this.v1 = up.getV1();
		this.v2 = up.getV2();
		this.scope = up.getScope();
		this.stat = up.getStat();
	}
	
	public Update(String dataStructure, Operation op, String key, String v1, Scope scope, Long threadId, Long txId) {
		super();
		this.order = nextCounter.incrementAndGet();//System.nanoTime();
		this.txId = txId;		
		this.threadId = threadId;
		this.dataStructure = dataStructure;
		this.op = op;
		this.key = key;
		this.v1 = v1;
		this.v2 = null;
		this.scope = scope;
		this.stat = new Stat();
	}
	
	
	public Long getTxId() {
		return txId;
	}

	public void setTxId(Long txId) {
		this.txId = txId;
	}

	public Long getThreadId() {
		return threadId;
	}

	public void setThreadId(Long threadId) {
		this.threadId = threadId;
	}


	public void setTimeStamp(Long t) {
		this.order= t;
	}
	public Long getTimeStamp() {
		return this.order;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public void setV1(String v1) {
		this.v1 = v1;
	}

	public void setV2(String v2) {
		this.v2 = v2;
	}

	public void setStat(Stat s) {
		this.stat = s;
	}
	public void setSecondLevel(String sIn){
		this.secondLevel = sIn;
	}

	public String getDataStructure() {
		return dataStructure;
	}
	public Operation getOp() {
		return op;
	}
	public String getKey() {
		return key;
	}
	
	public String getV1() {
		return v1;
	}
	
	public String getV2() {
		return v2;
	}
	
	public Scope getScope() {
		return scope;
	}
	public Stat getStat() {
		return stat;
	}
	public String getSecondLevel(){
		return this.secondLevel;
	}
	public boolean isSecondLevel(){
		return secondLevel != null;
	}
	
	@Override
	public String toString(){
		
		if(v2 != null)
			return "\nUpdate ["
				+ "Data Structure:" +dataStructure 
				+ ", Operation:" +op
				+ ", Key:" + key 
				+ ", Value:" + v1 
				+ ", Value:" + v2 
				+ ", Scope:" + scope
				+ ", Version:" + stat.getVersion() 
				+ ", TxId:" + txId
				+ ", Timestamp:" + order
				+ ", ThreadId:" + threadId+ "]";
		else 
			return "\nUpdate ["
					+ "Data Structure:" +dataStructure 
					+ ", Operation:" +op
					+ ", Key:" + key 
					+ ", Value:" + v1 
					+ ", Version:" + stat.getVersion() 
					+ ", TxId:" + txId
					+ ", Timestamp:" + order 
					+ ", ThreadId:" + threadId 
					+ ", Scope:" + scope +"]";
		
	}
	
}
