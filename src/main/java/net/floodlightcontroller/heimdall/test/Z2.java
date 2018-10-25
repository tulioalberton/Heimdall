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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.heimdall.ITarService;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.tracing.MapTracing;
import net.floodlightcontroller.linkdiscovery.Link;

public class Z2 implements IOFMessageListener, IFloodlightModule {

	private IFloodlightProviderService floodlightProviderService;
	
	private Logger logger;
	private Random rand;
	private MapTracing<String, Link> dataZ2;
	private static ITarService tarService;
	private HashMap<Long, Long> lastPacketIn;
	private static ConflictDegree conflictDegree;
	
	private Link link;
	/**
	 * loadWrite=[0-100]%
	 */
	private static int loadWrite;
	
	private static final Object lockA = new Integer(1);
	private static final Object lockB = new Integer(1);


	/**
	 * Conflict Degree: The Conflict Degree defines the internal conflicts (conflict
	 * among threads).
	 */
	public enum ConflictDegree {
		/**
		 * All switches write at the same Key, Key:Conflict_100. Which will incur at
		 * 100% internal conflicts.
		 */
		C_100,

		/**
		 * 50% internal conflicts
		 */
		C_50,

		/**
		 * 25% internal conflicts
		 */
		C_25,

		/**
		 * Each switch write at Switch_[DatapathId] (0% internal conflicts)
		 */
		NoConflict,
	}


	@SuppressWarnings("unchecked")
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);

		StringBuffer sb = new StringBuffer();
		sb.append("\n\tTest Configuration Parameters:");
		sb.append("\n\t\t Who Am I: " + this.getName());
		sb.append("\n\t\t Write Factor: " + loadWrite + "%");
		sb.append("\n\t\t Conflict Degree: " + conflictDegree);

		logger.info("{}\n", sb);

		dataZ2 = tarService.createMapTracing(
				new HashMap<String, Long>(), 
				"TestModule:Z2", 
				Scope.GLOBAL, 
				null,
				serializer_K, 
				deserializer_K,
				serializer_V, 
				deserializer_V);

		lastPacketIn = new HashMap<>();

	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub

		switch (msg.getType()) {
		case PACKET_IN:
			long threadId = Thread.currentThread().getId();
			int pEscrita = rand.nextInt(100);
			String writeLocal = "PatchNotDefined_Z2";
			lastPacketIn.putIfAbsent(threadId, 0L);
			
			U64 latency = U64.of(100L);
			
			link = new Link(sw.getId(), OFPort.ZERO, sw.getId(), OFPort.ZERO, latency);

			try {
				if (msg != null && lastPacketIn.containsKey(threadId)) {

					if (pEscrita < loadWrite
							/**
							 * Whether test with Mininet, this shall be commented, if test with cbench
							 * uncommented,
							 */
							//|| lastPacketIn.get(threadId) == msg.getXid() 
							//|| msg.getXid() == 0L
							){

						if (lastPacketIn.get(threadId) == msg.getXid()) {
							logger.debug("Reexecuting packetIn:{}, reason rollback.", msg.getXid());
						}

						switch (conflictDegree) {
						case C_100:// 100% conflicts
							writeLocal = "Conflict_100_Z2";
							break;

						case C_50:// 50% conflicts
							int factorA = rand.nextInt(2);
							if (factorA == 0)
								writeLocal = "Conflict_50_Z2";
							else
								writeLocal = "Switch_" + sw.getId().getLong()+"_Z2";

							break;

						case C_25:// 25% conflicts
							int factorB = rand.nextInt(4);
							if (factorB == 0)
								writeLocal = "Conflict_25_Z2";
							else
								writeLocal = "Switch_" + sw.getId().getLong()+"_Z2";

							break;

						case NoConflict:
						default:
							// Default, 0% conflicts
							writeLocal = "Switch_" + sw.getId().getLong()+"_Z2";
							break;
						}

						
						//synchronized (lockA) {
							if (dataZ2.get(writeLocal) != null) {
								dataZ2.put(writeLocal, link ); //
							} else {
								logger.info("this.data.get(writeLocal) == null, writeLocal: {}", writeLocal);
								dataZ2.put(writeLocal, link);
							}

							
						//}

						lastPacketIn.put(threadId, msg.getXid());
					}
				}
			} catch (NullPointerException npe) {
				// TODO: handle exception
				npe.printStackTrace();
			}

			return Command.CONTINUE;

		default:
			break;
		}
		return Command.CONTINUE;
	}

	Function<String, String> serializer_K = (info) -> {
		return String.valueOf(info);		
	};
	Function<String, String> deserializer_K = (String str) -> {
		return str;
	};
	
	Function<Link, String> serializer_V = (info) -> {
		logger.debug("Serializing: {}", info);
		return info.toKeyString();		
	};
	
	Function<String, Link> deserializer_V = (String str) -> {
		if(str==null) {
			return null;
			//return new Link();
		}
		else {
			String[] v = str.split(";");
			/*System.out.println("V 0: "+ v[0]);
			System.out.println("V 1: "+ v[1].toString());
			System.out.println("V 2: "+ v[2]);
			System.out.println("V 3: "+ v[3]);*/
			
			 DatapathId src = DatapathId.of(""+v[0]);
			 OFPort srcPort = OFPort.of(Integer.parseInt(""+v[1]));
			 DatapathId dst = DatapathId.of(""+v[2]);
			 OFPort dstPort = OFPort.of(Integer.parseInt(""+v[3]));
			 
			 return new Link(src, srcPort, dst, dstPort, U64.ZERO);
		}
		/*try {
			return new ObjectMapper().readValue(str, Link.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}*/
	};

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return this.getClass().getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(ITarService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(Z2.class);
		tarService = context.getServiceImpl(ITarService.class);
		rand = new Random(100);

		Map<String, String> configParams = context.getConfigParams(Z2.class);
		loadWrite = Integer.parseInt(configParams.get("loadWrite"));


		switch (configParams.get("conflictDegree")) {
		case "NoConflict":
			conflictDegree = ConflictDegree.NoConflict;
			break;
		case "C_50":
			conflictDegree = ConflictDegree.C_50;
			break;
		case "C_25":
			conflictDegree = ConflictDegree.C_25;
			break;
		case "C_100":
		default:
			conflictDegree = ConflictDegree.C_100;
			break;
		}

		// ctrlID = Integer.parseInt(floodlightProviderService.getControllerId());
	}


}
