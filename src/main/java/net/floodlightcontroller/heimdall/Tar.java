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

package net.floodlightcontroller.heimdall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.ControllerCounters;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.heimdall.tracing.ConcurrentHashMapTracing;
import net.floodlightcontroller.heimdall.tracing.HashSetTracing;
import net.floodlightcontroller.heimdall.tracing.MapTracing;
import net.floodlightcontroller.threadpool.IThreadPoolService;


public class Tar<K, V> implements 	IFloodlightModule,									
									IOFSwitchListener,
									ITarService<K, V>{

	protected static IOFSwitchService switchService;
	protected static IFloodlightProviderService floodlightProviderService;
	protected static ControllerCounters counter;
	
	
	// Recover modules automatically
	private static ArrayList<String> toRecoverLocal;
	private static ArrayList<String> toRecoverGlobal;

	private static boolean masterLeader = false;

	private static IThreadPoolService threadPoolService;

	// dispatch every STATISTIC_TASK_INTERVAL time
	private static SingletonTask statisticLatencyTask;
	private final int SUMMARY_TASK_INTERVAL = 60;//seconds
	private final int LATENCY_TASK_INTERVAL = 120;//seconds

	//From floodlightdefault.properties file.
	private static PersistentDomain persistence;
	private int cacheSize = 8192;
	private int cacheExpirationTimeMS = 3600000;//One hour
	private String zkHostPort="192.168.1.100:2181";//default
	private int zkTimeout=8000;//default
	
	private static SingletonTask infoSummaryTask;

	private static Logger logger;
	private static UtilDurable utilDurable;

	private static String controllerId;

	private static Tar instanceTar = null;

	
	public synchronized static Tar getInstance() {
		if (instanceTar == null) {
			instanceTar = new Tar();
			//System.err.println("Warning, should fall here just once, Tar.");
			return instanceTar;
		} else{
			return instanceTar;
		}
	}

	public void setSwitchService(IOFSwitchService sw) {
		switchService = sw;
	}

	public void setPersistence(PersistentDomain p) {
		persistence = p;
	}
	
	public IOFSwitchService getSwitchService() {
		return switchService;
	}

		
	public void setIFloodlightProviderService(IFloodlightProviderService fps) {
		floodlightProviderService = fps;
	}

	public IFloodlightProviderService getIFloodlightProviderService() {
		return floodlightProviderService;
	}

	public ArrayList<String> getToRecoverLocal() {
		return this.toRecoverLocal;
	}
	public ArrayList<String> getToRecoverGlobal() {
		return this.toRecoverGlobal;
	}
	
	public void setSwitchRole(OFControllerRole role, DatapathId dpSwId) {

		IOFSwitch sw = getSwitchService().getActiveSwitch(dpSwId);
		OFRoleReply reply = null;
		UtilDurable utilDurable = new UtilDurable();
		reply = utilDurable.setSwitchRole(sw, role);

		if (reply != null) {
			logger.info("DEFINED {} as {}!", sw.getId(), role);
		} else
			logger.info("Reply NULL!");
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		logger.debug("SwitchAdded switchId:{}, ThreadId:{}", switchId, Thread.currentThread().getId());
		storeLatencies();
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		logger.debug("SwitchRemoved switchId:{}, ThreadId:{}", switchId, Thread.currentThread().getId());
		storeLatencies();
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		logger.debug("SwitchActivated switchId:{}, ThreadId:{}", switchId, Thread.currentThread().getId());
		//storeLatencies();
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
		logger.trace("SwitchPortChanged switchId:{}, ThreadId:{}", switchId, Thread.currentThread().getId());
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		logger.trace("SwitchChanged switchId:{}, ThreadId:{}", switchId, Thread.currentThread().getId());
		//storeLatencies();
	}

	@Override
	public void switchDeactivated(DatapathId switchId) {
		logger.debug("SwitchDeactivated switchId:{}, ThreadId:{}", switchId, Thread.currentThread().getId());
		storeLatencies();
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ITarService.class);
		return l;

	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(ITarService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		l.add(IThreadPoolService.class);
		l.add(IDebugCounterService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		Tar.getInstance();		
		
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(Tar.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		
		
		Map<String, String> configParams = context.getConfigParams(Tar.class);
		this.cacheSize = Integer.parseInt(configParams.get("cacheSize"));
		logger.info("Defining cache size to: {}",this.cacheSize);
		
		this.cacheExpirationTimeMS = Integer.parseInt(configParams.get("cacheExpirationTimeMS"));
		logger.info("Defining cache expiration time(ms) to: {}",this.cacheExpirationTimeMS);
		
		this.zkHostPort = configParams.get("zkHostPort");
		logger.info("Defining zkHostPort to: {}",this.zkHostPort);
		
		this.zkTimeout = Integer.parseInt(configParams.get("zkTimeout"));
		logger.info("Defining zkTimeout to: {}",this.zkTimeout);

		persistence = PersistentDomain.getInstance();
		
		setControllerId(floodlightProviderService.getControllerId());

		utilDurable = new UtilDurable();
		toRecoverLocal = new ArrayList<>();
		toRecoverGlobal= new ArrayList<>();
	
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {

		setIFloodlightProviderService(floodlightProviderService);
		setSwitchService(switchService);
		setPersistence(persistence);
		switchService.addOFSwitchListener(this);
		
		persistence.setMeActive(controllerId);
		persistence.createControllerLocalData(controllerId);
		persistence.concurrenceAwareWatcher();
		bootStrapping();
		
			
		
		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
		
		// ============ INFO SUMMARY TASK ====================

		/*infoSummaryTask = new SingletonTask(ses, new Runnable() {

			@Override
			public void run() {
				logger.info("-------------------------------------------------");
				logger.info("ALL SWITCH DPIDs");
				Set<DatapathId> switchSet = switchService.getAllSwitchDpids();
				Iterator<DatapathId> itD = switchSet.iterator();
				while (itD.hasNext()) {
					DatapathId dpid = itD.next();
					logger.info("\tSwitch: {}", dpid);
					IOFSwitch sw = switchService.getSwitch(dpid);
					logger.info("\t\tSW Status: {}, ControllerRole: {}", sw.getStatus(), sw.getControllerRole());
				}
				ControllerCounters cc = floodlightProviderService.getCounters();
				logger.info("\nPacketIn:{}, Write(s):{}, Remove:{}, Data Store Access(es):{}, "
						+ "Bad Version(s):{}, PacketInRollback:{}, PacketInStopped:{}", 
						new Object[]{cc.packetIn.getCounterValue(), 
									 cc.write.getCounterValue(), 
									 cc.remove.getCounterValue(), 
									 cc.dataStoreAccess.getCounterValue(),
									 cc.badVersion.getCounterValue(),
									 cc.packetInRollback.getCounterValue(),
									 cc.packetInStopped.getCounterValue()});
			
				//infoSummaryTask.reschedule(SUMMARY_TASK_INTERVAL, TimeUnit.SECONDS);
			
			}

		});*/
		//infoSummaryTask.reschedule(SUMMARY_TASK_INTERVAL, TimeUnit.SECONDS);

		// ============ INFO SUMMARY TASK ====================

		
		// ============ LATENCY TASK; ====================
		statisticLatencyTask = new SingletonTask(ses, new Runnable() {
			@Override
			public void run() {
				storeLatencies();
				statisticLatencyTask.reschedule(LATENCY_TASK_INTERVAL, TimeUnit.SECONDS);
			}
		});
		statisticLatencyTask.reschedule(LATENCY_TASK_INTERVAL, TimeUnit.SECONDS);

		logger.info("\n\n\t=================================================================="
				+ "  \n\t     Heimdall initialized. Am I Master? "+masterLeader + ". Controller ID: "+controllerId
				+"   \n\t==================================================================\n");
		
		
	}

	public boolean imLeader() {
		return masterLeader;
	}

	public boolean bootStrapping() {
		if (!masterLeader) 
			masterLeader = persistence.masterLeader();
		persistence.leaveGlobalWatcher(masterLeader);
		return masterLeader;
	}

	public int getControllerId() {
		return Integer.parseInt(controllerId);
	}

	public void setControllerId(String ctrlIn) {
		controllerId = ctrlIn;
	}


	public void activateSwitches(HashMap<Integer, ArrayList<Long>> activate) {

		Integer myId = getControllerId();
		if(!activate.containsKey(myId))
			return;
		ArrayList<Long> switches = activate.get(myId);

	
		for (int i = 0; i < switches.size(); i++) {
			try {
				Long sw = switches.get(i);
				DatapathId dpid = DatapathId.of(sw);
				
				if (switchService.getActiveSwitch(dpid) != null)
					if (switchService.getSwitch(DatapathId.of(sw)).getControllerRole()
							.compareTo(OFControllerRole.ROLE_MASTER) != 0) {
						logger.debug("CtrlID: {}, Activating Switch: {}, as master switch.", myId, DatapathId.of(sw));
						setSwitchRole(OFControllerRole.ROLE_MASTER, DatapathId.of(sw));
						//Thread.sleep(80);
					} else {
						logger.trace("CtrlID: {}, Switch: {}, already as master switch.", myId, DatapathId.of(sw));
					}
			} catch (NullPointerException npe) {
				logger.debug("Problems to get switches coming from execAlg...");
				npe.printStackTrace();
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}

		}

		Iterator<Integer> itCtrl = activate.keySet().iterator();
		while (itCtrl.hasNext()) {
			Integer i = (Integer) itCtrl.next();
			if (myId != i) {
				ArrayList<Long> slaves = activate.get(i);
				for (int j = 0; j < slaves.size(); j++) {
					try {
						Long sw = slaves.get(j);
						if (switchService.getSwitch(DatapathId.of(sw)).getControllerRole()
								.compareTo(OFControllerRole.ROLE_SLAVE) != 0) {
							Thread.sleep(100);
							setSwitchRole(OFControllerRole.ROLE_SLAVE, DatapathId.of(sw));
							logger.debug("CtrlID:{}, SwitchID:{} setting as ROLE_SLAVE.", myId, DatapathId.of(sw));
						} else {
							logger.trace("CtrlID:{}, SwitchID:{} already as ROLE_SLAVE.", myId, DatapathId.of(sw));
						}
					}
					catch(NullPointerException npe){
						logger.debug("NullPointerException: {}", npe.getMessage());
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		
	}
	
	public void storeLatencies() {
		HashMap<Long, Long> latencyIndividual = new HashMap<>();
		Map<DatapathId, IOFSwitch> switchMap = switchService.getAllSwitchMap();
		Iterator<IOFSwitch> it = switchMap.values().iterator();
		while (it.hasNext()) {
			IOFSwitch sw = (IOFSwitch) it.next();
			latencyIndividual.put(sw.getId().getLong(), sw.getLatency().getValue());
		}

		if (latencyIndividual.size() > 0){
			logger.debug("Saving latency individual, total switche(s): {}.", latencyIndividual.size());
			persistence.storeLatencyIndividualData(latencyIndividual, controllerId);			
			}
		else
			logger.debug("Not saving latency individual, empty!");
	}
	
	

	/**
	 * Some update's origin is different from a packet_in (i.e. LLDP from link discovery).
	 * The temporary updates are stored on a Copy On Write data structure.
	 * The CoW structure is volatile, data from CoW exist only at packet_in execution pipeline and initiates empty.
	 * Which means, to consolidate it is necessary execute the method ConsolidateCoW or the data might be 
	 * overwritten (separated by thread).
	 * LLDP events dispatch updates as well and we need to deal with. 
	 * For instance, module Link Discovery use LLDP to gather information about links and the network. 
	 * The receiving of a LLDP implies into NIB updates sometimes, to consolidate this updates ...terminar
	 */
	
	@Override
	public ConcurrentHashMapTracing<K, V> createConcurrentHashMapTracing(
			ConcurrentHashMap<K, V> map, 
			String name, 
			Scope scope, 
			Object listener,
			Function<? super V, String> serializer,
			BiFunction<? super K, String, ? extends V> deserializer) {		
		ConcurrentHashMapTracing<K, V> m = new ConcurrentHashMapTracing<>(map, name, scope, 
				cacheSize, cacheExpirationTimeMS, serializer, deserializer);
		logger.trace("Added module: {} to recover, Scope: {}.", name, scope.toString());
		if(scope.equals(Scope.GLOBAL)){
			persistence.createGlobalData(name);
			toRecoverGlobal.add(name);
		}else{
			persistence.createLocalData(name);
			toRecoverLocal.add(name);
		}
		getIFloodlightProviderService().addRollbackListener(m.getIControllerRollbackListener());
		getIFloodlightProviderService().addCompletionListener(m.getIControllerCompletionListener());
		
		//getIFloodlightProviderService().doInitialRecover(name, m);
		
		return m;
	}

	@Override
	public ConcurrentHashMapTracing<K, V> createConcurrentHashMapTracing(
			ConcurrentHashMap<K, V> map, 
			String name, 
			Scope scope, 
			Object listener,
			Function<? super V, String> serializer,
			Function<String, ? extends V> deserializer) {		
		ConcurrentHashMapTracing<K, V> m = new ConcurrentHashMapTracing<>(map, name, scope, 
				cacheSize, cacheExpirationTimeMS, serializer, deserializer);
		logger.trace("Added module: {} to recover, Scope: {}.", name, scope.toString());
		if(scope.equals(Scope.GLOBAL)){
			persistence.createGlobalData(name);
			toRecoverGlobal.add(name);
		}else{
			persistence.createLocalData(name);
			toRecoverLocal.add(name);
		}
		getIFloodlightProviderService().addRollbackListener(m.getIControllerRollbackListener());
		getIFloodlightProviderService().addCompletionListener(m.getIControllerCompletionListener());
		
		//getIFloodlightProviderService().doInitialRecover(name, m);
		return m;
	}

	@Override
	public HashSetTracing<K> createHashSetTracing(
			HashSet<K> set, 
			String name, 
			Scope scope, 
			Object listener,
			Function<? super K, String> serializer,
			Function<String, ? extends K> deserializer) {
		HashSetTracing<K> m = new HashSetTracing<>(set, name, scope, cacheSize, cacheExpirationTimeMS,
				serializer, deserializer);
		logger.trace("Added module: {} to recover, Scope: {}.", name, scope.toString());
		if(scope.equals(Scope.GLOBAL)){
			persistence.createGlobalData(name);
			toRecoverGlobal.add(name);
		}else{
			persistence.createLocalData(name);
			toRecoverLocal.add(name);
		}
		getIFloodlightProviderService().addRollbackListener(m.getIControllerRollbackListener());
		getIFloodlightProviderService().addCompletionListener(m.getIControllerCompletionListener());		
		
		//getIFloodlightProviderService().doInitialRecover(name, m);
		
		return m;
	}
	
	@Override
	public MapTracing<K, V> createMapTracing(
			Map<K, V> map, 
			String name, 
			Scope scope, 
			Object listener,
			Function<? super K, String> serializer_KEY,
			Function<String, ? extends K> deserializer_KEY,
			Function<? super V, String> serializer_VALUE,
			Function<String, ? extends V> deserializer_VALUE) {
		
		MapTracing<K, V> m = new MapTracing<>(
				map, 
				name, 
				scope, 
				cacheSize, 
				cacheExpirationTimeMS, 
				serializer_KEY, 
				deserializer_KEY,
				serializer_VALUE,
				deserializer_VALUE);
		
		logger.info("Added module: {}, to recover, Scope: {}.", name, scope.toString());
		
		if(scope.equals(Scope.GLOBAL)){
			persistence.createGlobalData(name);
			toRecoverGlobal.add(name);
		}else{
			persistence.createLocalData(name);
			toRecoverLocal.add(name);
		}
		/**
		 * There some events triggered by REST app, and this can be perpetuated with the listeners scheme. 
		 */
		/*if(listener instanceof ACL){
			aclService.addACLListener(m.getIACLListener());
			logger.info("addACLListener to mapTracing, [{}], Listener:{}", name, listener);
		}*/	
		
		getIFloodlightProviderService().addRollbackListener(m.getIControllerRollbackListener());
		getIFloodlightProviderService().addCompletionListener(m.getIControllerCompletionListener());
		
		getIFloodlightProviderService().doInitialRecover(name, m);
		return m;
	}
	@Override
	public MapTracing<K, V> createMapTracing(
			Map<K, V> map, 
			String name, 
			Scope scope, 
			Object listener,
			
			Function<? super K, String> serializer_KEY,
			Function<String, ? extends K> deserializer_KEY,
			Function<? super V, String> serializer_VALUE,
			
			BiFunction<? super K, String, ? extends V>  deserializer_BF_VALUE
			) {
		MapTracing<K, V> m = new MapTracing<>(
				map, 
				name, 
				scope, 
				cacheSize, 
				cacheExpirationTimeMS,
				serializer_KEY, 
				deserializer_KEY,
				serializer_VALUE,
				deserializer_BF_VALUE
				);
		logger.info("Added module: {}, to recover, Scope: {}.", name, scope.toString());
		if(scope.equals(Scope.GLOBAL)){
			persistence.createGlobalData(name);
			toRecoverGlobal.add(name);
		}else{
			persistence.createLocalData(name);
			toRecoverLocal.add(name);
		}
		
		getIFloodlightProviderService().addRollbackListener(m.getIControllerRollbackListener());
		getIFloodlightProviderService().addCompletionListener(m.getIControllerCompletionListener());
		
		getIFloodlightProviderService().doInitialRecover(name, m);
		
		return m;
	}

	
}
