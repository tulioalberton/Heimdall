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
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.zookeeper.data.Stat;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IControllerCompletionListener;
import net.floodlightcontroller.core.IControllerRollbackListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.heimdall.ITarService;
import net.floodlightcontroller.heimdall.ITarService.Operation;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.PersistentDomain;
import net.floodlightcontroller.heimdall.TracePacketIn;
import net.floodlightcontroller.util.TimedCache;

public class ConcurrentHashMapTracing<K, V>
		implements ConcurrentMap<K, V>, IControllerRollbackListener, IControllerCompletionListener {

	private final PersistentDomain persistence;
	private final TracePacketIn trace;
	private final ConcurrentHashMap<K, V> map;
	private final ConcurrentHashMap<Long, ConcurrentHashMap<K, V>> mapCoW;
	private final HashMap<Long, Queue<Update>> updates;
	private final HashMap<Long, HashMap<K, Stat>> statsCoW;
	private final TimedCache<K> cache;
	private final ReentrantReadWriteLock lock;
	private final String name;
	private final Scope scope;
	private final Logger logger;
	
	private Function<? super V, String> ser;
	private Function<String, ? extends V> deserF;
	private BiFunction<? super K, String, ? extends V> deserBiF;
	private final boolean typeDeser;

	public ConcurrentHashMapTracing(ConcurrentHashMap<K, V> delegatee, String nm, Scope s, int cacheSize,
			int cacheExpirationTimeMS, Function<? super V, String> serializer,
			Function<String, ? extends V> deserializer) {
		this.map = delegatee;
		// concurrence factor should be adjusted resembling workThreads.
		this.mapCoW = new ConcurrentHashMap<Long, ConcurrentHashMap<K, V>>();
		this.name = nm;
		this.scope = s;
		this.trace = TracePacketIn.getInstance();
		this.persistence = PersistentDomain.getInstance();
		this.statsCoW = new HashMap<Long, HashMap<K, Stat>>();
		this.logger = LoggerFactory.getLogger(ConcurrentHashMapTracing.class);
		this.cache = new TimedCache<K>(cacheSize, cacheExpirationTimeMS);
		this.updates = new HashMap<>();
		this.lock = new ReentrantReadWriteLock();
		this.ser = Objects.requireNonNull(serializer);
		this.deserF = Objects.requireNonNull(deserializer);
		this.typeDeser = false;
	}

	public ConcurrentHashMapTracing(ConcurrentHashMap<K, V> delegatee, String nm, Scope s, int cacheSize,
			int cacheExpirationTimeMS, Function<? super V, String> serializer,
			BiFunction<? super K, String, ? extends V> deserializer) {
		this.map = delegatee;
		this.mapCoW = new ConcurrentHashMap<Long, ConcurrentHashMap<K, V>>(); // concurrence
																				// factor
																				// should
																				// be
																				// adjusted
																				// resembling
																				// workThreads.
		this.name = nm;
		this.scope = s;
		this.trace = TracePacketIn.getInstance();
		this.persistence = PersistentDomain.getInstance();
		this.statsCoW = new HashMap<Long, HashMap<K, Stat>>();
		this.logger = LoggerFactory.getLogger(ConcurrentHashMapTracing.class);
		this.cache = new TimedCache<K>(cacheSize, cacheExpirationTimeMS);
		this.updates = new HashMap<>();
		this.lock = new ReentrantReadWriteLock();
		this.ser = Objects.requireNonNull(serializer);
		this.deserBiF = Objects.requireNonNull(deserializer);
		this.typeDeser = true;
	}

	public String getName() {
		return this.name;
	}

	@Override
	public V get(Object key) {
		long threadId = Thread.currentThread().getId();
		K keyK = (K) key;
		
		Update update = new Update(name, Operation.GET, keyK.toString(), null, scope, threadId, trace.getTxThreadId(threadId));

		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", get(K), Key:" + key);

		if (this.scope.equals(Scope.GLOBAL)) {

			if (!this.statsCoW.containsKey(threadId))
				this.statsCoW.put(threadId, new HashMap<>());			
			this.statsCoW.get(threadId).putIfAbsent(keyK, new Stat());

			if (this.mapCoW.containsKey(threadId)) {
				if (mapCoW.get(threadId).containsKey(keyK)) {
					this.logger.info("Data in Map CopyOnWrite, threadId:{}, Key:{}", threadId, keyK.toString());
					this.cache.update(keyK);
					return this.mapCoW.get(threadId).get(keyK);
				}
			} else
				this.mapCoW.put(threadId, new ConcurrentHashMap<K, V>());

			if (this.cache.update(keyK) && this.map.containsKey(keyK)) {
				this.logger.info("Data in cache and normal map, not in CoW, returning, " + "threadId:{}, Version:{}",
						threadId, this.statsCoW.get(threadId).get(keyK).getVersion());
				return this.map.get(keyK);
			} else {
				Update dataDS = persistence.getDataDS(update);

				if (dataDS.getKey() != null) {
					V vR = null;
					try {
						if (dataDS.getOp().equals(Operation.REMOVE_K) || dataDS.getOp().equals(Operation.REMOVE_KV))
							return this.map.get(keyK);
						if (dataDS.getV2() != null) {
							if (typeDeser)
								vR = deserBiF.apply(keyK, dataDS.getV2());
							else
								vR = deserF.apply(dataDS.getV2());
						} else {
							if (typeDeser)
								vR = deserBiF.apply(keyK, dataDS.getV1());
							else
								vR = deserF.apply(dataDS.getV1());
						}
						if (vR != null)
							return this.mapCoW.get(threadId).put(keyK, vR);
					} catch (java.lang.NullPointerException npe) {
						this.logger.debug("Could not deserialize data from DS: {}", dataDS);
						return this.map.get(keyK);
					} catch (java.lang.RuntimeException rte) {
						this.logger.debug("RunTimeException: {}", dataDS);
						return this.map.get(keyK);
					} finally {
						this.statsCoW.get(threadId).put(keyK, dataDS.getStat());
						this.cache.update(keyK);
					}
					return this.map.get(keyK);
				} else {
					this.logger.debug("Data DOES NOT exist at Data Store: {}/{}", this.name, dataDS.getKey());
					return this.map.get(keyK);
				}
			}
		} 
		this.logger.trace("Returning data from normal map, threadId:{}", threadId);
		return this.map.get(keyK);
		
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public V put(K key, V value) {
		long threadId = Thread.currentThread().getId();

		if (this.scope.equals(Scope.GLOBAL)) {

			//Update update = utilC.getUpdateFormated(this.name, key, Operation.PUT, this.scope, threadId);
			Update update = new Update(name, Operation.PUT, key.toString(), ser.apply(value), scope, threadId,
					trace.getTxThreadId(threadId));

			if (!this.statsCoW.containsKey(threadId)) 
				this.statsCoW.put(threadId, new HashMap<>());			
			update.setStat(this.statsCoW.get(threadId).get(key));
			
			try {
				update.setV1(ser.apply(value));
			} catch (java.lang.NullPointerException npe) {
				this.logger.debug("NullPointerException at serialization: : K:{}, V:{}", key, value);
			} catch (java.lang.RuntimeException rte) {
				this.logger.debug("RuntimeException at serialization: K:{}, V:{} ", key, value);
			}

			this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", " + "PUT, Key:" + update.getKey());

			trace.addMemoryChanges(threadId, this.name, update);
			
			if (mapCoW.containsKey(threadId))
				return this.mapCoW.get(threadId).put(key, value);
			else {
				this.mapCoW.put(threadId, new ConcurrentHashMap<K, V>());
				return this.mapCoW.get(threadId).put(key, value);
			}
		} else
			return this.map.put(key, value);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void clear() {
		long threadId = Thread.currentThread().getId();
		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", clear()");

		if (this.scope.equals(Scope.GLOBAL)) {

			Update update = new Update(name, Operation.CLEAR, null, null, scope, threadId, trace.getTxThreadId(threadId));
			trace.addMemoryChanges(threadId, this.name, update);
		} else
			this.map.clear();
	}

	@Override
	public boolean containsKey(Object key) {
		long threadId = Thread.currentThread().getId();
		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", containsKey(K), Key:" + key);
		if (this.scope.equals(Scope.GLOBAL)) {
			if (this.mapCoW.containsKey(threadId))
				if (this.mapCoW.get(threadId).containsKey((K) key))
					return true;
		}
		return this.map.containsKey((K) key);

	}

	@Override
	public boolean containsValue(Object arg0) {
		long threadId = Thread.currentThread().getId();
		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", containsValue(V), Value:" + arg0);
		if (this.scope.equals(Scope.GLOBAL)) {
			if (this.mapCoW.containsKey(threadId))
				if (this.mapCoW.get(threadId).containsValue(arg0))
					return true;

		}
		return this.map.containsValue(arg0);

	}

	@Override
	public boolean isEmpty() {
		long threadId = Thread.currentThread().getId();
		this.logger.trace("Module: " + this.name + ", ThreadId: " + threadId + ", isEmpty(), Scope: " + scope);
		if (this.scope.equals(Scope.GLOBAL)) {
			if (this.mapCoW.containsKey(threadId))
				if (this.mapCoW.get(threadId).isEmpty())
					return this.map.isEmpty();
		}
		return this.map.isEmpty();
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		long threadId = Thread.currentThread().getId();
		this.logger.trace("Module: " + this.name + ", ThreadId: " + threadId + ", entrySet()");
		if (this.scope.equals(Scope.GLOBAL)) {
			Set<java.util.Map.Entry<K, V>> mergedEntrySet = new HashSet<Map.Entry<K, V>>();
			if (this.mapCoW.containsKey(threadId)) {
				if (this.mapCoW.get(threadId).entrySet().size() > 0) {
					this.logger.debug("Map CopyOnWrite EntrySet is Not Empty, merging. {}", this.name);
					mergedEntrySet.addAll(this.mapCoW.get(threadId).entrySet());
					mergedEntrySet.addAll(this.map.entrySet());
					return mergedEntrySet;
				}
			}
		}
		this.logger.debug("Map CopyOnWrite EntrySet is Empty, not merging. {}", this.name);
		return this.map.entrySet();
	}

	@Override
	public Set<K> keySet() {
		long threadId = Thread.currentThread().getId();
		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", keySet()");
		if (this.scope.equals(Scope.GLOBAL)) {
			Set<K> setR = new HashSet<>();
			if (this.mapCoW.containsKey(threadId)) {
				if (this.mapCoW.get(threadId).keySet().size() > 0) {
					this.logger.debug("Map CopyOnWrite KeySet is Not Empty, merging: {}", this.name);
					setR.addAll(this.mapCoW.get(threadId).keySet());
					setR.addAll(this.map.keySet());
					return setR;
				}
			}
		}
		this.logger.debug("Map CopyOnWrite KeySet is Empty, not merging: {}", this.name);
		return this.map.keySet();
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> arg0) {
		long threadId = Thread.currentThread().getId();

		
		 Update update = new Update(name, Operation.PUTALL, null, null, scope, threadId, trace.getTxThreadId(threadId));

		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ",  PUTALL, Key:" + arg0);

		trace.addMemoryChanges(threadId, this.name, update);

		if (mapCoW.containsKey(threadId))
			this.mapCoW.get(threadId).putAll(arg0);
		else {
			this.mapCoW.put(threadId, new ConcurrentHashMap<K, V>());
			this.mapCoW.get(threadId).putAll(arg0);
		}
		this.logger.info("PUTALL Not Implemented, existing...");
		System.err.println("PUTALL Not Implemented, existing...");
		System.exit(1);

	}

	@Override
	public V remove(Object key) {
		long threadId = Thread.currentThread().getId();

		if (this.scope.equals(Scope.GLOBAL)) {
			K keyK = (K) key;
			//Update update = utilC.getUpdateFormated(this.name, keyK, Operation.REMOVE_K, this.scope, threadId);
			Update update = new Update(name, Operation.REMOVE_K, keyK.toString(), null, scope, threadId, trace.getTxThreadId(threadId));

			if (!this.statsCoW.containsKey(threadId)) 
				this.statsCoW.put(threadId, new HashMap<>());			
			update.setStat(this.statsCoW.get(threadId).get(key));

			this.logger.info("Module: " + this.name + ", ThreadId: " + threadId + ", " + "REMOVE_K, Key:" + key);

			trace.addMemoryChanges(threadId, this.name, update);

			if (this.mapCoW.containsKey(threadId))
				if (this.mapCoW.get(threadId).containsKey(key))
					return this.mapCoW.get(threadId).remove(key);
			return this.map.get(key);
		}
		return this.map.remove(key);

	}

	@Override
	public int size() {
		long threadId = Thread.currentThread().getId();
		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", size().");
		return (this.map.size());
	}

	@Override
	public Collection<V> values() {
		long threadId = Thread.currentThread().getId();
		this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", " + "values(), Scope: " + scope);
		if (this.scope.equals(Scope.GLOBAL)) {
			Collection<V> vOut = new HashSet<>();
			if (this.mapCoW.containsKey(threadId)) {
				vOut.addAll(this.mapCoW.get(threadId).values());
				vOut.addAll(this.map.values());
				return vOut;
			}
		}
		return this.map.values();
	}

	@Override
	public V putIfAbsent(K arg0, V arg1) {
		long threadId = Thread.currentThread().getId();

		this.logger.debug(
				"Module: " + this.name + ", ThreadId: " + threadId + ", PUTABSENT, Key:" + arg0 + ", Value:" + arg1);

		if (this.scope.equals(Scope.GLOBAL)) {
			V vR = null;
			//Update update = utilC.getUpdateFormated(this.name, arg0, Operation.PUTABSENT, this.scope, threadId);
			Update update = new Update(name, Operation.PUTABSENT, arg0.toString(), ser.apply(arg1), scope, threadId, trace.getTxThreadId(threadId));

			if (!this.statsCoW.containsKey(threadId)) 
				this.statsCoW.put(threadId, new HashMap<>());			
			update.setStat(this.statsCoW.get(threadId).get(arg0));
			
			
			try {
				update.setV1(ser.apply(arg1));
			} catch (java.lang.NullPointerException npe) {
				this.logger.debug("NullPointerException at serialization: : K:{}, V:{}", arg0, arg1);
			} catch (java.lang.RuntimeException rte) {
				this.logger.debug("RuntimeException at serialization: K:{}, V:{} ", arg0, arg1);
			}

			trace.addMemoryChanges(threadId, this.name, update);

			if (!this.map.containsKey(arg0) && !this.map.containsValue(arg1)) {

				trace.addMemoryChanges(threadId, this.name, update);
		
				if (mapCoW.containsKey(threadId))
					return this.mapCoW.get(threadId).putIfAbsent(arg0, arg1);
				else {
					this.mapCoW.put(threadId, new ConcurrentHashMap<K, V>());
					return this.mapCoW.get(threadId).putIfAbsent(arg0, arg1);
				}
			}
			this.logger.debug("Module:" + this.name + ", ThreadId:" + threadId + ",PUTABSENT, Key/Value already present.");
			return this.map.get(arg0);
		}
		return this.map.putIfAbsent(arg0, arg1);

	}

	@Override
	public boolean remove(Object arg0, Object arg1) {
		long threadId = Thread.currentThread().getId();
		if (this.scope.equals(Scope.GLOBAL)) {
			K keyK = (K) arg0;
			//Update update = utilC.getUpdateFormated(this.name, (K) arg0, Operation.REMOVE_KV, this.scope, threadId);
			Update update = new Update(name, Operation.REMOVE_KV, keyK.toString(), null, scope, threadId, trace.getTxThreadId(threadId));
			update.setV1(ser.apply((V) arg1));

			if (!this.statsCoW.containsKey(threadId)) 
				this.statsCoW.put(threadId, new HashMap<>());			
			update.setStat(this.statsCoW.get(threadId).get(arg0));
			
			trace.addMemoryChanges(threadId, this.name, update);

			this.logger.debug(
					"Module: " + this.name + ", ThreadId: " + threadId + ", " + "REMOVE_KV, K:" + arg0 + ", V:" + arg1);

			if (this.mapCoW.containsKey(threadId)) {
				if (this.mapCoW.get(threadId).containsKey(arg0) && this.mapCoW.get(threadId).containsValue(arg1))
					return this.mapCoW.get(threadId).remove(arg0, arg1);
			}
			return this.map.containsKey(arg0) && this.map.containsValue(arg1);
		}
		return this.map.remove(arg0, arg1);
	}

	@Override
	public V replace(K key, V value) {
		long threadId = Thread.currentThread().getId();

		if (this.scope.equals(Scope.GLOBAL)) {
			//Update update = utilC.getUpdateFormated(this.name, key, Operation.REPLACE_KV, this.scope, threadId);
			Update update = new Update(name, Operation.REPLACE_KV, key.toString(), null, scope, threadId, trace.getTxThreadId(threadId));

			if (!this.statsCoW.containsKey(threadId)) 
				this.statsCoW.put(threadId, new HashMap<>());			
			update.setStat(this.statsCoW.get(threadId).get(key));

			try {
				update.setV1(ser.apply(value));
			} catch (java.lang.NullPointerException npe) {
				this.logger.debug("NullPointerException at serialization: : K:{}, V:{}", key, value);
			} catch (java.lang.RuntimeException rte) {
				this.logger.debug("RuntimeException at serialization: K:{}, V:{} ", key, value);
			}

			trace.addMemoryChanges(threadId, this.name, update);

			this.logger.debug("Module:" + this.name + ", ThreadId: " + threadId + ", REPLACE_KV, Key:" + key + ", Value:"
					+ value);

			
			if (this.mapCoW.containsKey(threadId))
				return this.mapCoW.get(threadId).replace(key, value);
			else {
				return this.map.get(key);
			}
		}

		return this.map.replace(key, value);

	}

	@Override
	public boolean replace(K key, V v1, V v2) {
		long threadId = Thread.currentThread().getId();

		if (this.scope.equals(Scope.GLOBAL)) {
			//Update update = utilC.getUpdateFormated(this.name, key, Operation.REPLACE_KVV, this.scope, threadId);
			Update update = new Update(name, Operation.REPLACE_KVV, key.toString(), null, scope, threadId, trace.getTxThreadId(threadId));

			if (!this.statsCoW.containsKey(threadId)) 
				this.statsCoW.put(threadId, new HashMap<>());			
			update.setStat(this.statsCoW.get(threadId).get(key));
			
			try {
				update.setV1(ser.apply(v1));
				update.setV2(ser.apply(v2));
			} catch (java.lang.NullPointerException npe) {
				this.logger.debug("NullPointerException at serialization: V1:{}, V2:{} ", v1, v2);
			} catch (java.lang.RuntimeException rte) {
				this.logger.debug("RuntimeException at serialization: V1:{}, V2:{} ", v1, v2);
			}

			trace.addMemoryChanges(threadId, this.name, update);

			this.logger.debug("Module: " + this.name + ", ThreadId: " + threadId + ", " + "REPLACE_KVV, Update:" + update);

			if (this.mapCoW.containsKey(threadId)) {
				if (mapCoW.get(threadId).containsKey(key) && mapCoW.get(threadId).containsValue(v1))
					return this.mapCoW.get(threadId).replace(key, v1, v2);
			}
			// we save all updates and just after transfer to global map
			return this.map.containsKey(key) && this.map.containsValue(v1);
		}

		return this.map.replace(key, v1, v2);

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public boolean consolidateCoW(long threadId) {

		boolean consolidate = true;

		if (updates.containsKey(threadId)) {
			if (!updates.get(threadId).isEmpty()) {
				lock.writeLock().lock();
				updates.get(threadId).iterator();
				try {
					while (!updates.get(threadId).isEmpty()) {
						Update update = updates.get(threadId).poll();

						switch (update.getOp()) {
						case REPLACE_KV:
							this.map.replace((K) update.getKey(), (V) update.getV1());
							//this.logger.debug("Consolidating {} Update, Key:{}, Data:{}",
								//	new Object[] { update.getOp(), update.getKey(), update.getValue1() });
							break;
						case REPLACE_KVV:
							this.map.replace((K) update.getKey(), (V) update.getV1(), (V) update.getV2());
							//this.logger.debug("Consolidating {} Update, Key:{}, Data:{}",
								//	new Object[] { update.getOp(), update.getKey(), update.getValue1() });
							break;
						case PUT:
							this.map.put((K) update.getKey(), (V) update.getV1());
							//this.logger.debug("Consolidating {} Update, Key:{}, Data:{}",
								//	new Object[] { update.getOp(), update.getKey(), update.getValue1() });
							break;
						case PUTABSENT:
							this.map.putIfAbsent((K) update.getKey(), (V) update.getV1());
							//this.logger.debug("Consolidating {} Update, Key:{}, Data:{}",
								//	new Object[] { update.getOp(), update.getKey(), update.getValue1() });
							break;
						case PUTALL:
							//this.map.putAll(update.getM());
							//this.logger.debug("Consolidating {} Update, Collection: {}", update.getM());
							break;
						case REMOVE_K:
							this.map.remove((K) update.getKey());
							//this.logger.debug("Consolidating {} Update, Key:{}", update.getOp(), update.getKey());
							break;
						case REMOVE_KV:
							this.map.remove((K) update.getKey(), (V) update.getV1());
							//this.logger.debug("Consolidating {} Update, Key:{}, Data:{}",
								//	new Object[] { update.getOp(), update.getKey(), update.getValue1() });
							break;
						case CLEAR:
							this.map.clear();
							//this.logger.debug("Consolidating {} Update", update.getOp());
							break;
						default:
							this.logger.debug("NOT CATCHED: {} Update???", update.getOp());
							consolidate = false;
							break;
						}
					}
				} finally {
					this.mapCoW.remove(threadId);
					lock.writeLock().unlock();
					
				}
				
			}
		}
		return consolidate;
	}

	public IControllerRollbackListener getIControllerRollbackListener() {
		return this;
	}

	public IControllerCompletionListener getIControllerCompletionListener() {
		return this;
	}

	@Override
	public void onMessageConsumed(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		long threadId = Thread.currentThread().getId();
		consolidateCoW(threadId);
	}

	@Override
	public void onBatchConsumed(TreeMap<Long, Update> orderedFinalPacketIn) {
		// TODO Auto-generated method stub
		
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
	public void onInitialRecover(TreeMap<Long, Update> orderedFinalPacketIn) {
		// TODO Auto-generated method stub
		
	}

	

}
