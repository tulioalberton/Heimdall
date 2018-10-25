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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.Transaction;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.heimdall.ITarService.DatastoreStatus;
import net.floodlightcontroller.heimdall.ITarService.Operation;
import net.floodlightcontroller.heimdall.ITarService.Scope;
import net.floodlightcontroller.heimdall.tracing.Update;
import net.floodlightcontroller.heimdall.zkMonitor.ZKGlobalMonitor;

public class PersistentDomain implements Watcher {

	private static PersistentDomain instancePersistence = null;
	private Logger log;
	private ObjectMapper mapper;
	// private boolean imLeader = false;

	//Used to recover at Initial startUp.
	private HashSet<String> toRecoverGlobal;
	
	private boolean masterLeader=false;
	// ZooKeeper needs
	private String zkHostPort = "192.168.1.100:2181";// default
	private int zkTimeout = 1000;// ms
	private ZooKeeper zk;
	private ZKGlobalMonitor zkGM;
	
	/**
	 * Configuration of persistence to all domain nodes
	 * 
	 * DO NOT CHANGE the names of folders, it is HARD CODED at ZKGlobalMonitor.
	 */
	private String rootNode = "/heimdall";
	private String activeCtrl = rootNode + "/activeCtrl";
	private String localData = rootNode + "/LocalData";
	private String globalData = rootNode + "/GlobalData";
	private String masterLeaderNode = rootNode + "/masterLeader";
	private String concurrenceAware = rootNode + "/concurrenceAware";	
	private String latency = rootNode + "/Latency";
	private String latencyConsolidate = latency + "/Consolidate";
	private String latencyIndividual = latency + "/Individual";
	private String lastMap = latency + "/LastMap";
	

	// Monitor and specific client threads 
	
	private Tar tarInstance;
//private HashMap<Long, ZooKeeper> zkClients;
	
	
	public synchronized static PersistentDomain getInstance() {
		// return new PersistentDomain();
		if (instancePersistence == null) {
			//System.err.println("Warning, should fall here just once, PersistentDomain.");
			instancePersistence = new PersistentDomain();
			return instancePersistence;
		} else {
			return instancePersistence;
		}
	}

	public PersistentDomain() {
		log = LoggerFactory.getLogger(PersistentDomain.class);
		//zkClients = new HashMap<>();
		try {
			zkGM = new ZKGlobalMonitor();
			zk = new ZooKeeper(this.zkHostPort, this.zkTimeout, zkGM);
			zkGM.setZK(zk);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		tarInstance = Tar.getInstance();
		mapper = new ObjectMapper();
		createInitialConfig(rootNode, "Root node ");
		createInitialConfig(activeCtrl, "Active Controllers node ");
		createInitialConfig(localData, "Local data Controller modules");
		createInitialConfig(globalData, "Global data Controller modules");
		createInitialConfig(latency, "Latency folder");
		createInitialConfig(latencyConsolidate, "Latency Consolidate");
		createInitialConfig(latencyIndividual, "Latency Individual");
		createInitialConfig(lastMap, "Last map saved by leader.");
		createInitialConfig(concurrenceAware, "To deal with concurrence amongst controller.");
		
		toRecoverGlobal = new HashSet<>();
	}

	private boolean createInitialConfig(String znodeIn, String info) {
		try {
			zk.create(znodeIn, info.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			log.debug("{} : {}", info, znodeIn);
			return true;
		} catch (NodeExistsException ne) {
			log.debug("{} {} already exists, no problem.", info, znodeIn);
			return false;
		} catch (InterruptedException | KeeperException e) {
			return false;
		}
	}

	protected boolean createGlobalData(String name) {
		try {
			zk.create(globalData + "/" + name, name.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			toRecoverGlobal.add(globalData + "/" + name);
			log.debug("Created global info: {}.", globalData + "/" + name);
			return true;
		} catch (NodeExistsException ne) {
			log.debug("Already exist: {} : No problem.", globalData + "/" + name);
			return false;
		} catch (InterruptedException | KeeperException e) {
			return false;
		}
		
	}

	protected boolean createLocalData(String name) {
		try {
			zk.create(localData + "/" + tarInstance.getControllerId() + "/" + name, name.getBytes(),
					Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			log.debug("Created global info: {}.", localData + "/" + tarInstance.getControllerId() + "/" + name);
			return true;
		} catch (NodeExistsException ne) {
			log.debug("Already exist: {} : No problem.",
					localData + "/" + tarInstance.getControllerId() + "/" + name);
			return false;
		} catch (InterruptedException | KeeperException e) {
			return false;
		}
	}

	protected void createControllerLocalData(String ctrlId) {
		try {
			zk.create(localData + "/" + ctrlId, ctrlId.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			log.debug("Created Local controller data: {}.", localData + "/" + tarInstance.getControllerId());
		} catch (NodeExistsException ne) {
			log.debug("Already exist: {} : No problem.", localData + "/" + tarInstance.getControllerId());
		} catch (InterruptedException | KeeperException e) {
		}
	}

	public String getDir(String dir) {
		switch (dir) {
		case "latencyIndividual":
			return latencyIndividual;
		case "latencyConsolidate":
			return latencyConsolidate;
		default:
			return rootNode;
		}
	}

	protected boolean masterLeader() {
		String info = "Master Leader ID: " + this.tarInstance.getControllerId();
		try {
			zk.create(masterLeaderNode, info.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
			log.info("I'm MASTER leader: {}", masterLeaderNode);
			masterLeader=true;
			return masterLeader;
		} catch (KeeperException | InterruptedException e) {
			log.info("I'm NOT MASTER leader, leaving watcher: {}", masterLeaderNode);
			masterLeader=false;
			masterWatcher();			
			return masterLeader;
		}
	}

	/*
	 * protected boolean acquireGlobalLock() { String info =
	 * "Global lock acquired by CtrlId:{} " + tarInstance.getControllerId(); try
	 * { zk.create(globalLock, info.getBytes(), Ids.OPEN_ACL_UNSAFE,
	 * CreateMode.EPHEMERAL); logger.debug("{}", info); return true; } catch
	 * (KeeperException | InterruptedException e) { logger.debug("NOT: {}",
	 * info); return false; } } protected boolean releaseGlobalLock() { String
	 * info = "Global lock released by CtrlId:{} " +
	 * tarInstance.getControllerId(); try { zk.delete(globalLock, -1);
	 * logger.debug("{}", info); return true; } catch (KeeperException |
	 * InterruptedException e) { logger.debug("NOT: {}", info); return false; }
	 * }
	 */

	public void informContention(Update update) {
		try {
			zk.create(concurrenceAware + "/" + update.getKey(), 
					this.mapper.writeValueAsBytes(update), 
					Ids.OPEN_ACL_UNSAFE,
					CreateMode.EPHEMERAL);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		} catch (KeeperException e) {
			if(KeeperException.Code.NODEEXISTS.equals(e.code())) {
				log.debug("Already informed about contention.");
				return;
			}
			
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	public void removeContention(String path) {
		try {
			zk.delete(path, -1);
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	protected void concurrenceAwareWatcher() {
		try {
			zk.getChildren(concurrenceAware, zkGM, null);
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	protected void masterWatcher() {
		try {
			zk.getData(masterLeaderNode, zkGM, null);
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public ResultDS storeData(TreeMap<Long, Update> treeUpdates) {

		long threadId = Thread.currentThread().getId();
		Transaction t = zk.transaction();
		Queue<Update> auxQueue = new LinkedList<>();
			
		String path = "";
		byte[] byteArray;
		int totalBytes = 0;
		
		ResultDS r = new ResultDS();

		Iterator<Long> itStore = treeUpdates.keySet().iterator();
		while (itStore.hasNext()) {
			Long timeStamp = (Long) itStore.next();
			Update up = treeUpdates.get(timeStamp);

			auxQueue.add(up);
			try {
				switch (up.getOp()) {
				case GET:
					r.incGetCounter();
					break;

				case KEYSET:
				case ENTRYSET:
				case VALUES:
				case CONTAINS_K:
				case CONTAINS_V:
					r.incReadCounter();
					break;

				case SIZE:
				case IS_EMPTY:
					r.incOtherCounter();
					break;

				case CLEAR:
					if (up.getScope().equals(Scope.LOCAL)) {
						path = localData + "/" + tarInstance.getControllerId() + "/" + up.getDataStructure() + "/"
								+ up.getKey();
						t.delete(path, -1);
						log.debug("Inserting CLEAR on Transaction, LOCAL data, Version:-1, Path:{}", path);
					} else if (up.getScope().equals(Scope.GLOBAL)) {
						path = globalData + "/" + up.getDataStructure() + "/" + up.getKey();
						t.delete(path, -1);
						log.debug("Inserting CLEAR on Transaction, GLOBAL data, Path:{},", path);
					}
					r.incClearCounter();
					break;
				case REMOVE_K:
				case REMOVE_KV:
					if (up.getScope().equals(Scope.LOCAL)) {
						path = localData + "/" + tarInstance.getControllerId() + "/" + up.getDataStructure() + "/" + up.getKey();
						 t.setData(path, mapper.writeValueAsBytes(up), -1);
						// t.delete(path, -1);
						log.debug("Inserting REMOVE_K|KV on Transaction, LOCAL data, Version:-1, Path:{}", path);
					}
					if (up.getScope().equals(Scope.GLOBAL)) {
						if (up.isSecondLevel())
							path = globalData + "/" + up.getDataStructure() + "/" + up.getSecondLevel() + "/"+ up.getKey();
						else
							path = globalData + "/" + up.getDataStructure() + "/" + up.getKey();

						t.setData(path, this.mapper.writeValueAsBytes(up), up.getStat().getVersion());
						//t.setData(path, this.mapper.writeValueAsBytes(up), -1);//temporary.
						log.debug("Inserting REMOVE_K|V on Transaction, GLOBAL, Path:{}, Update:{}", path, up);
					}

					r.incRemoveCounter();
					break;
				case REMOVEALL:
					log.error("\n\nMethod:{} not implemented, PersistentDomain.java. Exiting. {}", up.getOp());
					System.exit(1);
					break;
				/**
				 * It is necessary to do a zk.setData instead zk.create because if the data does
				 * not exist at dataStore it will be created.
				 * 
				 */
				case ADDALL:
				case PUTALL:
				case SET:
					log.error("\n\nMethod:{} not implemented, PersistentDomain.java. Exiting.", up.getOp());
					System.exit(1);
					break;

				case ADD:
					if (up.getScope().equals(Scope.GLOBAL)) {
						path = globalData + "/" + up.getDataStructure() + "/" + up.getSecondLevel() + "/" + up.getKey();
						t.setData(path, mapper.writeValueAsBytes(up), -1);

						log.debug("Inserting setData on Transaction, GLOBAL data, Version:{}, Path:{}, Update:{}",
								new Object[] { up.getStat().getVersion(), path, up });
					} else {
						path = localData + "/" + tarInstance.getControllerId() + "/" + up.getDataStructure() + "/"
								+ up.getSecondLevel() + "/" + up.getKey();
						t.setData(path, mapper.writeValueAsBytes(up), -1);
						log.debug("Inserting setData on Transaction, LOCAL data, Version:-1, Path:{}", path);
					}
					r.incWriteCounter();
					break;
				case PUT:
				case PUTABSENT:
				case REPLACE_KV:
				case REPLACE_KVV:
					if (up.getScope().equals(Scope.GLOBAL)) {
						path = globalData + "/" + up.getDataStructure() + "/" + up.getKey();
						t.setData(path, mapper.writeValueAsBytes(up), up.getStat().getVersion());
					} else {
						path = localData + "/" + tarInstance.getControllerId() + "/" + up.getDataStructure() + "/"
								+ up.getKey();
						t.setData(path, mapper.writeValueAsBytes(up), -1);
					}
					
					/*log.debug("#t.set(...), GLOBAL, Op:" + up.getOp() 
							+ ", Key: "+up.getKey()
							+ ", Value: "+up.getV1()
							+ ", Version: "+up.getStat().getVersion()
							+ ", ThreadId: "+up.getThreadId());*/

								
					if (log.isTraceEnabled()) {
						byteArray = mapper.writeValueAsBytes(up);
						totalBytes += byteArray.length;
						//logger.debug("Operation: {}, total of bytes:{}", up.getOp(), byteArray.length);
					}

					r.incWriteCounter();
					break;
				default:
					log.info("\n\n" + "Type not recognized: {" + up.getOp() + "}, " + "Key:{" + up.getKey() + "}, "
							+ "V1: {" + up.getV1() + "}, " + "V2: {" + up.getV2() + "}, " + "}, " + "Scope:{"
							+ up.getScope() + "}");
					log.error("\n\n\tOperation:{} not implemented, PersistentDomain.java. Exiting.", up.getOp());
					// System.exit(1);
					break;
				}
			} catch (NullPointerException npe) {
				log.info("NPE: Update: {}", up);
				npe.printStackTrace();
			} catch (JsonProcessingException jpe) {
				jpe.printStackTrace();
			}
		}

		//logger.info("{}\n",sb);
		
		if ((r.getWriteCounter() == 0) && (r.getRemoveCounter() == 0) && (r.getClearCounter() == 0)) {
			log.trace("There are not updates, returning DatastoreStatus.READ_ONLY, ThreadId:{}", threadId);
			r.setDatastoreStatus(DatastoreStatus.READ_ONLY);
			return r;
		}

		ArrayList<OpResult> listResult = new ArrayList<>();
		try {
			listResult.addAll(t.commit());
			
			log.debug("Transaction Committed, total size, Bytes:" + totalBytes + ", ThreadId:" + threadId
					+ ", Update operation(s):" + listResult.size());

			r.setDatastoreStatus(DatastoreStatus.WRITE_OK);
			return r;

		} catch (KeeperException ke) {

			if (ke.code().equals(Code.NONODE)) {
				log.info("Node does not exist, I'll create: {}", path);
				
				TreeMap<Long, Update>  noNodeQueue = new TreeMap<>();

				while (!auxQueue.isEmpty()) {
					Update up = auxQueue.poll();
					if (up.getOp().equals(Operation.REMOVE_K) || up.getOp().equals(Operation.REMOVE_KV)
							|| up.getOp().equals(Operation.REMOVEALL)) {
						log.info("Excluding {} operation from createNoNodeException.", up.getOp());
					} else {
						noNodeQueue.put(up.getTimeStamp(), up);
						if (up.getScope().equals(Scope.LOCAL)) {
							path = localData + "/" + tarInstance.getControllerId() + "/" + up.getDataStructure();
							createNoNodeException(up, path);
							createNoNodeException(up, path + "/" + up.getKey());
						}
						if (up.getScope().equals(Scope.GLOBAL)) {
							if (up.isSecondLevel()) {
								path = globalData + "/" + up.getDataStructure() + "/" + up.getSecondLevel();
								createNoNodeException(up, path);
								createNoNodeException(up, path + "/" + up.getKey());
							} else {
								path = globalData + "/" + up.getDataStructure();
								createNoNodeException(up, path);
								createNoNodeException(up, path + "/" + up.getKey());
							}

						}
					}

				}
				if (ke.code().equals(Code.NODEEXISTS)) {
					log.debug("Node EXIST: {}", path);
					StringBuffer sb2 = new StringBuffer();
					sb2.append("\nLIST OF UPDATES NOT SAVED :: BAD_VERSION.");
					Iterator<Long> it3 = treeUpdates.keySet().iterator();
					while (it3.hasNext()) {
						Long timeStamp = (Long) it3.next();
						sb2.append("\n\t# " + treeUpdates.get(timeStamp).toString());			
					}
					log.info("{}\n",sb2);
				}
				// returns just different from remove operations.
				return storeData(noNodeQueue);

			} else if (ke.code().equals(Code.BADVERSION)) {
				log.trace("BAD VERSION, data not updated.");
				r.setDatastoreStatus(DatastoreStatus.BAD_VERSION);
				return r;
			} else if (ke.code().equals(Code.NODEEXISTS)) {
				r.setDatastoreStatus(DatastoreStatus.GENERIC_ERROR);
				return r;
			} else {
				ke.printStackTrace();
				r.setDatastoreStatus(DatastoreStatus.GENERIC_ERROR);
				return r;
			}

		} catch (InterruptedException ie) {
			ie.printStackTrace();
			r.setDatastoreStatus(DatastoreStatus.GENERIC_ERROR);
			return r;
		}
	}
	
	protected void createNoNodeException(Update data, String path) {
		try {
			log.debug("Creating because Node Exception Arised: {}, Data:{}.", path, data.toString());
			zk.create(path, this.mapper.writeValueAsBytes(data), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		} catch (KeeperException ke) {
			if (ke.code().equals(Code.NODEEXISTS))
				log.debug("Node {} exist???, no problem.", path);
		} catch (IllegalArgumentException iae) {
			iae.printStackTrace();
		} catch (JsonProcessingException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public boolean updateLatencyConsolidateData(HashMap<Integer, HashMap<Long, Long>> consolidatedData) {
		try {
			zk.setData(latencyConsolidate, this.mapper.writeValueAsBytes(consolidatedData), -1);
			log.trace("Latency consolidated updated: {}", consolidatedData.toString());
			leaveGlobalWatcher(masterLeader);
			return true;
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException ie) {
			ie.printStackTrace();
			return false;
		} catch (KeeperException ke) {
			ke.printStackTrace();
			return false;
		}

	}

	protected void storeLatencyIndividualData(HashMap<Long, Long> latencyData, String ctrlId) {
		try {
			zk.create(latencyIndividual + "/" + ctrlId, this.mapper.writeValueAsBytes(latencyData), Ids.OPEN_ACL_UNSAFE,
					CreateMode.EPHEMERAL);

			log.trace("Latency data saved: {}", latencyData.toString());
		} catch (NodeExistsException ne) {
			updateLatencyData(latencyData, ctrlId);
		} catch (InterruptedException | KeeperException | JsonProcessingException ie) {
			ie.printStackTrace();
		}
		leaveGlobalWatcher(masterLeader);
	}

	protected boolean updateLatencyData(HashMap<Long, Long> latencyData, String CtrlId) {
		try {
			zk.setData(latencyIndividual + "/" + CtrlId, this.mapper.writeValueAsBytes(latencyData), -1);
			log.trace("Latency data updated: {}", latencyData.toString());
			leaveGlobalWatcher(masterLeader);
			return true;
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException ie) {
			ie.printStackTrace();
			return false;
		} catch (KeeperException ke) {
			ke.printStackTrace();
			return false;
		}

	}
	
	protected void leaveGlobalWatcher(boolean masterLeader) {
		try {
			if(masterLeader){
				zk.getChildren(latencyIndividual, zkGM, null);
				log.trace("{} : watcher for Latency Individual", latencyIndividual);
			}else{
				zk.getData(latencyConsolidate, zkGM, null);
				log.trace("{} : watcher for Latency Consolidated", latencyConsolidate);
			}
		} catch (KeeperException | InterruptedException ke) {
			log.debug("Problem with leaving watcher Latency Individual/Consolidated");
			ke.printStackTrace();
		}
	}

	protected void setMeActive(String ctrlId) {
		try {
			zk.create(activeCtrl + "/" + ctrlId, "I'm active and full of happyness".getBytes(), Ids.OPEN_ACL_UNSAFE,
					CreateMode.EPHEMERAL);
			log.debug("Controller {} Activated and Monitoring for crashes {}", ctrlId, activeCtrl);
			zk.getChildren(activeCtrl, zkGM, null);
		} catch (NodeExistsException ne) {
			log.error("CtrlID: {} already activated, EXITING, see controllerId at properties file!",
					activeCtrl + "/" + ctrlId);
			System.exit(1);
		} catch (InterruptedException | KeeperException e) {
		}
	}

	@Override
	public void process(WatchedEvent event) {}

	public List<Update> getInitialRecover(String dataStructure) {
		
		List<Update> toReturn = new LinkedList<>();
		try {
			List<String> keysToRecover = zk.getChildren(globalData + "/"+ dataStructure, false);			
			/*logger.info("To Initial Recover: DataStructure: {}, Keys:{}", 
					globalData + "/"+ dataStructure,
					keysToRecover);*/
			
			Iterator<String> itKeys = keysToRecover.iterator();
			while (itKeys.hasNext()) {
				Stat stat = new Stat();
				String key = (String) itKeys.next();				
				String path = globalData + "/"+ dataStructure +"/"+key;				
				byte[] dataToRecover = zk.getData(path, false, stat);
				Update update = this.mapper.readValue(dataToRecover, new TypeReference<Update>() {});
				update.setStat(stat);
				toReturn.add(update);
			}
			
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return toReturn;
	}
	
	public Update getDataDS(Update update) {
		
		//long threadId = Thread.currentThread().getId();
		String path = globalData + "/" + update.getDataStructure() + "/" + update.getKey();
		byte[] dataToRecover = null;
		
		Stat stat = new Stat();
		try {
			dataToRecover = zk.getData(path, false, stat);
			update = this.mapper.readValue(dataToRecover, new TypeReference<Update>() {});
			update.setStat(stat);
			//logger.info("INSIDE GET DATA: {}", update.toString());
			return update;
		} catch (KeeperException ke) {
			if (ke.code().equals(Code.NONODE)) {
				log.debug("Update: {} NoNode Exception, creating and returning.", update);
				
				path = globalData + "/" + update.getDataStructure();
				createNoNodeException(update, path);
				createNoNodeException(update, path + "/" + update.getKey());
				
				return update;
			} else {
				ke.printStackTrace();
				throw new IllegalStateException(ke);
			}
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
	}
	
	public boolean verifyUpdate(Update update) {
		String path;
		Transaction t = zk.transaction();
		if (update.getScope().equals(Scope.GLOBAL)) {
			path = globalData + "/" + update.getDataStructure() + "/" + update.getKey();
			
			t.check(path, update.getStat().getVersion());
			
		} else {
			path = localData + "/" + tarInstance.getControllerId() + "/" + update.getDataStructure() + "/" + update.getKey();
			t.check(path, update.getStat().getVersion());
		}
		try {
			List<OpResult> listOpResult = t.commit();
			return true;
		} catch (InterruptedException | KeeperException e) {
			//e.printStackTrace();
			return false;
		}
		 
	}
	
	
	
}
