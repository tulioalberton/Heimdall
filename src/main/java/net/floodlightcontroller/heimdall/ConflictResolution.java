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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.heimdall.tracing.Update;

public class ConflictResolution {

	private static final Logger log = LoggerFactory.getLogger(ConflictResolution.class);

	private HashSet<String> allKeys;
	private TreeMap<Long, Update> allUpdates;
	private TreeMap<Long, Update> notValidUpdates;
	private HashSet<Long> conflictedThreads;
	private HashSet<String> conflictedKeys;
	
	private HashSet<Update> notValidTx;

	private boolean conflict;
	private boolean canISave;
	private boolean localConflict;
	private boolean versionDistance;
	private boolean versionDistanceAtBeginning;
	private boolean batchAbort;
	 
	private HashMap<Long, TreeMap<Long, Update>> finalBatch;
	
	/**
	 * If we detect a version conflict into second verification we need abort the
	 * batch
	 */
	private boolean secondVerification;

	public ConflictResolution() {
		conflict = false;
		localConflict = false;
		versionDistance = false;
		versionDistanceAtBeginning = false;
		batchAbort = false;
		notValidTx = new HashSet<>();
		conflictedKeys = new HashSet<>();
		allKeys = new HashSet<>();
		conflictedThreads = new HashSet<>();
		allUpdates = new TreeMap<>();
		notValidUpdates = new TreeMap<>();
		canISave = true;
	}

	public boolean isVersionDistanceAtBeginning() {
		return versionDistanceAtBeginning;
	}

	public void setVersionDistanceAtBeginning(boolean versionDistanceAtBeginning) {
		this.versionDistanceAtBeginning = versionDistanceAtBeginning;
	}
		
	public HashSet<Update> getNotValidTx() {
		return notValidTx;
	}

	public void addNotValidTx(Update up) {
		this.notValidTx.add(up);
		this.notValidUpdates.put(up.getTimeStamp(), up);
	}

	public boolean isVersionDistance() {
		return versionDistance;
	}

	public void setVersionDistance(boolean versionDistance) {
		this.versionDistance = versionDistance;
	}

	public Collection<Update> getOrderedUpdates() {
		return this.allUpdates.values();
	}
	
	public void addUpdate(Update update) {
		this.allUpdates.put(update.getTimeStamp(), update);
	}

	public TreeMap<Long, Update> getUpdates() {
		return allUpdates;
	}

	
	public TreeMap<Long, Update> getNotValidUpdates() {
		return notValidUpdates;
	}

	public void addNotValidUpdate(Update notValidUpdate) {
		this.notValidUpdates .put(notValidUpdate.getTimeStamp(), notValidUpdate);
	}

	public boolean isBatchAbort() {
		return batchAbort;
	}

	public void setBatchAbort(boolean batchAbort) {
		this.batchAbort = batchAbort;
	}

	public boolean isSecondVerification() {
		return secondVerification;
	}

	public void setSecondVerification(boolean secondVerification) {
		this.secondVerification = secondVerification;
	}

	public void addIntoAllKeys(String key) {
		this.allKeys.add(key);
	}

	public HashSet<String> getAllKeys() {
		return allKeys;
	}

	public boolean isLocalConflict() {
		return localConflict;
	}

	public void setLocalConflict(boolean localConflict) {
		this.localConflict = localConflict;
	}

	public void addConflictedKey(String key) {
		this.conflictedKeys.add(key);
	}

	public HashSet<String> getConflictedKey() {
		return conflictedKeys;
	}

	public boolean isConflict() {
		return conflict;
	}

	public boolean isCanISave() {
		return canISave;
	}

	public void setCanISave(boolean canISave) {
		this.canISave = canISave;
	}

	public void setConflict(boolean conflict) {
		this.conflict = conflict;
	}

	public HashSet<Long> getConflictedThreads() {
		return conflictedThreads;
	}

	public void addConflictedThread(Long thread) {
		this.conflictedThreads.add(thread);
	}

	public String toString() {
		return "ConflictResolution[ "
				+ "(canISave:" + canISave + "), "
				+ "(is Conflict:" + conflict + "), "
				+ "(is versionDistance:" + versionDistance + "), "
				+ "(is versionDistanceAtBegin:" + versionDistanceAtBeginning + "), "
				+ "(is localConflict:" + localConflict + "), "
				+ "(is batchAbort:" + batchAbort + ")]";

	}

	public ConflictResolution verifyLocalBatchConflict(HashMap<Long, TreeMap<Long, Update>> finishedPacketIn)
			throws Exception {

		finalBatch = new HashMap<>(finishedPacketIn);
		
		HashSet<String> verifyConflictKey = new HashSet<>();
		Iterator<Long> itThread = finishedPacketIn.keySet().iterator();
		while (itThread.hasNext()) {
			Long thread = (Long) itThread.next();
			Iterator<Update> itUpdate = finishedPacketIn.get(thread).values().iterator();
			while (itUpdate.hasNext()) {
				Update update = (Update) itUpdate.next();
				if (verifyConflictKey.add(update.getDataStructure() + "" + update.getKey()) == false) {
					addConflictedKey(update.getKey());
					setLocalConflict(true);
				}
				addUpdate(update);
				addIntoAllKeys(update.getKey());
			}
		}
		return this;
	}


	public ConflictResolution verifyTxValidity(ConcurrentHashMap<String, Update> statDS)
			throws Exception {
		
		Iterator<Update> itUp = getOrderedUpdates().iterator();
		while (itUp.hasNext()) {
			Update update = (Update) itUp.next();
			if(statDS.containsKey(update.getKey())) {
				if(statDS.get(update.getKey()).getTxId() >= update.getTxId() ) {
					addNotValidTx(update);
					addNotValidUpdate(update);					
				}
			}
		}
		
		return this;
	}


	public ConflictResolution verifyVersionDistance() throws Exception {

		HashMap<String, TreeMap<Long, Update>> separateAndOrderedKeys = new HashMap<>();
		Iterator<Update> itUP = getOrderedUpdates().iterator();
		while (itUP.hasNext()) {
			Update update = (Update) itUP.next();
			separateAndOrderedKeys.putIfAbsent(update.getKey(), new TreeMap<>());
			separateAndOrderedKeys.get(update.getKey()).put(update.getTimeStamp(), update);
		}

		// Verifying distance greater than 1:
		boolean versionDistanceGreaterThanOne = false;
		Update anterior = null;
		Update current = null;
		Iterator<String> itKey = separateAndOrderedKeys.keySet().iterator();

		while (itKey.hasNext()) {
			String key = (String) itKey.next();

			TreeMap<Long, Update> toVerify = separateAndOrderedKeys.get(key);

			Iterator<Long> timeStampIt = toVerify.keySet().iterator();
			while (timeStampIt.hasNext()) {
				Long timeStamp = (Long) timeStampIt.next();
				if (anterior == null) {
					anterior = toVerify.get(timeStamp);
				} else {
					current = toVerify.get(timeStamp);
					if (current.getStat().getVersion() - anterior.getStat().getVersion() == 1
							&& versionDistanceGreaterThanOne == false) {
						anterior = current;
					} else {
						addNotValidTx(current);
						//getUpdates().remove(current.getTimeStamp());
						versionDistanceGreaterThanOne = true;
						setConflict(true);
						setVersionDistance(true);
					}

				}
			}
			anterior = null;
			versionDistanceGreaterThanOne = false;
		}

		return this;
	}

	public ConflictResolution verifyVersionDistanceAtBeginning(ConcurrentHashMap<String, Update> statDS)
			throws Exception {

		HashMap<String, TreeMap<Long, Update>> separateAndOrderedKeys = new HashMap<>();
		Iterator<Update> itUP = getOrderedUpdates().iterator();
		while (itUP.hasNext()) {
			Update update = (Update) itUP.next();
			separateAndOrderedKeys.putIfAbsent(update.getKey(), new TreeMap<>());
			separateAndOrderedKeys.get(update.getKey()).put(update.getTimeStamp(), update);
		}

		Iterator<String> itKey = separateAndOrderedKeys.keySet().iterator();
		while (itKey.hasNext()) {
			String key = (String) itKey.next();
			TreeMap<Long, Update> toVerify = separateAndOrderedKeys.get(key);

			if (statDS.containsKey(toVerify.firstEntry().getValue().getKey())) {
				if (toVerify.firstEntry().getValue().getStat().getVersion()
						- statDS.get(toVerify.firstEntry().getValue().getKey()).getStat().getVersion() != 1) {
					setConflict(true);
					setVersionDistanceAtBeginning(true);
					setBatchAbort(true);
					addNotValidTx(toVerify.firstEntry().getValue());
					addConflictedKey(toVerify.firstEntry().getValue().getKey());
				}
			}
		}
		return this;
	}
	
	public ConflictResolution finalizeBatch() {
		
		
		Iterator<Update> notValidTX = getNotValidTx().iterator();
		while (notValidTX.hasNext()) {
			Update update = (Update) notValidTX.next();
			if(finalBatch.containsKey(update.getThreadId())){
				finalBatch.remove(update.getThreadId());				
			}
			//getUpdates().remove(update.getTimeStamp());
		}
		getUpdates().clear();
		
		Iterator<Long> itThread= finalBatch.keySet().iterator();
		while (itThread.hasNext()) {
			Long thread = (Long) itThread.next();
			this.allUpdates.putAll(finalBatch.get(thread));
		}
		return this;
	}

}
