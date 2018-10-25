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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.heimdall.ITarService;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.tracing.MapTracing;

public class Z1 implements IOFMessageListener, IFloodlightModule, IOFSwitchListener {

	private IFloodlightProviderService floodlightProviderService;
	private IOFSwitchService switchService;
	private Logger log;
	private Random rand;
	private MapTracing<String, Long> dataZ1;
	private static ITarService tarService;
	private HashMap<Long, Long> lastPacketIn;
	private HashSet<DatapathId> switchList;
	private HashSet<DatapathId> switchWrite;
	private HashMap<Long, Boolean> conflictControl;
	

	private static final Object lockA = new Integer(1);
	private static final Object lockB = new Integer(1);

	// private ReentrantReadWriteLock lock;

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
		 * 10% internal conflicts
		 */
		C_10,
		
		/**
		 * 1% internal conflicts
		 */
		C_1,
		
		/**
		 * 0.5% internal conflicts
		 */
		C_05,
		
		/**
		 * 02% internal conflicts
		 */
		C_025,
		
		
		/**
		 * Each switch write at Switch_[DatapathId] (0% internal conflicts)
		 */
		NoConflict,
	}

	public enum SwitchLoad {

		/**
		 * FullLoad, there is no load division, each switch could generate a write.
		 */
		FullLoad,

		/**
		 * SeparateLoad_50_50: 50% load is write, and 50% load is read. Not random,
		 * switches pairs read and switches odd write.
		 */
		SeparateLoad_50_50,

		/**
		 * 25% of switches write and 75% read. Switches shall be multiple of 16.
		 */
		SeparateLoad_25_75,

		/**
		 * 25% of switches read and 75% write. Switches shall be multiple of 16.
		 */
		SeparateLoad_75_25
	}

	private static ConflictDegree conflictDegree;
	private static SwitchLoad switchLoad;

	/**
	 * loadWrite=[0-100]%
	 */
	private static int loadWrite;

	@SuppressWarnings("unchecked")
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		// switchService.addOFSwitchListener(this);

		StringBuffer sb = new StringBuffer();
		sb.append("\n\tTest Configuration Parameters:");
		sb.append("\n\t\t Who Am I: " + this.getName());
		sb.append("\n\t\t Write Factor: " + loadWrite + "%");
		sb.append("\n\t\t Conflict Degree: " + conflictDegree);
		sb.append("\n\t\t Switch Load: " + switchLoad);

		log.info("{}\n", sb);

		dataZ1 = tarService.createMapTracing(new HashMap<String, Long>(), "TestModule:Z1", Scope.GLOBAL, null,
				serializer_K, deserializer_K, serializer_V, deserializer_V);

		lastPacketIn = new HashMap<>();

	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		// TODO Auto-generated method stub

		switch (msg.getType()) {
		case PACKET_IN:
			long threadId = Thread.currentThread().getId();
			int pEscrita = rand.nextInt(100);
			String writeLocal = "PatchNotDefined_Z1";
			lastPacketIn.putIfAbsent(threadId, 0L);
			conflictControl.putIfAbsent(threadId, false);

			//log.info("RECEIVED PACKET.....");
			
			try {
				if (msg != null && lastPacketIn.containsKey(threadId)) {

					if (pEscrita < loadWrite
					
					/**
					 * Whether testing with Mininet code below shall be commented, 
					 * if testing with cbench uncommented,
					 */
					 //|| lastPacketIn.get(threadId) == msg.getXid()
					 //|| msg.getXid() == 0L
					) {

						/*if (lastPacketIn.get(threadId) == msg.getXid()) {
							log.debug("Reexecuting packetIn:{}, reason rollback.", msg.getXid());
						}*/

						switch (conflictDegree) {
						case C_100:// 100% conflicts
							writeLocal = "Conflict_100_Z1";
							break;

						case C_50:// 50% conflicts
							int factorA = rand.nextInt(2);
							if (factorA == 0 || conflictControl.get(threadId)) {
								if(lastPacketIn.get(threadId) == msg.getXid()|| 
										!conflictControl.get(threadId)) {
									//conflictControl.put(threadId, true);
									writeLocal = "Conflict_50_Z1";
								}
								else {
									conflictControl.put(threadId, false);
									writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
								}
							}
							else
								writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";

							break;

						case C_25:// 25% conflicts
							int factorB = rand.nextInt(4);
							if (factorB == 0 || conflictControl.get(threadId)) {
								if(lastPacketIn.get(threadId) == msg.getXid()|| 
										!conflictControl.get(threadId)) {
									//conflictControl.put(threadId, true);
									writeLocal = "Conflict_25_Z1";
								}
								else {
									conflictControl.put(threadId, false);
									writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
								}
							}
							else
								writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";

							break;
						case C_10:// 10% conflicts
							int factorC = rand.nextInt(100);
							
							if (factorC < 10 || conflictControl.get(threadId)) {
								
								if(lastPacketIn.get(threadId) == msg.getXid()|| 
										!conflictControl.get(threadId)) {
									writeLocal = "Conflict_10_Z1";
									//conflictControl.put(threadId, true);
								}
								else {
									conflictControl.put(threadId, false);
									writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
								}
							}
							else
								writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";

							break;
							
						case C_1:// 1% conflicts
							int factorD = rand.nextInt(100);
							if (factorD == 0 || conflictControl.get(threadId)) {
								
								
								if(lastPacketIn.get(threadId) == msg.getXid() || 
										!conflictControl.get(threadId)) {
									//conflictControl.put(threadId, true);
									writeLocal = "Conflict_1_Z1";
								}
								else {
									conflictControl.put(threadId, false);
									writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
								}
							}
							else
								writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
							
							break;
						case C_05:// 0.5% conflicts
							int factorE = rand.nextInt(200);
							if (factorE == 0 || conflictControl.get(threadId)) {
								if(lastPacketIn.get(threadId) == msg.getXid()|| 
										!conflictControl.get(threadId)) {
									writeLocal = "Conflict_05_Z1";
									//conflictControl.put(threadId, true);
								}
								else {
									conflictControl.put(threadId, false);
									writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
								}
							}
							else
								writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";

							break;
						case C_025:// 0.25% conflicts
							int factorF = rand.nextInt(400);
							if (factorF == 0 || conflictControl.get(threadId)) {
								if(lastPacketIn.get(threadId) == msg.getXid()|| 
										!conflictControl.get(threadId)) {
									//conflictControl.put(threadId, true);
									writeLocal = "Conflict_025_Z1";
								}
								else {
									conflictControl.put(threadId, false);
									writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
								}
							}
							else
								writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";

							break;
						case NoConflict:
						default:
							// Default, 0% conflicts
							writeLocal = "Switch_" + sw.getId().getLong() + "_Z1";
							break;
						}
						
						/*if (rand.nextInt(200) == 0) {
							log.debug("#$#$#$#$ REMOVING KEY: {}", writeLocal);
							if (dataZ1.get(writeLocal) != null)
								dataZ1.remove(writeLocal);
							else
								log.info("#$#$#$#$ KEY: {} Does Not exist, not remoing.", writeLocal);
						} else {*/
							//log.debug("$$$$$ First update at: {}", writeLocal);
							// synchronized (lockA) {
							if (dataZ1.get(writeLocal) != null) {
								dataZ1.put(writeLocal, dataZ1.get(writeLocal) + 1); //
							} else {
								log.debug("this.dataZ1.get({}) == null", writeLocal);
								dataZ1.put(writeLocal, 1L);
							}

							/*log.debug("$$$$$ Second update at: {}", writeLocal);

							if (dataZ1.get(writeLocal) != null) {
								dataZ1.put(writeLocal, dataZ1.get(writeLocal) + 1); //
							} else {
								log.debug("this.dataZ1.get({}) == null", writeLocal);
								dataZ1.put(writeLocal, 1L);
							}*/
						//}
						// System.exit(1);
						// }

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

	Function<Long, String> serializer_V = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};

	Function<String, Long> deserializer_V = (String str) -> {
		if (str == null) {
			return 0L;
		}
		try {
			return new ObjectMapper().readValue(str, Long.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
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
		switchService = context.getServiceImpl(IOFSwitchService.class);
		log = LoggerFactory.getLogger(Z1.class);
		tarService = context.getServiceImpl(ITarService.class);
		rand = new Random(100);
		switchList = new HashSet<>();
		switchWrite = new HashSet<>();
		conflictControl = new HashMap<>();
		// lock = new ReentrantReadWriteLock();

		Map<String, String> configParams = context.getConfigParams(Z1.class);
		loadWrite = Integer.parseInt(configParams.get("loadWrite"));

		switch (configParams.get("switchLoad")) {
		case "SeparateLoad_50_50":
			switchLoad = SwitchLoad.SeparateLoad_50_50;
			log.info("Definning switchLoad to: {}", switchLoad);
			break;
		case "SeparateLoad_75_25":
			switchLoad = SwitchLoad.SeparateLoad_25_75;
			log.info("Definning switchLoad to: {}", switchLoad);
			break;
		case "SeparateLoad_25_75":
			switchLoad = SwitchLoad.SeparateLoad_25_75;
			log.info("Definning switchLoad to: {}", switchLoad);
			break;
		case "FullLoad":
		default:
			switchLoad = SwitchLoad.FullLoad;
			log.info("Definning switchLoad to: {}", switchLoad);
			break;
		}

		switch (configParams.get("conflictDegree")) {
		
		case "C_100":
			conflictDegree = ConflictDegree.C_100;	
			break;
		
		case "C_50":
			conflictDegree = ConflictDegree.C_50;
			break;
		
		case "C_25":
			conflictDegree = ConflictDegree.C_25;
			break;
		
		case "C_10":
			conflictDegree = ConflictDegree.C_10;
			break;
		
		case "C_1":
			conflictDegree = ConflictDegree.C_1;
			break;
		
		case "C_05":
			conflictDegree = ConflictDegree.C_05;
			break;
		
		case "C_025":
			conflictDegree = ConflictDegree.C_025;
			break;
		
		case "NoConflict":
		default:
			conflictDegree = ConflictDegree.NoConflict;
			break;
		}

		// ctrlID = Integer.parseInt(floodlightProviderService.getControllerId());
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		switchList.add(switchId);
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		switchList.remove(switchId);
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		Iterator<DatapathId> it;

		switch (switchLoad) {
		case SeparateLoad_75_25:
			switchWrite.clear();
			if (switchList.size() % 16 == 0) {
				int ref = switchList.size() / 4;
				int quarter = switchList.size() / ref;
				int i = 1;
				it = switchList.iterator();
				while (it.hasNext()) {
					DatapathId dpId = (DatapathId) it.next();
					if (i != quarter) {
						switchWrite.add(dpId);
						i = 0;
					}
					i++;
				}
				log.info("Switches defined as write switches: {}", switchWrite.size());
			} else {
				log.info("To set SeparateLoad_25_75 switch load, the switch size shall " + "be multiple of 16."
						+ " Defining switchLoad fo FullLoad.");
				switchWrite.addAll(switchService.getAllSwitchDpids());
				log.info("Switches defined as write switches: {}", switchWrite.size());
			}
			break;
		case SeparateLoad_25_75:
			switchWrite.clear();
			if (switchList.size() % 16 == 0) {
				int ref = switchList.size() / 4;
				int quarter = switchList.size() / ref;
				int i = 1;
				it = switchList.iterator();
				while (it.hasNext()) {
					DatapathId dpId = (DatapathId) it.next();
					if (i == quarter) {
						switchWrite.add(dpId);
						i = 0;
					}
					i++;
				}
				log.info("Switches defined as write switches: {}", switchWrite.size());
			} else {
				log.info("To set SeparateLoad_25_75 switch load, the switch size shall " + "be multiple of 16."
						+ " Defining switchLoad fo FullLoad.");
				switchWrite.addAll(switchService.getAllSwitchDpids());
				log.info("Switches defined as write switches: {}", switchWrite.size());
			}
			break;
		case SeparateLoad_50_50:
			switchWrite.clear();
			it = switchList.iterator();
			while (it.hasNext()) {
				DatapathId dpId = (DatapathId) it.next();
				if (dpId.getLong() % 2 == 0) {
					switchWrite.add(dpId);
				}
			}
			log.info("Switches defined as write switches: {}", switchWrite.size());
			break;
		case FullLoad:
		default:
			switchWrite.addAll(switchService.getAllSwitchDpids());
			// logger.info("Switches defined as write switches: {}", switchWrite.size());
			break;
		}

	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
		// TODO Auto-generated method stub
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
	}

	@Override
	public void switchDeactivated(DatapathId switchId) {
		// TODO Auto-generated method stub
	}

}
