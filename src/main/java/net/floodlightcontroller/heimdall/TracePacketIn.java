
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.python.antlr.PythonParser.return_stmt_return;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.SwitchStatus;
import net.floodlightcontroller.heimdall.ITarService.PipelineStatus;
import net.floodlightcontroller.heimdall.test.Z1.SwitchLoad;
import net.floodlightcontroller.heimdall.tracing.Update;

public class TracePacketIn {

	private static TracePacketIn instanceTrace = null;

	private static final AtomicLong txId = new AtomicLong();
	
	private static Logger log;
	private static ConcurrentHashMap<Long, Queue<OFMessage>> msgBuffer;
	private static ConcurrentHashMap<Long, TreeMap<Long, Update>> memoryChanges;
	private static ConcurrentHashMap<Long, TreeMap<Long, Update>> finishedMemoryChanges;
	private static ConcurrentHashMap<Long, PipelineStatus> status;
	private static ConcurrentHashMap<Long, Long> threadTxId;
	private static ConcurrentHashMap<Long, Boolean> isWritePacketIn;
	private ObjectMapper mapper;
	//private final ReentrantReadWriteLock lock;
	
	public TracePacketIn() {
		msgBuffer = new ConcurrentHashMap<Long, Queue<OFMessage>>();
		memoryChanges = new ConcurrentHashMap<Long, TreeMap<Long, Update>>();
		finishedMemoryChanges = new ConcurrentHashMap<Long, TreeMap<Long, Update>>();
		status = new ConcurrentHashMap<Long, PipelineStatus>();
		threadTxId = new ConcurrentHashMap<Long, Long>();
		isWritePacketIn = new ConcurrentHashMap<Long, Boolean>();
		log = LoggerFactory.getLogger(TracePacketIn.class);
		
		mapper = new ObjectMapper();
		mapper.configure(Feature.QUOTE_FIELD_NAMES, true);
		// mapper.configure(MapperFeature.AUTO_DETECT_FIELDS, true);
		// mapper.configure(Feature.QUOTE_NON_NUMERIC_NUMBERS, true);
		// mapper.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
	}

	public synchronized static TracePacketIn getInstance() {
		if (instanceTrace == null) {
			instanceTrace = new TracePacketIn();
			System.out.print("Warning, should fall here just once, TracePackeIn.");
			return instanceTrace;
		} else {
			return instanceTrace;
		}
	}

	public PipelineStatus getStatus(long threadId) {
		return status.get(threadId);
	}
	public StringBuffer getAllStatus() {
		StringBuffer sb = new StringBuffer();
		sb.append("\nStatus All Threads:");
		Iterator<Long> tId = status.keySet().iterator();
		while (tId.hasNext()) {
			Long thread = (Long) tId.next();
			sb.append("\n\tThread: "+ thread+", \tStatus: " + status.get(thread));	
		}
		return sb;
		
	}

	public boolean isWrite(long threadId) {
		if (isWritePacketIn.containsKey(threadId))
			return isWritePacketIn.get(threadId);
		else
			return false;
	}
	public Long getTxThreadId(Long threadId) {
		return threadTxId.get(threadId);
	}

	public void setTxInitialCounter(Long initialValue) {
		if(initialValue > txId.incrementAndGet())
			txId.set(initialValue);
	}
	
	public void setStatus(long threadId, PipelineStatus s) {
		if (s.equals(PipelineStatus.INITIATED)) {
			threadTxId.put(threadId, txId.incrementAndGet());
		}else if (s.equals(PipelineStatus.FINISHED)) {
			threadTxId.put(threadId, 0L);
		}
		
		if (s.equals(PipelineStatus.PREPARE) && memoryChanges.containsKey(threadId)) {
			finishedMemoryChanges.put(threadId, memoryChanges.remove(threadId));
		} else if (s.equals(PipelineStatus.SAVED) 
				|| s.equals(PipelineStatus.ROLLBACK) 
				|| s.equals(PipelineStatus.BATCH_ROLLBACK)
				|| s.equals(PipelineStatus.SELF_ROLLBACK)
				|| s.equals(PipelineStatus.TX_INVALID)) {
			isWritePacketIn.put(threadId, false);
			threadTxId.put(threadId, 0L);
		}
		//log.info("Setting PipelineStatus of threadId:{}, to:{}", threadId, s);
		status.put(threadId, s);		
	}

	
	public HashMap<Long, TreeMap<Long, Update>> getLinkDiscoveryUpdates(long threadId) {
		
		HashMap<Long, TreeMap<Long, Update>> finished = new HashMap<>();
		finished.put(threadId, new TreeMap<>());
		log.debug("LK MemoryChanges: \n{}\nLK FinishedMemoryChanges: \n{}\n", 
				memoryChanges.toString(),
				finishedMemoryChanges.toString());
		
		finished.put(threadId, memoryChanges.remove(threadId));
		//log.info("FINISHED LK: {}", finished);
		//setStatus(threadId, PipelineStatus.SAVING);		
		return finished;
	}
	
	public HashMap<Long, TreeMap<Long, Update>> getMyUpdates(long threadId) {
		HashMap<Long, TreeMap<Long, Update>> finished = new HashMap<>();
		try {
			if (getStatus(threadId).equals(PipelineStatus.PREPARE) && !getStatus(threadId).equals(PipelineStatus.SAVING)) {
				finished.put(threadId, finishedMemoryChanges.remove(threadId));
				setStatus(threadId, PipelineStatus.SAVING);
			}	
		} catch (NullPointerException e) {
			return finished;
		}
		
		
		return finished;
	}

	public HashMap<Long, TreeMap<Long, Update>> getAllPacketInUpdates(long threadId) {
		
		HashMap<Long, TreeMap<Long, Update>> finished = new HashMap<>();
		
		if (getStatus(threadId).equals(PipelineStatus.SAVING) || 
				!finishedMemoryChanges.containsKey(threadId)) {
			return finished;
		}
		
		synchronized (finishedMemoryChanges) {
			if (finishedMemoryChanges.containsKey(threadId) 
					&& getStatus(threadId).equals(PipelineStatus.PREPARE)) {

				Iterator<Long> it = finishedMemoryChanges.keySet().iterator();
				while (it.hasNext()) {
					long thread = (long) it.next();
					if (getStatus(thread).equals(PipelineStatus.PREPARE)
							&& !getStatus(thread).equals(PipelineStatus.SAVING)) {
						finished.put(thread, finishedMemoryChanges.remove(thread));
						setStatus(thread, PipelineStatus.SAVING);
					} /*
						 * else { log.info(" ThreadId: {} not ready...", thread); }
						 */
				}
			}
		}
		return finished;
	}
	
	public void addMsgBuffer(long threadId, OFMessage messageToBuffer) {
		log.trace("Adding msg to MessageBuffer, ThreadId:{}, Message:{}",
				threadId, messageToBuffer);
		msgBuffer.putIfAbsent(threadId, new LinkedList<OFMessage>());
		msgBuffer.get(threadId).add(messageToBuffer);
	}

	public void addMemoryChanges(long threadId, String tableName, Update update) {
		log.trace("Adding into MemoryChanges: {}", update);
		memoryChanges.putIfAbsent(threadId, new TreeMap<>());
		memoryChanges.get(threadId).put(update.getTimeStamp(), update);
		isWritePacketIn.put(threadId, true);
	}

	public void clearMessageBuffer(long threadId) {
		try {
			msgBuffer.remove(threadId);
		} catch (Exception e) {
			log.debug("Probably msgBuffer has no data.");
		}
	}

	public void dispatchMsgBuffer(long threadId, IOFSwitch sw) {
		if (msgBuffer.containsKey(threadId)) {
			while (!msgBuffer.get(threadId).isEmpty()) {
				OFMessage message = msgBuffer.get(threadId).poll();
				if(sw == null)
					log.error("IOFSwitch null at DispatchMessageBuffer.");
				else if(sw.getStatus().equals(SwitchStatus.MASTER))
						sw.write(message);
			}
		} else {
			log.trace("EMPTY msgBuffer, threadId {}", threadId);
		}
	}

	public void clearMessageBufferAndMemoryChanges(long threadId) {
		if (msgBuffer.containsKey(threadId))
			msgBuffer.get(threadId).clear();

		if (memoryChanges.containsKey(threadId))
			memoryChanges.get(threadId).clear();
		
		if(finishedMemoryChanges.containsKey(threadId))
			finishedMemoryChanges.get(threadId).clear();

	}

}
