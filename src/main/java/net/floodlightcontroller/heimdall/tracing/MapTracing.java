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
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
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
import net.floodlightcontroller.heimdall.ITarService.Operation;
import net.floodlightcontroller.heimdall.ITarService.PipelineStatus;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.PersistentDomain;
import net.floodlightcontroller.heimdall.TracePacketIn;
import net.floodlightcontroller.util.TimedCache;

public class MapTracing<K, V> implements Map<K, V>, IControllerRollbackListener, IControllerCompletionListener {

	/**
	 * Necessary register on IControllerRollbackListener.
	 */

	//private Object lockUpdateAware = new Integer(1);
	private Object lockConsolidateUpdates = new Integer(1);
	// private final Object lockDataStore = new Integer(1);

	private final PersistentDomain persistence;
	private final TracePacketIn trace;
	private Map<K, V> map;
	private ConcurrentHashMap<K, Update> dataDS;
	private ConcurrentHashMap<K, Update> updateAware;
	private ConcurrentHashMap<Long, HashMap<K, V>> mapCoW;
	private ConcurrentHashMap<Long, HashMap<K, Stat>> statCoW;
	/**
	 * Long = txId K = key V = Value
	 */
	//private ConcurrentHashMap<K, TreeMap<Long, V>> dataStoreSnapshot;
	/**
	 * Long = ThreadId Long = Snapshot to use during pipeline execution.
	 */
	private ConcurrentHashMap<Long, Long> snapshotToUse;

	private TimedCache<K> cache;
	private String name;
	private Scope scope;
	private Logger log;

	private Function<? super K, String> ser_F_KEY;
	private Function<String, ? extends K> deser_F_KEY;
	private Function<? super V, String> ser_F_VALUE;
	private Function<String, ? extends V> deser_F_VALUE;

	private BiFunction<? super K, String, ? extends V> deserBiF;
	private boolean typeDeser;// used to choose between second level trace or not.

	public MapTracing(Map<K, V> delegatee, String nm, Scope s, int cacheSize, int cacheExpirationTimeMS,
			Function<? super K, String> serializer_KEY, Function<String, ? extends K> deserializer_KEY,
			Function<? super V, String> serializer_VALUE, Function<String, ? extends V> deserializer_VALUE) {
		this.map = delegatee;
		this.cache = new TimedCache<>();
		this.name = nm;
		this.scope = s;
		this.trace = TracePacketIn.getInstance();
		this.persistence = PersistentDomain.getInstance();
		this.log = LoggerFactory.getLogger(MapTracing.class);
		//this.dataStoreSnapshot = new ConcurrentHashMap<K, TreeMap<Long, V>>();
		this.snapshotToUse = new ConcurrentHashMap<>();

		this.typeDeser = false;
		this.ser_F_KEY = Objects.requireNonNull(serializer_KEY);
		this.deser_F_KEY = Objects.requireNonNull(deserializer_KEY);
		this.ser_F_VALUE = Objects.requireNonNull(serializer_VALUE);
		this.deser_F_VALUE = Objects.requireNonNull(deserializer_VALUE);
		this.deserBiF = null;

		this.dataDS = new ConcurrentHashMap<K, Update>();
		this.mapCoW = new ConcurrentHashMap<Long, HashMap<K, V>>();
		this.statCoW = new ConcurrentHashMap<Long, HashMap<K, Stat>>();
		this.updateAware = new ConcurrentHashMap<>();

	}

	public MapTracing(Map<K, V> delegatee, String nm, Scope s, int cacheSize, int cacheExpirationTimeMS,
			Function<? super K, String> serializer_KEY, Function<String, ? extends K> deserializer_KEY,
			Function<? super V, String> serializer_VALUE,
			BiFunction<? super K, String, ? extends V> deserializer_BF_VALUE) {
		this.map = delegatee;
		this.name = nm;
		this.scope = s;
		this.trace = TracePacketIn.getInstance();
		this.persistence = PersistentDomain.getInstance();
		this.log = LoggerFactory.getLogger(MapTracing.class);
		//this.dataStoreSnapshot = new ConcurrentHashMap<K, TreeMap<Long, V>>();
		this.snapshotToUse = new ConcurrentHashMap<>();

		this.typeDeser = true;
		this.ser_F_KEY = Objects.requireNonNull(serializer_KEY);
		this.deser_F_KEY = Objects.requireNonNull(deserializer_KEY);
		this.ser_F_VALUE = Objects.requireNonNull(serializer_VALUE);
		this.deser_F_VALUE = null;
		this.deserBiF = Objects.requireNonNull(deserializer_BF_VALUE);

		this.dataDS = new ConcurrentHashMap<K, Update>();
		this.mapCoW = new ConcurrentHashMap<Long, HashMap<K, V>>();
		this.statCoW = new ConcurrentHashMap<Long, HashMap<K, Stat>>();
		this.updateAware = new ConcurrentHashMap<>();
		this.cache = new TimedCache<>();

	}

	public String getName() {
		return name;
	}

	/*private Long getVersionToUse(Long threadId, K key, Long txId) {
		log.trace("Data Store Snapshots: {}", dataStoreSnapshot);

		if (!dataStoreSnapshot.containsKey(key))
			return 0L;

		if (snapshotToUse.containsKey(threadId))
			if (snapshotToUse.get(threadId) > 0L)
				return snapshotToUse.get(threadId);

		Entry<Long, V> entry = dataStoreSnapshot.get(key).lastEntry();
		if (entry != null && txId >= entry.getKey()) {
			log.trace(
					"My TxId is greater or equal than last txId from dataStoreSnapshots. Returning last Snapshot To Use. ");
			snapshotToUse.put(threadId, entry.getKey());
			return entry.getKey();
		} else {
			NavigableMap<Long, V> entryTail = dataStoreSnapshot.get(key).tailMap(txId - 3, true);
			log.debug("Entry Tail Snapshots: {}", entryTail);
			if (entryTail.lowerEntry(txId) != null) {
				snapshotToUse.put(threadId, entryTail.higherEntry(txId).getKey());
				log.trace("My TxId is NOT greater or equal than lastKey from dataStoreSnapshots. SnapshotToUse: {}",
						entryTail.lowerEntry(txId).getKey());
			} else {
				log.trace(
						"My TxId is NOT greater or equal than lastKey from dataStoreSnapshots. SnapshotToUse, entryTail: {}",
						entryTail);
			}
			return snapshotToUse.get(threadId);
		}

	}*/

	@Override
	public V get(Object key) {

		long threadId = Thread.currentThread().getId();
		Update updatePattern = new Update(name, Operation.GET, ser_F_KEY.apply((K) key), null, scope, threadId,
				trace.getTxThreadId(threadId));
		K keyK = (K) deser_F_KEY.apply(updatePattern.getKey());

		this.statCoW.putIfAbsent(threadId, new HashMap<K, Stat>());
		this.mapCoW.putIfAbsent(threadId, new HashMap<K, V>());

		if (mapCoW.get(threadId).containsKey(keyK) && statCoW.get(threadId).containsKey(keyK)) {
			/*if(keyK.toString().startsWith("Conflict"))
				log.info("Returning from MapCow map, KeyK:{}", keyK);*/
			return mapCoW.get(threadId).get(keyK);
		}

		if (dataDS.containsKey(keyK) && cache.update(keyK)) {
			V vR = null;
			synchronized (lockConsolidateUpdates) {
				this.mapCoW.get(threadId).put(keyK, deser_F_VALUE.apply(dataDS.get(keyK).getV1()));
				this.statCoW.get(threadId).put(keyK, dataDS.get(keyK).getStat());
				vR = (V) deser_F_VALUE.apply(dataDS.get(keyK).getV1());
			}
			/*if(keyK.toString().startsWith("Conflict"))
				log.info("Returning from Data DS map, KeyK:{}", keyK);*/
			return vR;

		}
		/*
		 * if(updateAware.contains(keyK)) { this.mapCoW.get(threadId).put(keyK, (V)
		 * updateAware.get(keyK).getV1()); this.statCoW.get(threadId).put(keyK,
		 * updateAware.get(keyK).getStat()); return (V) updateAware.get(keyK).getV1(); }
		 */

		/*
		 * Long versionToUse = getVersionToUse(threadId, keyK, updatePattern.getTxId());
		 * log.trace("Version to USE: {}", versionToUse);
		 * 
		 * if(snapshotToUse.containsKey(threadId)) { //
		 * log.info("Returning Snapshot Version: {}, Update:{}", versionToUse,
		 * dataStoreSnapshot.get(keyK).get(versionToUse)); return
		 * dataStoreSnapshot.get(keyK).get(versionToUse); }
		 */

		// System.exit(1);

		// ## IF not into local and Copy-On-Write, go to DataStore.

		Update dataFromDataStore = persistence.getDataDS(updatePattern);
		V vR = null;
		try {
			if (dataFromDataStore.getOp().equals(Operation.REMOVE_K)
					|| dataFromDataStore.getOp().equals(Operation.REMOVE_KV)
					|| dataFromDataStore.getOp().equals(Operation.GET)
					) {
				return null;
			}

			if (dataFromDataStore.getV2() != null) {
				if (typeDeser)
					vR = deserBiF.apply(keyK, dataFromDataStore.getV2().toString());
				else
					vR = deser_F_VALUE.apply(dataFromDataStore.getV2().toString());
			} else if (dataFromDataStore.getV1() != null) {
				if (typeDeser)
					vR = deserBiF.apply(keyK, dataFromDataStore.getV1().toString());
				else
					vR = deser_F_VALUE.apply(dataFromDataStore.getV1().toString());
			}

			log.debug("## Went to Data Store, Data Structure:{}, Key:{}, Value:{}, Version:{}", new Object[] { name,
					updatePattern.getKey(), dataFromDataStore.getV1(), dataFromDataStore.getStat().getVersion() });
			
			this.mapCoW.get(threadId).put(keyK, (V) vR);
			return (V) vR;

		} catch (NullPointerException npe) {
			log.info("Could not deserialize data from DS: {}", dataFromDataStore);
			// return map.get(key);
		} catch (java.lang.RuntimeException rte) {
			log.info("RunTimeException: {}, \nMessage: {}", dataFromDataStore, rte.getMessage());
			// return map.get(key);
		} finally {
			this.cache.update(keyK);
			this.mapCoW.get(threadId).put(keyK, (V) vR);
			dataFromDataStore.getStat().setVersion(new Integer(dataFromDataStore.getStat().getVersion() - 1 ));
			this.statCoW.get(threadId).put(keyK, dataFromDataStore.getStat());
			synchronized (lockConsolidateUpdates) {
				this.dataDS.put(keyK, dataFromDataStore);
			}
		}

		log.trace("Data DOES NOT exist at Data Store: {}/{}", name, dataFromDataStore.getKey());

		log.trace("Returning data from normal map, threadId:{}", threadId);
		return map.get(key);

		/*
		 * Update upReturn = new Update(name, Operation.GET, ser_F_KEY.apply((K) key),
		 * null, null, scope, new Stat(), threadId, trace.getTxThreadId(threadId));
		 * 
		 * K keyK = (K) deser_F_KEY.apply(upReturn.getKey());
		 * 
		 * log.debug("Module: " + name + ", ThreadId: " + threadId + ", GET, Key:" +
		 * key);
		 * 
		 * if (this.scope.equals(Scope.GLOBAL)) {
		 * 
		 * this.statCoW.putIfAbsent(threadId, new HashMap<K, Stat>());
		 * this.statCoW.get(threadId).putIfAbsent(keyK, new Stat());
		 * this.mapCoW.putIfAbsent(threadId, new HashMap<K, V>());
		 * 
		 * V vR = null;
		 * 
		 * if (this.updateAware.containsKey(keyK) && this.cache.update(keyK) &&
		 * this.mapCoW.get(threadId).containsKey(keyK) &&
		 * this.statCoW.get(threadId).containsKey(keyK)) {
		 * 
		 * upReturn = new Update(this.updateAware.get(keyK));
		 * this.statCoW.get(threadId).put(keyK, upReturn.getStat());
		 * this.mapCoW.get(threadId).put(keyK, deser_F_VALUE.apply(upReturn.getV1()));
		 * 
		 * log.info("Trying to return from Up Aware MAP. \nKey:{}, Version:{}, Value:{}"
		 * , new Object[] { keyK, upReturn.getStat().getVersion(), upReturn.getV1() });
		 * 
		 * vR = deser_F_VALUE.apply(upReturn.getV1()); return vR;
		 * 
		 * } else if (dataDS.containsKey(keyK) && this.cache.update(keyK) &&
		 * !this.mapCoW.get(threadId).containsKey(keyK)) { Update up = dataDS.get(keyK);
		 * this.mapCoW.get(threadId).put(keyK, deser_F_VALUE.apply(up.getV1()));
		 * this.statCoW.get(threadId).put(keyK, up.getStat()); vR =
		 * deser_F_VALUE.apply(upReturn.getV1());
		 * log.info("Returning from Data DS MAP. Key:{}, Version:{}", keyK,
		 * dataDS.get(keyK).getStat().getVersion()); return vR; }
		 * 
		 * Update dataFromDataStore = persistence.getDataDS(upReturn); try { if
		 * ((dataFromDataStore.getOp().equals(Operation.REMOVE_K) ||
		 * dataFromDataStore.getOp().equals(Operation.REMOVE_KV) ||
		 * dataFromDataStore.getOp().equals(Operation.GET))) { return null; }
		 * 
		 * if (dataFromDataStore.getV2() != null) { if (typeDeser) vR =
		 * deserBiF.apply(keyK, dataFromDataStore.getV2().toString()); else vR =
		 * deser_F_VALUE.apply(dataFromDataStore.getV2().toString()); } else if
		 * (dataFromDataStore.getV1() != null) { if (typeDeser) vR =
		 * deserBiF.apply(keyK, dataFromDataStore.getV1().toString()); else vR =
		 * deser_F_VALUE.apply(dataFromDataStore.getV1().toString()); }
		 * 
		 * log.
		 * info("## Went to Data Store, Data Structure:{}, Key:{}, Value:{}, Version:{}"
		 * , new Object[] { name, upReturn.getKey(), dataFromDataStore.getV1(),
		 * dataFromDataStore.getStat().getVersion() });
		 * this.mapCoW.get(threadId).put(keyK, (V) vR); return (V) vR;
		 * 
		 * } catch (NullPointerException npe) {
		 * log.info("Could not deserialize data from DS: {}", dataFromDataStore); //
		 * return map.get(key); } catch (java.lang.RuntimeException rte) {
		 * log.info("RunTimeException: {}, \nMessage: {}", dataFromDataStore,
		 * rte.getMessage()); // return map.get(key); } finally {
		 * this.cache.update(keyK); this.mapCoW.get(threadId).put(keyK, (V) vR);
		 * this.statCoW.get(threadId).put(keyK, dataFromDataStore.getStat());
		 * synchronized (lockUpdateAware) {
		 * log.info("Updating UpAware, Key:{} Version:{}", keyK,
		 * dataFromDataStore.getStat().getVersion()); this.updateAware.put(keyK,
		 * dataFromDataStore); } }
		 * 
		 * log.trace("Data DOES NOT exist at Data Store: {}/{}", name,
		 * dataFromDataStore.getKey());
		 * 
		 * return map.get(key);
		 * 
		 * }
		 * 
		 * 
		 * 
		 * log.trace("Returning data from normal map, threadId:{}", threadId); return
		 * map.get(key);
		 */

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public V put(K key, V value) {
		long threadId = Thread.currentThread().getId();

		if (this.scope.equals(Scope.GLOBAL)) {

			Update update = new Update(name, Operation.PUT, ser_F_KEY.apply(key), ser_F_VALUE.apply(value), scope,
					threadId, trace.getTxThreadId(threadId));

			if (this.statCoW.containsKey(threadId) && this.statCoW.get(threadId).containsKey(key))
				update.getStat().setVersion(new Integer(this.statCoW.get(threadId).get(key).getVersion() + 1));
			/*else
				update.getStat().setVersion(new Integer(this.dataDS.get(key).getStat().getVersion() + 1));*/
			log.trace("VALID PUT: {}", update);

			this.statCoW.get(threadId).put(key, update.getStat());
			// this.updateAware.put(key, upNewVersion);

			trace.addMemoryChanges(threadId, name, update);

			this.mapCoW.putIfAbsent(threadId, new HashMap<K, V>());
			this.cache.update(key);
			return this.mapCoW.get(threadId).put(key, value);

			/*
			 * log.debug("NOT Valid PUT: SELF_ROLLBACK, Key:{}, statCoW:{}, " //+
			 * "updateAware:{}" + ", ThreadId:{}", new Object[] { update.getKey(),
			 * this.statCoW.get(threadId).get(key).getVersion(),
			 * //this.updateAware.get(key).getStat().getVersion(), threadId });
			 * 
			 * log.debug("MapCoW: {}", mapCoW.get(threadId).remove(key));
			 * log.debug("StatCoW: {}", statCoW.get(threadId).remove(key));
			 * 
			 * trace.setStatus(threadId, PipelineStatus.SELF_ROLLBACK);
			 * 
			 * return this.mapCoW.get(threadId).put(key, value);
			 */

		} else {
			// log.trace("Put into local MAP. Key:{}, Value:{}", key, value);
			return map.put(key, value);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	@Override
	public void clear() {
		long threadId = Thread.currentThread().getId();
		log.debug("Module: " + name + ", ThreadId: " + threadId + ", clear()");

		if (this.scope.equals(Scope.GLOBAL)) {
			Update update = new Update(name, Operation.CLEAR, null, null, scope, threadId,
					trace.getTxThreadId(threadId));

			trace.addMemoryChanges(threadId, name, update);
		} else
			map.clear();

	}

	@Override
	public boolean containsKey(Object key) {
		long threadId = Thread.currentThread().getId();

		this.statCoW.putIfAbsent(threadId, new HashMap<K, Stat>());
		this.statCoW.get(threadId).putIfAbsent((K) key, new Stat());
		this.mapCoW.putIfAbsent(threadId, new HashMap<K, V>());

		log.debug("Module: " + name + ", ThreadId: " + threadId + ", containsKey(K), Key:" + key);
		if (this.scope.equals(Scope.GLOBAL)) {
			if (this.mapCoW.get(threadId).containsKey((K) key))
				return true;
		}
		return map.containsKey((K) key);
	}

	@Override
	public boolean containsValue(Object value) {
		long threadId = Thread.currentThread().getId();
		log.debug("Module: " + name + ", ThreadId: " + threadId + ", containsValue(V), Value:" + value);
		if (this.scope.equals(Scope.GLOBAL)) {
			if (this.mapCoW.containsKey(threadId))
				if (this.mapCoW.get(threadId).containsValue(value))
					return true;
		}
		return map.containsValue(value);

	}

	@Override
	public boolean isEmpty() {
		long threadId = Thread.currentThread().getId();
		log.debug("Module: " + name + ", ThreadId: " + threadId + ", isEmpty(), Scope: " + scope);
		if (this.scope.equals(Scope.GLOBAL)) {
			if (this.mapCoW.containsKey(threadId))
				if (this.mapCoW.get(threadId).isEmpty())
					return map.isEmpty();
		}
		return map.isEmpty();

	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		long threadId = Thread.currentThread().getId();

		log.debug("Module: " + name + ", ThreadId: " + threadId + ", entrySet()");

		if (this.scope.equals(Scope.GLOBAL)) {
			Set<Map.Entry<K, V>> mergedEntrySet = new HashSet<Map.Entry<K, V>>();
			if (this.mapCoW.containsKey(threadId)) {
				if (this.mapCoW.get(threadId).entrySet().size() > 0) {
					// log.trace("Map CopyOnWrite EntrySet is Not Empty, merging. {}", name);
					mergedEntrySet.addAll(this.mapCoW.get(threadId).entrySet());
					mergedEntrySet.addAll(map.entrySet());
					return mergedEntrySet;
				}
			}
		}
		log.trace("Map CopyOnWrite EntrySet is Empty, not merging. {}", name);

		/*
		 * Iterator<K> keys = map.keySet().iterator(); while (keys.hasNext()) { K k =
		 * (K) deser_F_KEY.apply(keys.next().toString()); log.info("KEY: {}, Type:{}",
		 * k, k.getClass().toString()); Link l = (Link) deser_F_KEY.apply(k.toString());
		 * log.info("KEY LINK: {}, Type:{}", l, l.getClass().toString());
		 * 
		 * }
		 */

		/*
		 * String entries2 = map.entrySet().stream().map(o ->
		 * o.getKey().getClass().toString()) .collect(Collectors.joining("\n"));
		 * log.info("Entry Set,  Third, Local MAP:\n\n{}\n", entries2);
		 */

		return map.entrySet();

	}

	@Override
	public Set<K> keySet() {
		long threadId = Thread.currentThread().getId();
		// logger.debug("Module: " + name + ", ThreadId: " + threadId + ", keySet()");

		if (this.scope.equals(Scope.GLOBAL)) {
			Set<K> setR = new HashSet<>();
			if (this.mapCoW.containsKey(threadId)) {
				if (this.mapCoW.get(threadId).keySet().size() > 0) {
					log.trace("Map CopyOnWrite KeySet is Not Empty, merging: {}", name);
					setR.addAll(this.mapCoW.get(threadId).keySet());
					setR.addAll(map.keySet());
					return setR;
				}
			}
		}
		log.trace("Map CopyOnWrite KeySet is Empty, not merging: {}", name);
		return map.keySet();

	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		long threadId = Thread.currentThread().getId();

		Update update = new Update(name, Operation.PUTALL, null, null, scope, threadId, trace.getTxThreadId(threadId));
		trace.addMemoryChanges(threadId, name, update);

		log.info("Module: " + name + ", ThreadId: " + threadId + ", putAll(Col), " + "Scope: " + scope + ", Size: "
				+ m.toString().getBytes().length);

		this.mapCoW.putIfAbsent(threadId, new HashMap<K, V>());
		this.mapCoW.get(threadId).putAll(m);

		log.info("PUTALL Not Implemented, existing...");
		System.err.println("PUTALL Not Implemented, existing...");
		System.exit(1);

	}

	@Override
	public V remove(Object key) {
		long threadId = Thread.currentThread().getId();

		if (this.scope.equals(Scope.GLOBAL)) {
			K desKey = (K) deser_F_KEY.apply(ser_F_KEY.apply((K) key));

			log.trace("Module: " + name + ", ThreadId: " + threadId + ", " + "REMOVE_K, Key: " + desKey);

			Update update = new Update(name, Operation.REMOVE_K, ser_F_KEY.apply((K) key), null, scope, threadId,
					trace.getTxThreadId(threadId));
			Update upNewVersion = new Update(name, Operation.REMOVE_K, ser_F_KEY.apply((K) key), null, scope, threadId,
					trace.getTxThreadId(threadId));

			synchronized (lockConsolidateUpdates) {
				// statCow and updateAware validity
				if (this.statCoW.containsKey(threadId) && this.updateAware.containsKey(desKey)) {
					if (this.statCoW.get(threadId).get(key).getVersion() == this.updateAware.get(key).getStat()
							.getVersion()) {
						update.getStat().setVersion(new Integer(this.updateAware.get(key).getStat().getVersion()));
						upNewVersion.getStat()
								.setVersion(new Integer(this.updateAware.get(key).getStat().getVersion() + 1));

						log.trace("Valid REMOVE_K: DataStructure:{}, Key:{}, statCoW:{}, updateAware:{}, ThreadId:{}",
								new Object[] { this.name, update.getKey(),
										this.statCoW.get(threadId).get(key).getVersion(),
										this.updateAware.get(key).getStat().getVersion(), threadId });

						this.statCoW.get(threadId).put(desKey, upNewVersion.getStat());
						this.updateAware.put(desKey, upNewVersion);

						trace.addMemoryChanges(threadId, name, update);

						this.mapCoW.putIfAbsent(threadId, new HashMap<K, V>());
						this.cache.update(desKey);

						if (this.mapCoW.containsKey(threadId))
							if (this.mapCoW.get(threadId).containsKey(desKey))
								return this.mapCoW.get(threadId).remove(desKey);

					} else {
						log.debug("NOT Valid REMOVE_K: SELF_ROLLBACK, Key:{}, statCoW:{}, updateAware:{}, ThreadId:{}",
								new Object[] { update.getKey(), this.statCoW.get(threadId).get(key).getVersion(),
										this.updateAware.get(key).getStat().getVersion(), threadId });

						mapCoW.get(threadId).remove(key);
						statCoW.get(threadId).remove(key);
					}
				}
			}

			trace.setStatus(threadId, PipelineStatus.SELF_ROLLBACK);
			return map.get(desKey);

			/*
			 * //synchronized (lockUpdateAware) {
			 * 
			 * update.getStat().setVersion(new
			 * Integer(this.updateAware.get(desKey).getStat().getVersion()));
			 * upNewVersion.getStat().setVersion(new
			 * Integer(this.updateAware.get(desKey).getStat().getVersion() + 1));
			 * 
			 * log.
			 * trace("Valid REMOVE_K: DataStructure:{}, Key:{}, statCoW:{}, updateAware:{}, ThreadId:{}"
			 * , new Object[] { this.name, update.getKey(), threadId });
			 * 
			 * //this.statCoW.get(threadId).put(desKey, upNewVersion.getStat());
			 * //this.updateAware.put(desKey, upNewVersion);
			 * 
			 * trace.addMemoryChanges(threadId, name, update);
			 * 
			 * this.mapCoW.putIfAbsent(threadId, new HashMap<K, V>());
			 * //this.cache.update(desKey);
			 * 
			 * //} if (this.mapCoW.containsKey(threadId)) if
			 * (this.mapCoW.get(threadId).containsKey(desKey)) return
			 * this.mapCoW.get(threadId).remove(desKey);
			 */
		}

		return map.remove(key);

	}

	@Override
	public int size() {
		long threadId = Thread.currentThread().getId();
		log.debug("Module: " + name + ", ThreadId: " + threadId + ", size(), Scope: " + scope);
		return map.size();
	}

	@Override
	public Collection<V> values() {
		long threadId = Thread.currentThread().getId();
		log.debug("Module: " + name + ", ThreadId: " + threadId + ", " + "values(), Scope: " + scope);
		if (this.scope.equals(Scope.GLOBAL)) {
			Collection<V> vOut = new HashSet<>();
			if (this.mapCoW.containsKey(threadId)) {
				vOut.addAll(this.mapCoW.get(threadId).values());
				vOut.addAll(map.values());
				return vOut;
			}
		}
		return map.values();
	}

	private boolean consolidateUpdates(TreeMap<Long, Update> orderedFinalPacketIn) {

		boolean consolidate = true;
		synchronized (lockConsolidateUpdates) {
			Iterator<Update> itUP = orderedFinalPacketIn.values().iterator();
			while (itUP.hasNext()) {
				Update update = itUP.next();

				if (!name.equals(update.getDataStructure())) {
					continue;
				}
				K desKey = (K) deser_F_KEY.apply(update.getKey());
				V desValue = (V) deser_F_VALUE.apply(update.getV1());

				dataDS.put(desKey, update);
				cache.update(desKey);
				//dataStoreSnapshot.putIfAbsent(desKey, new TreeMap<>());

				switch (update.getOp()) {
				case REPLACE_KV:

					this.map.replace(desKey, desValue);
					break;
				case REPLACE_KVV:
					V desValue2 = (V) deser_F_VALUE.apply(update.getV2());
					this.map.replace(desKey, desValue, desValue2);
					break;
				case PUT:
					this.map.put(desKey, desValue);
					
					/*if(desKey.toString().startsWith("Conflict"))
						log.info("APPLYING PUT, Key:{}, Value:{}, Version:{}", 
								new Object[] { desKey, desValue, update.getStat().getVersion()});*/
					//dataStoreSnapshot.get(desKey).put(update.getTxId(), desValue);
					break;
				case PUTABSENT:
					this.map.putIfAbsent(desKey, desValue);
					break;
				case PUTALL:
					// PutAll is converted into several put's.
					break;
				case REMOVE_K:
					this.map.remove(desKey);
					break;
				case REMOVE_KV:
					this.map.remove(desKey, desValue);
					break;
				case CLEAR:
					this.map.clear();
					break;
				default:
					// this.log.debug("NOT CATCHED, Update:{}", update);
					consolidate = false;
					break;
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
	public void onBatchConsumed(TreeMap<Long, Update> orderedFinalPacketIn) {
		consolidateUpdates(orderedFinalPacketIn);
	}

	@Override
	public void onMessageConsumed(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		//long threadId = Thread.currentThread().getId();
		// log.error("\n \n DOING NOTHING");
	}

	@Override
	public void onSelfRollback(long threadId) {
		try {
			this.mapCoW.get(threadId).clear();
			this.statCoW.get(threadId).clear();
		} catch (NullPointerException e) {
			log.error("NPE at onSelfRollback. ThreaId: {}", threadId);
		} catch (Exception e) {

		}

		snapshotToUse.remove(threadId);

	}

	@Override
	public void onBatchRollback(TreeMap<Long, Update> orderedFinalPacketIn) {
		// HashSet<String> already = new HashSet<>();
		synchronized (lockConsolidateUpdates) {
			Iterator<Update> itUP = orderedFinalPacketIn.values().iterator();
			while (itUP.hasNext()) {
				Update update = itUP.next();

				if (!name.equals(update.getDataStructure()))
					continue;

				try {
					this.mapCoW.get(update.getThreadId()).remove((K) update.getKey());
					this.statCoW.get(update.getThreadId()).remove((K) update.getKey());
					this.cache.invalidate((K) update.getKey());					
				} catch (Exception e) {
					log.info("Rolling back EXCEPTION, ThreadId:{}, Key:{}", update.getThreadId(), update.getKey());
				}
			}
		}

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void onDataStoreRollback(TreeMap<Long, Update> orderedFinalPacketIn) {

		HashSet<String> already = new HashSet<>();
		synchronized (lockConsolidateUpdates) {
			Iterator<Update> itUP = orderedFinalPacketIn.values().iterator();
			while (itUP.hasNext()) {
				Update update = itUP.next();

				if (!name.equals(update.getDataStructure()))
					continue;

				if (!already.contains(update.getKey())) {
					this.cache.invalidate((K) update.getKey());
					this.mapCoW.get(update.getThreadId()).remove((K) update.getKey());
					this.statCoW.get(update.getThreadId()).remove((K) update.getKey());
					this.dataDS.remove((K) update.getKey());
					already.add((String) update.getKey());
				}
			}
		}
	}

	@Override
	public void onInitialRecover(TreeMap<Long, Update> orderedFinalPacketIn) {

		synchronized (lockConsolidateUpdates) {
			Iterator<Update> itUP = orderedFinalPacketIn.values().iterator();
			while (itUP.hasNext()) {
				Update update = itUP.next();

				if (!name.equals(update.getDataStructure()))
					continue;

				K desKey = (K) deser_F_KEY.apply(update.getKey());
				V desValue = (V) deser_F_VALUE.apply(update.getV1());

				dataDS.put(desKey, update);
				cache.update(desKey);
				//dataStoreSnapshot.putIfAbsent(desKey, new TreeMap<>());

				switch (update.getOp()) {
				case REPLACE_KV:
					this.map.replace(desKey, desValue);
					break;
				case REPLACE_KVV:
					V desValue2 = (V) deser_F_VALUE.apply(update.getV2());
					this.map.replace(desKey, desValue, desValue2);
					break;
				case PUT:
					this.map.put(desKey, desValue);
					//dataStoreSnapshot.get(desKey).put(update.getTxId(), desValue);

					trace.setTxInitialCounter(update.getTxId());

					//log.debug("Applying update at Initial Recover, Class:{}, {}", this.getName(), update);
					break;
				case PUTABSENT:
					this.map.putIfAbsent(desKey, desValue);
					break;
				case PUTALL:
					// PutAll is converted into several put's.
					break;
				case REMOVE_K:
					this.map.remove(desKey);
					break;
				case REMOVE_KV:
					this.map.remove(desKey, desValue);
					break;
				case CLEAR:
					this.map.clear();
					break;
				default:
					// this.log.debug("NOT CATCHED, Update:{}", update);
					break;
				}
			}
		}

	}

}