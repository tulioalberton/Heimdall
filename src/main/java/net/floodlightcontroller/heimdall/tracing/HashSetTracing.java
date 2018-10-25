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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import org.apache.zookeeper.data.Stat;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IControllerCompletionListener;
import net.floodlightcontroller.core.IControllerRollbackListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.heimdall.ITarService.Operation;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.PersistentDomain;
import net.floodlightcontroller.heimdall.TracePacketIn;
import net.floodlightcontroller.util.TimedCache;

public class HashSetTracing<K> implements Set<K>, IControllerRollbackListener, IControllerCompletionListener {

	private HashSet<K> hSet;
	private HashMap<Long, HashSet<K>> setCoW;
	private String name;
	private Scope scope;
	private TracePacketIn trace;
	private Logger logger;
	
	private final Object lockUpdateAware = new Integer(1);
	private final Object lockConsolidateUpdates = new Integer(1);
	private final ConcurrentHashMap<K, Update> dataDS;
	private final TimedCache<K> cache;
	
	private Function<? super K, String> serializer;
	private Function<String, ? extends K> deserializer;
	private String secondLevel=null;

	public HashSetTracing() {
		super();
		cache = new TimedCache<>();
		dataDS = new ConcurrentHashMap<K, Update>();
		scope = Scope.GLOBAL;
	}

	public HashSetTracing(
							HashSet<K> hs, 
							String nm, 
							Scope s, 
							int cacheSize, 
							int cacheExpirationTimeMS,
							Function<? super K, String> serializer, 
							Function<String, ? extends K> deserializer) {
		
		hSet = hs;
		setCoW = new HashMap<>();
		name = nm;
		scope = s;
		trace = TracePacketIn.getInstance();
		logger = LoggerFactory.getLogger(HashSetTracing.class);
		dataDS = new ConcurrentHashMap<K, Update>();
		cache = new TimedCache<>();
	}

	public String getName() {
		return this.name;
	}

	@Override
	public int size() {
		return this.hSet.size();
	}

	@Override
	public boolean isEmpty() {
		if (this.scope.equals(Scope.GLOBAL)) {
			long threadId = Thread.currentThread().getId();
			logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", isEmpty(), Scope: " + scope);

			if (this.setCoW.containsKey(threadId)) {
				if (this.setCoW.get(threadId).isEmpty())
					return this.hSet.isEmpty();
			}
			return this.hSet.isEmpty();
		} else
			return this.hSet.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		if (this.scope.equals(Scope.GLOBAL)) {
			long threadId = Thread.currentThread().getId();
			logger.debug("Module: " + this.name + " ThreadId: " + threadId + " contains(Obj): " + o + ", Size: "
					+ o.toString().getBytes().length);

			if (this.setCoW.containsKey(threadId))
				if (this.setCoW.get(threadId).contains(o))
					return true;
			return this.hSet.contains(o);
		} else
			return this.hSet.contains(o);
	}

	@Override
	public Iterator<K> iterator() {
		if (this.scope.equals(Scope.GLOBAL)) {
			long threadId = Thread.currentThread().getId();
			logger.debug("Module: " + this.name + " ThreadId: " + threadId + ", iterator().");

			if (this.setCoW.containsKey(threadId)) {
				HashSet<K> mergedIterator = new HashSet<>();
				if (this.setCoW.get(threadId).size() > 0) {
					mergedIterator.addAll(this.setCoW.get(threadId));
					logger.trace("Returning CopyOnWrite Iterator, merged with normal set. {}", this.name);
				}
				mergedIterator.addAll(this.hSet);
				return mergedIterator.iterator();
			} else {
				logger.trace("Returning Normal Iterator, not merged with normal set. {}", this.name);
				return this.hSet.iterator();
			}
		} else
			return this.hSet.iterator();
	}

	@Override
	public Object[] toArray() {
		if (this.scope.equals(Scope.GLOBAL)) {
			long threadId = Thread.currentThread().getId();
			logger.debug("Module: " + this.name + " ThreadId: " + threadId + " toArray().");

			if (this.setCoW.containsKey(threadId)) {
				Object[] arr = new Object[this.setCoW.get(threadId).size() + this.hSet.size()];
				Iterator<K> it = this.setCoW.get(threadId).iterator();
				int i = 0;
				while (it.hasNext()) {
					K k = (K) it.next();
					arr[i++] = k;
				}
				it = this.hSet.iterator();
				while (it.hasNext()) {
					K k = (K) it.next();
					arr[i++] = k;
				}
				return arr;
			}
			return this.hSet.toArray();
		} else
			return this.hSet.toArray();

	}

	@Override
	public <T> T[] toArray(T[] a) {
		long threadId = Thread.currentThread().getId();
		logger.debug("Module: " + this.name + " ThreadId: " + threadId + " toArray(T):");
		logger.error("Not implemented. toArray(T[]");
		return this.hSet.toArray(a);
	}

	@Override
	public boolean add(K e) {
		long threadId = Thread.currentThread().getId();
		
		
		if (this.scope.equals(Scope.GLOBAL)) {
			
			Update update = new Update(name, Operation.ADD, serializer.apply((K) e), null, scope, threadId, trace.getTxThreadId(threadId));
			
			update.setSecondLevel(secondLevel);
			logger.debug("Module: " + this.name + " ThreadId: " + threadId + ", ADD, " +secondLevel+"/"+update.getKey());
			
			trace.addMemoryChanges(threadId, this.name, update);
			
			if (this.setCoW.containsKey(threadId)) {
				if (setCoW.get(threadId).contains(e)) {
					return false;
				} else {
					logger.debug("Adding data to setCow, threadId:{}, data:{}", threadId, e);
					return this.setCoW.get(threadId).add(deserializer.apply(e.toString()));
				}
			} else {
				setCoW.put(threadId, new HashSet<>());
				return this.setCoW.get(threadId).add(e);
			}
		} else
			return this.hSet.add(e);
	}

	@Override
	public boolean remove(Object o) {
		long threadId = Thread.currentThread().getId();		
		
		if (this.scope.equals(Scope.GLOBAL)) {

			Update update = new Update(name, Operation.REMOVE_K, serializer.apply((K) o), null, scope, threadId, trace.getTxThreadId(threadId));
			
			update.setSecondLevel(secondLevel);
			logger.info("Module: " + this.name + " ThreadId: " + threadId + " REMOVE_K: " +secondLevel+"/"+ update.getKey());
			
			trace.addMemoryChanges(threadId, this.name, update);
		
			if (this.setCoW.containsKey(threadId)) {
				if (this.setCoW.get(threadId).contains(o))
					return this.setCoW.get(threadId).remove(o);
				else
					return this.hSet.contains(o);
			}
			return this.hSet.contains(o);
		} else
			return this.hSet.remove(o);

	}

	@Override
	public boolean containsAll(Collection<?> c) {
		long threadId = Thread.currentThread().getId();
		logger.debug("Module: " + this.name + " ThreadId: " + threadId + " containsAll(Collection): " + c);
		return this.hSet.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends K> c) {
		long threadId = Thread.currentThread().getId();
		logger.debug("Module: " + this.name + " ThreadId: " + threadId + " addAll(Collection): " + c);
		
		if (this.scope.equals(Scope.GLOBAL)) {
			Update update = new Update(name, Operation.ADDALL, null, null, scope, threadId, trace.getTxThreadId(threadId));
			update.setV1(c.toString());
			update.setSecondLevel(secondLevel);
			
			trace.addMemoryChanges(threadId, this.name, update);
		
			if (setCoW.containsKey(threadId)) {
				logger.debug("Adding ALL HashSet Element, ThreadId:{}", threadId);
				return this.setCoW.get(threadId).addAll(c);
			} else {
				this.setCoW.put(threadId, new HashSet<>());
				logger.debug("Adding ALL and creating HashSet Element, ThreadId:{}", threadId);
				return this.setCoW.get(threadId).addAll(c);
			}
		} else
			return this.hSet.addAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		// TODO Auto-generated method stub
		long threadId = Thread.currentThread().getId();
		logger.debug("Module: " + this.name + " ThreadId: " + threadId + " retainAll(Collection): " + c);

		System.err.print("\n\nModule: " + this.name + " ThreadId: " + threadId + " retainAll(Collection): " + c);
		System.exit(1);
		return this.hSet.retainAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		long threadId = Thread.currentThread().getId();
		logger.debug("Module: " + this.name + " ThreadId: " + threadId + " removeAll(Collection)." + c);
		
		if (this.scope.equals(Scope.GLOBAL)) {
			Update update = new Update(name, Operation.REMOVEALL, null, null, scope, threadId, trace.getTxThreadId(threadId));
			update.setSecondLevel(secondLevel);
			
			trace.addMemoryChanges(threadId, this.name, update);
			
			return true;
		} else
			return this.hSet.removeAll(c);
	}

	@Override
	public void clear() {
		long threadId = Thread.currentThread().getId();
		logger.trace("Module: " + this.name + " ThreadId: " + threadId + " clear().");
		
		if (this.scope.equals(Scope.GLOBAL)) {
			Update update = new Update(name, Operation.CLEAR, null, null, scope, threadId, trace.getTxThreadId(threadId));
			update.setSecondLevel(secondLevel);
			
			trace.addMemoryChanges(threadId, this.name, update);
		} else
			this.hSet.clear();

	}

	public IControllerRollbackListener getIControllerRollbackListener() {
		return this;
	}

	public IControllerCompletionListener getIControllerCompletionListener() {
		return this;
	}

	@Override
	public void onBatchConsumed(TreeMap<Long, Update> orderedFinalPacketIn) {
		consolidateUpdates(orderedFinalPacketIn);
	}
	
	private boolean consolidateUpdates(TreeMap<Long, Update> orderedFinalPacketIn) {

		boolean consolidate = true;
		synchronized (lockConsolidateUpdates) {
			Iterator<Update> itUP = orderedFinalPacketIn.values().iterator();
			while (itUP.hasNext()) {
				Update update = itUP.next();
//				dataDS.put((K) update.getKey(), update);
//				cache.update((K) update.getKey());
			}
		}
		return consolidate;
	}


	public void setSecondLevel(String sIn){
		this.secondLevel = sIn;
	}


	@Override
	public void onSelfRollback(long threadId) {
	}

	@Override
	public void onBatchRollback(TreeMap<Long, Update> orderedFinalPacketIn) {
	}

	@Override
	public void onDataStoreRollback(TreeMap<Long, Update> orderedFinalPacketIn) {
	}

	@Override
	public void onMessageConsumed(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onInitialRecover(TreeMap<Long, Update> orderedFinalPacketIn) {
		// TODO Auto-generated method stub
		
	}


}
