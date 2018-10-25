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
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.heimdall.tracing.ConcurrentHashMapTracing;
import net.floodlightcontroller.heimdall.tracing.HashSetTracing;
import net.floodlightcontroller.heimdall.tracing.MapTracing;

public interface ITarService<K, V> extends IFloodlightService {
	
	public ConcurrentHashMapTracing<K, V> createConcurrentHashMapTracing(
				ConcurrentHashMap<K, V> map, 
				String name, 
				Scope scope, 
				Object listener,
				Function<? super V, String> serializer,
				BiFunction<? super K, String, ? extends V> deserializer);
	
	public ConcurrentHashMapTracing<K, V> createConcurrentHashMapTracing(
			ConcurrentHashMap<K, V> map, 
			String name, 
			Scope scope, 
			Object listener,
			Function<? super V, String> serializer,
			Function<String, ? extends V> deserializer);
	
	public HashSetTracing<K> createHashSetTracing(
				HashSet<K> set, 
				String name, 
				Scope scope, 
				Object listener,
				Function<? super K, String> serializer,
				Function<String, ? extends K> deserializer);
	
	
	/**
	 * 
	 * @param map
	 * @param name
	 * @param scope
	 * @param listener
	 * @param serializer_KEY
	 * @param deserializer_KEY
	 * @param serializer_VALUE
	 * @param deserializer_VALUE
	 * @return
	 */
	
	public MapTracing<K, V> createMapTracing(
				Map<K, V> map, 
				String name, 
				Scope scope, 
				Object listener,
				Function<? super K, String> serializer_KEY,
				Function<String, ? extends K> deserializer_KEY,
				Function<? super V, String> serializer_VALUE,
				Function<String, ? extends V> deserializer_VALUE);
	
	public MapTracing<K, V> createMapTracing(
			Map<K, V> map, 
			String name, 
			Scope scope, 
			Object listener,
			
			Function<? super K, String> serializer_KEY,
			Function<String, ? extends K> deserializer_KEY,
			Function<? super V, String> serializer_VALUE,
			
			BiFunction<? super K, String, ? extends V> deserializer_BF_VALUE);

	
	public enum Scope {
		/**
         * Global updates are stored at Data Store.
         */
        GLOBAL,        
        /**
         * Local updates are locally saved. 
         */
        LOCAL
    }
	
	public enum PipelineStatus {
		/**
         * PacketIn in execution, into pipeline. 
         */
        INITIATED,
        
        /**
         * Finished its execution, transfering update to finishedMemoryChanges
         */
        PREPARE,
        /**
         * Saving my updates and possibly some updates done by other thread(s).
         * If I'm saving some data, definitely mine update is there.      
         */
        SAVING,
        
        /**
         * Success, data was safety written into Data Store (WRITE_OK). 
         */
        SAVED,

		/**
         * Could not be saved into Data Store, needs re-initiate packet In (BAD_VERSION).. 
         */
        ROLLBACK,
        
        /**
         * Packet was ignored by some reason. Possible reasons: data field is empty, slave switch... 
         */
        IGNORED,
        /**
         * Tx aborted / invalid
         */
        TX_INVALID,
        
        /**
         * Self Rollback
         */
        SELF_ROLLBACK,
        
        /**
         * First of batch invalid with Data Store.
         */
        BATCH_ROLLBACK,
        
        /**
         * Finished pipeline.
         *  
         */
        FINISHED
    }
	public enum Operation{
		ADD,
		ADDALL,
		PUT,		
		PUTALL,
		PUTABSENT,
		REMOVE_K,
		REMOVE_KV,
		REMOVEALL,
		REPLACE_KV,
		REPLACE_KVV,
		SET,
		CONTAINS_K,
		CONTAINS_V,
		GET,
		KEYSET,
		ENTRYSET,
		VALUES,
		SIZE,
		CLEAR,
		IS_EMPTY,
		NOP
	}
	
	public enum DatastoreStatus{
		WRITE_OK,			//ok: with updates (write(s));
		READ_ONLY,			//ok: without updates (no write(s));		
		BAD_VERSION, 		//same as Zookeeper
		GENERIC_ERROR,		//some exception.
		BATCH_ABORT 		// problem at first of batch. 
	}
	
	
	
	//public void recoverFromStorage(String r);

	

}
