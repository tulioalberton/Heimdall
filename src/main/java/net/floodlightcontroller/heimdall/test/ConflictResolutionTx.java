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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.heimdall.ConflictResolution;
import net.floodlightcontroller.heimdall.ITarService.Operation;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.tracing.Update;

public class ConflictResolutionTx {

	ConcurrentHashMap<Long, TreeMap<Long, Update>> nextBatch = new ConcurrentHashMap<>();
	HashMap<Long, TreeMap<Long, Update>> finishedPacketIn = new HashMap<>();
	TreeMap<Long, Update> refactoredPacketIn = new TreeMap<>();
	ConflictResolution conflict = new ConflictResolution();
	static ConcurrentHashMap<String, Update> statDS = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, Update> lastCK = new ConcurrentHashMap<>();
	ConcurrentHashMap<Long, HashSet<String>> batchKeys = new ConcurrentHashMap<>();

	public ConflictResolutionTx() {

		/**
		 * Stat DS is indexed by key	
		 */
		statDS.put("A", new Update("TEST", Operation.PUT, "A", null, Scope.GLOBAL, 10L, 22L));
		statDS.put("B", new Update("TEST", Operation.PUT, "B", null, Scope.GLOBAL, 10L, 24L));
		statDS.put("C", new Update("TEST", Operation.PUT, "C", null, Scope.GLOBAL, 10L, 26L));
		statDS.put("D", new Update("TEST", Operation.PUT, "D", null, Scope.GLOBAL, 10L, 24L));

	}

	public static void main(String[] args) {
		
		ConflictResolutionTx crt3 = new ConflictResolutionTx();		
		System.out.println("###################    Test Valid Just One TX, Assert: true for one TX.######");		
		crt3.test_ValidJustOneTx(); 
		System.out.println("#########################################################");
	}
	
	
	public void test_ValidJustOneTx() {
		finishedPacketIn.clear();
		try {
			// ################### VERIFY Tx Validity ###########################
			Update upB1 = new Update("TEST", Operation.PUT, "B", null, Scope.GLOBAL, 10L, 25L);
			upB1.getStat().setVersion(1);
			finishedPacketIn.putIfAbsent(upB1.getThreadId(), new TreeMap<>());
			finishedPacketIn.get(upB1.getThreadId()).put(upB1.getTimeStamp(), upB1);

			for (int i = 1; i < 3; i++) {
				Update upA1 = new Update("TEST", Operation.PUT, "A", null, Scope.GLOBAL, 10L, 25L);
				upA1.getStat().setVersion(i);
				finishedPacketIn.putIfAbsent(upA1.getThreadId(), new TreeMap<>());
				finishedPacketIn.get(upA1.getThreadId()).put(upA1.getTimeStamp(), upA1);
			}
			
			Update upC1 = new Update("TEST", Operation.PUT, "A", null, Scope.GLOBAL, 12L, 27L);
			upC1.getStat().setVersion(1);
			finishedPacketIn.putIfAbsent(upC1.getThreadId(), new TreeMap<>());
			finishedPacketIn.get(upC1.getThreadId()).put(upC1.getTimeStamp(), upC1);
			
			Update upD1 = new Update("TEST", Operation.PUT, "C", null, Scope.GLOBAL, 14L, 26L);
			upD1.getStat().setVersion(1);
			finishedPacketIn.putIfAbsent(upD1.getThreadId(), new TreeMap<>());
			finishedPacketIn.get(upD1.getThreadId()).put(upD1.getTimeStamp(), upD1);
			
			Update upD2 = new Update("TEST", Operation.PUT, "D", null, Scope.GLOBAL, 14L, 26L);
			upD2.getStat().setVersion(1);
			finishedPacketIn.putIfAbsent(upD2.getThreadId(), new TreeMap<>());
			finishedPacketIn.get(upD2.getThreadId()).put(upD2.getTimeStamp(), upD2);
			
			
			System.out.println("INITIAL BATCH: " + finishedPacketIn);
						
			conflict.verifyLocalBatchConflict(finishedPacketIn);
			conflict.verifyVersionDistance();
			conflict.verifyVersionDistanceAtBeginning(statDS);
			conflict.verifyTxValidity(statDS);
			conflict.finalizeBatch();
			
			
			System.out.println("FINAL BATCH: " + conflict.getUpdates());
			System.out.println("REMOVED FROM FINAL BATCH: " + conflict.getNotValidTx());
			System.out.println("" + conflict);
			
		
		} catch (Exception e) {
			System.out.println("Problem during executing batch conflict resolution. Aborting batch.");
			conflict.setBatchAbort(true);
			e.printStackTrace();
		}
	}
	
	
	
}
