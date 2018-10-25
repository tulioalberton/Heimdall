/**
 *    Copyright 2011, Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.linkdiscovery.internal;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortState;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IInfoProvider;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.heimdall.ITarService;
import net.floodlightcontroller.heimdall.ITarService.DatastoreStatus;
import net.floodlightcontroller.heimdall.PersistentDomain;
import net.floodlightcontroller.heimdall.ResultDS;
import net.floodlightcontroller.heimdall.Tar;
import net.floodlightcontroller.heimdall.TracePacketIn;
import net.floodlightcontroller.heimdall.tracing.Update;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.SwitchType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.web.LinkDiscoveryWebRoutable;
import net.floodlightcontroller.packet.BSN;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OFMessageUtils;

/**
 * This class sends out LLDP messages containing the sending switch's datapath
 * id as well as the outgoing port number. Received LLrescDP messages that match
 * a known switch cause a new LinkTuple to be created according to the invariant
 * rules listed below. This new LinkTuple is also passed to routing if it exists
 * to trigger updates. This class also handles removing links that are
 * associated to switch ports that go down, and switches that are disconnected.
 * Invariants: -portLinks and switchLinks will not contain empty Sets outside of
 * critical sections -portLinks contains LinkTuples where one of the src or dst
 * SwitchPortTuple matches the map key -switchLinks contains LinkTuples where
 * one of the src or dst SwitchPortTuple's id matches the switch id -Each
 * LinkTuple will be indexed into switchLinks for both src.id and dst.id, and
 * portLinks for each src and dst -The updates queue is only added to from
 * within a held write lock
 * 
 * @edited Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 */
public class LinkDiscoveryManager implements IOFMessageListener,
IOFSwitchListener, IStorageSourceListener, ILinkDiscoveryService,
IFloodlightModule, IInfoProvider {
	protected static final Logger log = LoggerFactory.getLogger(LinkDiscoveryManager.class);

	
	public static final String MODULE_NAME = "linkdiscovery";

	// Names of table/fields for links in the storage API
	private static final String TOPOLOGY_TABLE_NAME = "controller_topologyconfig";
	private static final String TOPOLOGY_ID = "id";
	private static final String TOPOLOGY_AUTOPORTFAST = "autoportfast";

	private static final String LINK_TABLE_NAME = "controller_link";
	private static final String LINK_ID = "id";
	private static final String LINK_SRC_SWITCH = "src_switch_id";
	private static final String LINK_SRC_PORT = "src_port";
	private static final String LINK_DST_SWITCH = "dst_switch_id";
	private static final String LINK_DST_PORT = "dst_port";
	private static final String LINK_VALID_TIME = "valid_time";
	private static final String LINK_TYPE = "link_type";
	private static final String SWITCH_CONFIG_TABLE_NAME = "controller_switchconfig";

	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IStorageSourceService storageSourceService;
	protected IThreadPoolService threadPoolService;
	protected IRestApiService restApiService;
	protected IDebugCounterService debugCounterService;
	protected IShutdownService shutdownService;

	// Role
	protected HARole role;

	// LLDP and BDDP fields
	//private static final byte[] LLDP_STANDARD_DST_MAC_STRING = MacAddress.of("01:08:c2:00:00:0e").getBytes();
	private static final byte[] LLDP_STANDARD_DST_MAC_STRING = MacAddress.of("01:80:c2:00:00:0e").getBytes();
	//http://openvswitch.org/support/dist-docs/ovs-vswitchd.conf.db.5.html
	//https://mail.openvswitch.org/pipermail/ovs-discuss/2013-May/029986.html
	
	private static final long LINK_LOCAL_MASK = 0xfffffffffff0L;
	private static final long LINK_LOCAL_VALUE = 0x0180c2000000L;
	//private static final long LINK_LOCAL_VALUE = 0x0108c2000000L;
	protected static int EVENT_HISTORY_SIZE = 1024; // in seconds

	// BigSwitch OUI is 5C:16:C7, so 5D:16:C7 is the multicast version
	private static final String LLDP_BSN_DST_MAC_STRING = "5d:16:c7:00:00:01";
	//private static final String LLDP_BSN_DST_MAC_STRING = "ff:ff:ff:ff:ff:ff";

	// Direction TLVs are used to indicate if the LLDPs were sent
	// periodically or in response to a recieved LLDP
	private static final byte TLV_DIRECTION_TYPE = 0x0e;
	private static final short TLV_DIRECTION_LENGTH = 1; // 1 byte
	private static final byte TLV_DIRECTION_VALUE_FORWARD[] = { 0x01 };
	private static final byte TLV_DIRECTION_VALUE_REVERSE[] = { 0x02 };
	private static final LLDPTLV forwardTLV = new LLDPTLV().setType(TLV_DIRECTION_TYPE)
			.setLength(TLV_DIRECTION_LENGTH)
			.setValue(TLV_DIRECTION_VALUE_FORWARD);

	private static final LLDPTLV reverseTLV = new LLDPTLV().setType(TLV_DIRECTION_TYPE)
			.setLength(TLV_DIRECTION_LENGTH)
			.setValue(TLV_DIRECTION_VALUE_REVERSE);

	// Link discovery task details.
	protected SingletonTask discoveryTask;
	//protected final int DISCOVERY_TASK_INTERVAL = 8;
	protected final int LINK_TIMEOUT = 90; // timeout as part of LLDP process.
	protected final int LLDP_TO_ALL_INTERVAL = 2000; // 15 seconds.
	protected long lldpClock = 0;
	// This value is intentionally kept higher than LLDP_TO_ALL_INTERVAL.
	// If we want to identify link failures faster, we could decrease this
	// value to a small number, say 1 or 2 sec.
	//protected final int LLDP_TO_KNOWN_INTERVAL = 20; // LLDP frequency for known
	// links
	
	protected LLDPTLV controllerTLV;
	protected LLDPTLV epochTLV;
	protected ReentrantReadWriteLock lock;
	long lldpEpoch = 0;
	private HashMap <String, Long> lldpLastForwarded;
	
	protected SingletonTask summaryTopology;
	private PersistentDomain persistence;
	protected SingletonTask lldpDiscoverTask;
	
	
	//protected ITarService tarService;
	//private MapTracing<String, Long> dataWrite;
	

	/*
	 * Latency tracking
	 */
	protected static int LATENCY_HISTORY_SIZE = 10;
	protected static double LATENCY_UPDATE_THRESHOLD = 0.50;

	/**
	 * Flag to indicate if automatic port fast is enabled or not. Default is set
	 * to false -- Initialized in the init method as well.
	 */
	protected boolean AUTOPORTFAST_DEFAULT = false;
	protected boolean autoPortFastFeature = AUTOPORTFAST_DEFAULT;

	/**
	 * Map from link to the most recent time it was verified functioning
	 */
	protected Map<Link, LinkInfo> links;

	/**
	 * Map from switch id to a set of all links with it as an endpoint
	 */
	protected Map<DatapathId, HashSet<Link>> switchLinks;
	

	/**
	 * Map from a id:port to the set of links containing it as an endpoint
	 */
	protected Map<NodePortTuple, HashSet<Link>> portLinks;

	protected volatile boolean shuttingDown = false;

	/*
	 * topology aware components are called in the order they were added to the
	 * the array
	 */
	protected ArrayList<ILinkDiscoveryListener> linkDiscoveryAware;
	
	protected HashMap <Long, BlockingQueue<LDUpdate>> updates;

	/**
	 * List of ports through which LLDP/BDDPs are not sent.
	 */
	protected Set<NodePortTuple> suppressLinkDiscovery;

	/**
	 * A list of ports that are quarantined for discovering links through them.
	 * Data traffic from these ports are not allowed until the ports are
	 * released from quarantine.
	 */
	protected LinkedBlockingQueue<NodePortTuple> quarantineQueue;
	protected LinkedBlockingQueue<NodePortTuple> maintenanceQueue;
	protected LinkedBlockingQueue<NodePortTuple> toRemoveFromQuarantineQueue;
	protected LinkedBlockingQueue<NodePortTuple> toRemoveFromMaintenanceQueue;

	/**
	 * Quarantine task
	 */
	protected SingletonTask bddpTask;
	protected final int BDDP_TASK_INTERVAL = 250; // 100 ms.
	protected final int BDDP_TASK_SIZE = 10; // # of ports per iteration

	private class MACRange {
		MacAddress baseMAC;
		int ignoreBits;
	}
	protected Set<MACRange> ignoreMACSet;

	private IHAListener haListener;

	/**
	 * Debug Counters
	 */
	private IDebugCounter ctrQuarantineDrops;
	private IDebugCounter ctrIgnoreSrcMacDrops;
	private IDebugCounter ctrIncoming;
	private IDebugCounter ctrLinkLocalDrops;
	private IDebugCounter ctrLldpEol;

	private final String PACKAGE = LinkDiscoveryManager.class.getPackage().getName();


	
	/**
	 * Heimdall utilizes specific functions to serializer and deserializer.
	 *  
	 */
	/*Function<HashMap<Link, LinkInfo>, String> serHM_K_Link_V_LinkInfo = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	Function<String, HashMap<Link, LinkInfo>> desHM_K_Link_V_LinkInfo = (String str) -> {
		try {
			return new ObjectMapper().readValue(str, HashMap.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};*/
	
	
	static Function<Link, String> serializer_KEY_Link = (info) -> {
		return info.toKeyString();
	};
	
	static Function<String, Link> deserializer_KEY_Link = (String str) -> {
		
		//System.out.println("deserializing_KEY_Link: " + str);
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
	
	};
	
	Function<LinkInfo, String> serializer_VALUE_LinkInfo = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	

	Function<String, LinkInfo> deserializer_VALUE_LinkInfo = (String str) -> {
		if(str==null) {
			return new LinkInfo(new Date(),new Date(),new Date());
		}
		try {
			return new ObjectMapper().readValue(str, LinkInfo.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	/*
	Function<HashSetTracing<Link>, String> serHST_Link = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	
	Function<String, HashSetTracing<Link>> desHST_Link = (String str) -> {
		try {
			return new ObjectMapper().readValue(str, HashSetTracing.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	Function<HashSet<Link>, String> serHS_Link = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	
	Function<String, HashSet<Link>> desHS_Link = (String str) -> {
		if(str==null) {
			return new HashSet<>();
		}
		try {
			return new ObjectMapper().readValue(str, HashSet.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	Function<String, NodePortTuple> des_NodePortTuple = (String str) -> {
		if(str==null) {
			return new NodePortTuple(DatapathId.NONE, OFPort.ZERO);
		}
		try {
			return new ObjectMapper().readValue(str, NodePortTuple.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	Function<String, DatapathId> des_DatapathId = (String str) -> {
		if(str==null) {
			return DatapathId.NONE;
		}
		try {
			return new ObjectMapper().readValue(str, DatapathId.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	Function<String, Link> des_Link = (String str) -> {
		if(str==null) {
			return new Link();
		}
		try {
			return new ObjectMapper().readValue(str, Link.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	Function<MapTracing<DatapathId, HashSetTracing<Link>>, String> serMT_HST_Link = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	
	Function<HashMap<DatapathId, HashSetTracing<Link>>, String> serHM_HST_Link = (info) -> {
		try {
			return new ObjectMapper().writeValueAsString(info);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	};
	
	Function<String, MapTracing<DatapathId, HashSetTracing<Link>>> desMT_HST_Link = (String str) -> {
		try {
			return new ObjectMapper().readValue(str, MapTracing.class);
		} catch (java.io.IOException e) {
			throw new UncheckedIOException(e);
		}
	};
	
	*/
	
	
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
	
	//*********************
	// ILinkDiscoveryService
	//*********************

	@Override
	public OFPacketOut generateLLDPMessage(IOFSwitch iofSwitch, OFPort port, 
			boolean isStandard, boolean isReverse) {

		OFPortDesc ofpPort = iofSwitch.getPort(port);

		if (log.isTraceEnabled()) {
			log.trace("Sending LLDP packet out of swich: {}, port: {}, reverse: {}",
				new Object[] {iofSwitch.getId().toString(), port.toString(), Boolean.toString(isReverse)});
		}

		// using "nearest customer bridge" MAC address for broadest possible
		// propagation
		// through provider and TPMR bridges (see IEEE 802.1AB-2009 and
		// 802.1Q-2011),
		// in particular the Linux bridge which behaves mostly like a provider
		// bridge
		byte[] chassisId = new byte[] { 4, 0, 0, 0, 0, 0, 0 }; // filled in later
		byte[] portId = new byte[] { 2, 0, 0 }; // filled in later
		byte[] ttlValue = new byte[] { 0, 0x78 };
		// OpenFlow OUI - 00-26-E1-00
		byte[] dpidTLVValue = new byte[] { 0x0, 0x26, (byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 0x0b)
				.setLength((short) dpidTLVValue.length)
				.setValue(dpidTLVValue);

		byte[] dpidArray = new byte[8];
		ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);
		ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);

		DatapathId dpid = iofSwitch.getId();
		dpidBB.putLong(dpid.getLong());
		// set the chassis id's value to last 6 bytes of dpid
		System.arraycopy(dpidArray, 2, chassisId, 1, 6);
		// set the optional tlv to the full dpid
		System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);

		// TODO: Consider remove this block of code.
		// It's evil to overwrite port object. The the old code always
		// overwrote mac address, we now only overwrite zero macs and
		// log a warning, mostly for paranoia.
		byte[] srcMac = ofpPort.getHwAddr().getBytes();
		byte[] zeroMac = { 0, 0, 0, 0, 0, 0 };
		if (Arrays.equals(srcMac, zeroMac)) {
			log.warn("Port {}/{} has zero hardware address"
					+ "overwrite with lower 6 bytes of dpid",
					dpid.toString(), ofpPort.getPortNo().getPortNumber());
			System.arraycopy(dpidArray, 2, srcMac, 0, 6);
		}

		// set the portId to the outgoing port
		portBB.putShort(port.getShortPortNumber());

		LLDP lldp = new LLDP();
		lldp.setChassisId(new LLDPTLV().setType((byte) 1)
				.setLength((short) chassisId.length)
				.setValue(chassisId));
		lldp.setPortId(new LLDPTLV().setType((byte) 2)
				.setLength((short) portId.length)
				.setValue(portId));
		lldp.setTtl(new LLDPTLV().setType((byte) 3)
				.setLength((short) ttlValue.length)
				.setValue(ttlValue));
		
		lldp.getOptionalTLVList().add(dpidTLV);
		
		// Add the controller identifier to the TLV value.
		lldp.getOptionalTLVList().add(controllerTLV);
		
		//Add the epoch of lldp. 
		lldp.getOptionalTLVList().add(epochTLV);
		
		if (isReverse) {
			lldp.getOptionalTLVList().add(reverseTLV);
		} else {
			lldp.getOptionalTLVList().add(forwardTLV);
		}	
		/* 
		 * Introduce a new TLV for med-granularity link latency detection.
		 * If same controller, can assume system clock is the same, but
		 * cannot guarantee processing time or account for network congestion.
		 * 
		 * Need to include our OpenFlow OUI - 00-26-E1-01 (note 01; 00 is DPID); 
		 * save last 8 bytes for long (time in ms). 
		 * 
		 * Note Long.SIZE is in bits (64).
		 */
		long time = System.currentTimeMillis();
		long swLatency = iofSwitch.getLatency().getValue();
		if (log.isTraceEnabled()) {
			log.trace("SETTING LLDP LATENCY TLV: Current Time {}; {} control plane latency {}; sum {}", new Object[] { time, iofSwitch.getId(), swLatency, time + swLatency });
		}
		byte[] timestampTLVValue = ByteBuffer.allocate(Long.SIZE / 8 + 4)
				.put((byte) 0x00)
				.put((byte) 0x26)
				.put((byte) 0xe1)
				.put((byte) 0x01) /* 0x01 is what we'll use to differentiate DPID (0x00) from time (0x01) */
				.putLong(time + swLatency /* account for our switch's one-way latency */)
				.array();

		LLDPTLV timestampTLV = new LLDPTLV()
		.setType((byte) 0x0f)
		.setLength((short) timestampTLVValue.length)
		.setValue(timestampTLVValue);

		/* Now add TLV to our LLDP packet */
		lldp.getOptionalTLVList().add(timestampTLV);

		Ethernet ethernet;
		if (isStandard) {
			ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
					.setDestinationMACAddress(LLDP_STANDARD_DST_MAC_STRING)
					.setEtherType(EthType.LLDP);
			ethernet.setPayload(lldp);
		} else {
			BSN bsn = new BSN(BSN.BSN_TYPE_BDDP);
			bsn.setPayload(lldp);

			ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
					.setDestinationMACAddress(LLDP_BSN_DST_MAC_STRING)
					.setEtherType(EthType.of(Ethernet.TYPE_BSN & 0xffff)); /* treat as unsigned */
			ethernet.setPayload(bsn);
		}

		// serialize and wrap in a packet out
		byte[] data = ethernet.serialize();
		OFPacketOut.Builder pob = iofSwitch.getOFFactory().buildPacketOut()
		.setBufferId(OFBufferId.NO_BUFFER)
		.setActions(getDiscoveryActions(iofSwitch, port))
		.setData(data);
		OFMessageUtils.setInPort(pob, OFPort.CONTROLLER);

		//log.debug("{}", pob.build());
		log.debug("Sending LLDP switch: {}, port: {}, isStandard: {}, isReverse: {}",
				new Object[] {iofSwitch.getId().toString(), port.toString(), isStandard, isReverse});
		return pob.build();
	}

	/**
	 * Get the LLDP sending period in seconds.
	 *
	 * @return LLDP sending period in seconds.
	 */
	/*public int getLldpFrequency() {
		return LLDP_TO_KNOWN_INTERVAL;
	}*/

	/**
	 * Get the LLDP timeout value in seconds
	 *
	 * @return LLDP timeout value in seconds
	 */
	public int getLldpTimeout() {
		return LINK_TIMEOUT;
	}

	@Override
	public Map<NodePortTuple, HashSet<Link>> getPortLinks() {
		return portLinks;
	}

	@Override
	public Set<NodePortTuple> getSuppressLLDPsInfo() {
		return suppressLinkDiscovery;
	}

	/**
	 * Add a switch port to the suppressed LLDP list. Remove any known links on
	 * the switch port.
	 */
	@Override
	public void AddToSuppressLLDPs(DatapathId sw, OFPort port) {
		NodePortTuple npt = new NodePortTuple(sw, port);
		this.suppressLinkDiscovery.add(npt);
		deleteLinksOnPort(npt, "LLDP suppressed.");
	}

	/**
	 * Remove a switch port from the suppressed LLDP list. Discover links on
	 * that switchport.
	 */
	@Override
	public void RemoveFromSuppressLLDPs(DatapathId sw, OFPort port) {
		NodePortTuple npt = new NodePortTuple(sw, port);
		this.suppressLinkDiscovery.remove(npt);
		discover(npt);
	}

	public boolean isShuttingDown() {
		return shuttingDown;
	}

	@Override
	public boolean isTunnelPort(DatapathId sw, OFPort port) {
		return false;
	}

	@Override
	public ILinkDiscovery.LinkType getLinkType(Link lt, LinkInfo info) {
		try {
			if (info.getUnicastValidTime() != null) {
				return ILinkDiscovery.LinkType.DIRECT_LINK;
			} else if (info.getMulticastValidTime() != null) {
				return ILinkDiscovery.LinkType.MULTIHOP_LINK;
			}
		} catch (NullPointerException npe) {
			return ILinkDiscovery.LinkType.INVALID_LINK;	
		}
		
		return ILinkDiscovery.LinkType.INVALID_LINK;
	}

	@Override
	public Set<OFPort> getQuarantinedPorts(DatapathId sw) {
		Set<OFPort> qPorts = new HashSet<OFPort>();

		Iterator<NodePortTuple> iter = quarantineQueue.iterator();
		while (iter.hasNext()) {
			NodePortTuple npt = iter.next();
			if (npt.getNodeId().equals(sw)) {
				qPorts.add(npt.getPortId());
			}
		}
		return qPorts;
	}

	@Override
	public Map<DatapathId, HashSet<Link>> getSwitchLinks() {
		return this.switchLinks;
	}

	@Override
	public void addMACToIgnoreList(MacAddress mac, int ignoreBits) {
		MACRange range = new MACRange();
		range.baseMAC = mac;
		range.ignoreBits = ignoreBits;
		ignoreMACSet.add(range);
	}

	@Override
	public boolean isAutoPortFastFeature() {
		return autoPortFastFeature;
	}

	@Override
	public void setAutoPortFastFeature(boolean autoPortFastFeature) {
		this.autoPortFastFeature = autoPortFastFeature;
	}

	@Override
	public void addListener(ILinkDiscoveryListener listener) {
		linkDiscoveryAware.add(listener);
	}

	@Override
	public Map<Link, LinkInfo> getLinks() {
		lock.readLock().lock();
		Map<Link, LinkInfo> result;
		try {
			result = new HashMap<Link, LinkInfo>(links);
		} finally {
			lock.readLock().unlock();
		}
		return result;
	}

	@Override
	public LinkInfo getLinkInfo(Link link) {
		lock.readLock().lock();
		LinkInfo linkInfo = links.get(link);
		LinkInfo retLinkInfo = null;
		if (linkInfo != null) {
			retLinkInfo = new LinkInfo(linkInfo);
		}
		lock.readLock().unlock();
		return retLinkInfo;
	}

	@Override
	public String getName() {
		return MODULE_NAME;
	}

	//*********************
	//   OFMessage Listener
	//*********************

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			ctrIncoming.increment();
			return this.handlePacketIn(sw.getId(), (OFPacketIn) msg, cntx);
		default:
			break;
		}
		return Command.CONTINUE;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	//***********************************
	//  Internal Methods - Packet-in Processing Related
	//***********************************

	protected Command handlePacketIn(DatapathId sw, OFPacketIn pi,
			FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		
		//log.info("Handling packetIn: eth.getEtherType() {}",eth.getEtherType());
		if (eth.getPayload() instanceof BSN) {			
			BSN bsn = (BSN) eth.getPayload();
			if (bsn == null) return Command.STOP;
			if (bsn.getPayload() == null) return Command.STOP;
			// It could be a packet other than BSN LLDP, therefore
			// continue with the regular processing.
			if (bsn.getPayload() instanceof LLDP == false)
				return Command.CONTINUE;
			log.trace("Received BSN packetIn.");
			return handleLldp((LLDP) bsn.getPayload(), sw, inPort, false, cntx);
		} else if (eth.getPayload() instanceof LLDP) {
			log.trace("Received LLDP packetIn.");
			return handleLldp((LLDP) eth.getPayload(), sw, inPort, true, cntx);
		} else if (eth.getEtherType().getValue() < 1536 && eth.getEtherType().getValue() >= 17) {
			log.trace("Received ETH packetIn.");
			long destMac = eth.getDestinationMACAddress().getLong();
			if ((destMac & LINK_LOCAL_MASK) == LINK_LOCAL_VALUE) {
				ctrLinkLocalDrops.increment();
				//if (log.isTraceEnabled()) {
					log.info("Ignoring packet addressed to 802.1D/Q, reserved address.");
				//}
				return Command.STOP;
			}
		} else if (eth.getEtherType().getValue() < 17) {
			log.error("Received invalid ethertype of {}.", eth.getEtherType());
			return Command.STOP;
		}

		if (ignorePacketInFromSource(eth.getSourceMACAddress())) {
			log.info("ignorePacketInFromSource packetIn, SourceMAC-{}.", eth.getSourceMACAddress() );
			ctrIgnoreSrcMacDrops.increment();
			return Command.STOP;
		}

		// If packet-in is from a quarantine port, stop processing.
		/*NodePortTuple npt = new NodePortTuple(sw, inPort);
		if (quarantineQueue.contains(npt)) {
			ctrQuarantineDrops.increment();
			log.info("ignorePacketIn, NodePortTuple: {}.", npt);
			return Command.STOP;
		}*/

		return Command.CONTINUE;
	}

	private boolean ignorePacketInFromSource(MacAddress srcMAC) {
		Iterator<MACRange> it = ignoreMACSet.iterator();
		while (it.hasNext()) {
			MACRange range = it.next();
			long mask = ~0;
			if (range.ignoreBits >= 0 && range.ignoreBits <= 48) {
				mask = mask << range.ignoreBits;
				if ((range.baseMAC.getLong() & mask) == (srcMAC.getLong() & mask)) {
					return true;
				}
			}
		}
		return false;
	}

	private Command handleLldp(LLDP lldp, DatapathId receivedSwitch, OFPort receivedPort,
			boolean isStandard, FloodlightContext cntx) {
		// If LLDP is suppressed on this port, ignore received packet as well
		IOFSwitch iofSwitch = switchService.getSwitch(receivedSwitch);

		//log.debug("Received LLDP packet on sw {}, port {}", sw, inPort);

		if (!isIncomingDiscoveryAllowed(receivedSwitch, receivedPort, isStandard)){
			log.info("\nSTOPPING PIPELINE... NOT ALLOWED Incoming Discovery.");
			return Command.STOP;
		}
		// If this is a malformed LLDP exit
		if (lldp.getPortId() == null || lldp.getPortId().getLength() != 3) {
			log.info("\nSTOPPING PIPELINE... lldp.getPortId() == null || lldp.getPortId().getLength() != 3");
			return Command.STOP;
		}

		long myCtrlId = ByteBuffer.wrap(controllerTLV.getValue()).getLong();
		long remoteCtrlId = 0;
		String lldpControl = "";
		boolean isMyLLDP = false;
		Boolean isReverse = null;
		
		DatapathId remoteDpid = null;// DatapathId.of(ByteBuffer.wrap(lldp.getChassisId().getValue()).getLong(4));
		ByteBuffer portBB = ByteBuffer.wrap(lldp.getPortId().getValue());
		portBB.position(1);
		OFPort remotePort = OFPort.of(portBB.getShort());		
		IOFSwitch iofRemoteSwitch = null;
		
		long timestamp = 0;

		// Verify this LLDP packet matches what we're looking for
		for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
			if (lldptlv.getType() == 11 && lldptlv.getLength() == 12
					&& lldptlv.getValue()[0] == 0x0
					&& lldptlv.getValue()[1] == 0x26
					&& lldptlv.getValue()[2] == (byte) 0xe1
					&& lldptlv.getValue()[3] == 0x0) {
				ByteBuffer dpidBB = ByteBuffer.wrap(lldptlv.getValue());
				remoteDpid = DatapathId.of(dpidBB.getLong(4));				
			} 
			else if (lldptlv.getType() == 12 && lldptlv.getLength() == 8) {
				remoteCtrlId = ByteBuffer.wrap(lldptlv.getValue()).getLong();
				if (myCtrlId == remoteCtrlId) 
					isMyLLDP = true;
				else
					lldpControl = "ctrl-"+remoteCtrlId+"=rDpid-"+remoteDpid.getLong()+":rPort-"+remotePort.getPortNumber()
									+":swId-"+receivedSwitch.getLong()+":inPort-"+receivedPort.getPortNumber();
				
			}
			else if (lldptlv.getType() == 13 && lldptlv.getLength() == 8) {
				lldpEpoch = ByteBuffer.wrap(lldptlv.getValue()).getLong();				
			}
			else if (lldptlv.getType() == TLV_DIRECTION_TYPE && lldptlv.getLength() == TLV_DIRECTION_LENGTH) {
				if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_FORWARD[0])
					isReverse = false;
				else if (lldptlv.getValue()[0] == TLV_DIRECTION_VALUE_REVERSE[0])
					isReverse = true;
			}
			else if (lldptlv.getType() == 15 && lldptlv.getLength() == 12
					&& lldptlv.getValue()[0] == 0x0
					&& lldptlv.getValue()[1] == 0x26
					&& lldptlv.getValue()[2] == (byte) 0xe1
					&& lldptlv.getValue()[3] == 0x01) { /* 0x01 for timestamp */
				ByteBuffer tsBB = ByteBuffer.wrap(lldptlv.getValue()); /* skip OpenFlow OUI (4 bytes above) */
				long swLatency = iofSwitch.getLatency().getValue();
				timestamp = tsBB.getLong(4); /* include the RX switch latency to "subtract" it */
				if (log.isTraceEnabled()) {
					log.trace("RECEIVED LLDP LATENCY TLV: Got timestamp of {}; Switch {} latency of {}", new Object[] { timestamp, iofSwitch.getId(), iofSwitch.getLatency().getValue() }); 
				}
				timestamp = timestamp + swLatency;
			}
		}

		log.trace("\n\tRemote Dpid: {}, Received Dpid:{}"
				+ "\n\tRemote Port: {}, Received Port:{}"
				+ "\n\tController Id: {}", 
				new Object[]{remoteDpid.getLong(), 
						receivedSwitch.getLong(), 
						remotePort, 
						receivedPort, 
						remoteCtrlId});
		
		//log.info("LLDP: {}", lldp.toString());
		
		if (isMyLLDP == false) {
			//lldpControl += ":standard-"+isStandard+":reverse-"+isReverse;
			// This is not the LLDP sent by this controller.
			// If the LLDP message has multicast bit set, then we need to
			// broadcast the packet as a regular packet (after checking IDs)
			
			log.debug("Got LLDP from Id:{}, Control: {}, Epoch:{}.", new Object[]{remoteCtrlId, lldpControl, lldpEpoch});
			
			if (lldpLastForwarded.containsKey(lldpControl)) {
				if (lldpEpoch > lldpLastForwarded.get(lldpControl)) {
					lldpLastForwarded.put(lldpControl, lldpEpoch);
					log.debug("Forwarding LLDP, From: {}, Epoch:{}", lldpControl, lldpEpoch);
					
					// serialize and wrap in a packet out
					byte[] data = lldp.serialize();
					OFPacketOut.Builder pob = iofSwitch.getOFFactory().buildPacketOut();
					List<OFAction> actions = new ArrayList<OFAction>();
				    actions.add(iofSwitch.getOFFactory().actions().output(OFPort.IN_PORT, 0));
				    //actions.add(iofSwitch.getOFFactory().actions().output(OFPort.ALL, 0));
				    pob.setActions(actions);
				    pob.setBufferId(OFBufferId.NO_BUFFER);
				    //pob.setInPort(receivedPort);
				    pob.setInPort(OFPort.ZERO);
				    pob.setData(data);
					switchService.getActiveSwitch(receivedSwitch).write(pob.build(), false);
					return Command.STOP;	
				} else {
					log.debug("NOT Forwarding, already sent LLDP, From: {}, Epoch:{}", lldpControl, lldpEpoch);
					return Command.STOP;
				}
			}else {
				lldpLastForwarded.put(lldpControl, lldpEpoch);
				log.info("Forwarding first LLDP, From: {}, Epoch:{}", lldpControl, lldpEpoch);		
				// serialize and wrap in a packet out
				byte[] data = lldp.serialize();
				OFPacketOut.Builder pob = iofSwitch.getOFFactory().buildPacketOut();
				List<OFAction> actions = new ArrayList<OFAction>();
			    actions.add(iofSwitch.getOFFactory().actions().output(OFPort.IN_PORT, 0));
			    pob.setActions(actions);
			    pob.setBufferId(OFBufferId.NO_BUFFER);
			    //pob.setInPort(receivedPort);
			    pob.setInPort(OFPort.ZERO);
			    pob.setData(data);
			    switchService.getActiveSwitch(receivedSwitch).write(pob.build(),false);
				return Command.STOP;
			}			
		}

		
		lldpControl = "ctrl-"+remoteCtrlId+"=rDpid-"+remoteDpid.getLong()+":rPort-"+remotePort
			+":swId-"+receivedSwitch.getLong()+":inPort-"+receivedPort.getPortNumber();
		
		log.debug("Got my LLDP Id:{}, Control: {}, Epoch:{}.", new Object[]{remoteCtrlId, lldpControl, lldpEpoch});
		
		iofRemoteSwitch = switchService.getSwitch(remoteDpid);
		
		
		if (iofRemoteSwitch == null) {
			// Ignore LLDPs not generated by Floodlight, or from a switch that
			// has recently
			// disconnected, or from a switch connected to another Floodlight
			// instance
			//if (log.isTraceEnabled()) {
				log.info("Received LLDP from remote switch not connected to this controller");
			//}
			return Command.STOP;
		}

		if (!iofRemoteSwitch.portEnabled(remotePort)) {
			//if (log.isTraceEnabled()) {
				log.info("Ignoring link with disabled source port: switch {} port {} {}",
						new Object[] { iofRemoteSwitch.getId().toString(),
						remotePort,
						iofRemoteSwitch.getPort(remotePort)});
			//}
			return Command.STOP;
		}
		
		if (suppressLinkDiscovery.contains(new NodePortTuple(
				iofRemoteSwitch.getId(),
				remotePort))) {
			//if (log.isTraceEnabled()) {
				log.info("Ignoring link with suppressed src port: switch {} port {} {}",
						new Object[] { iofRemoteSwitch.getId().toString(),
						remotePort,
						iofRemoteSwitch.getPort(remotePort)});
			//}
			return Command.STOP;
		}
		if (!iofSwitch.portEnabled(receivedPort)) {
			//if (log.isTraceEnabled()) {
				log.info("Ignoring link with disabled dest port: switch {} port {} {}",
						new Object[] { receivedSwitch.toString(),
								receivedPort.getPortNumber(),
						iofSwitch.getPort(receivedPort).getPortNo().getPortNumber()});
			//}
			return Command.STOP;
		}

		// Store the time of update to this link, and push it out to
		// routingEngine
		long time = System.currentTimeMillis();
		U64 latency = (timestamp != 0 && (time - timestamp) > 0) ? U64.of(time - timestamp) : U64.ZERO;
		if (log.isTraceEnabled()) {
			log.trace("COMPUTING FINAL DATAPLANE LATENCY: Current time {}; Dataplane+{} latency {}; Overall latency from {} to {} is {}", 
					new Object[] { time, iofSwitch.getId(), timestamp, remoteDpid.getLong(), 
							iofSwitch.getId(), String.valueOf(latency.getValue()) });
		}
		Link lt = new Link(iofRemoteSwitch.getId(), remotePort, iofSwitch.getId(), receivedPort, latency);

		if (!isLinkAllowed(lt.getSrc(), lt.getSrcPort(),
				lt.getDst(), lt.getDstPort()))
			return Command.STOP;

		// Continue only if link is allowed.
		Date lastLldpTime = null;
		Date lastBddpTime = null;

		Date firstSeenTime = new Date(System.currentTimeMillis());

		if (isStandard) {
			lastLldpTime = new Date(firstSeenTime.getTime());
		} else {
			lastBddpTime = new Date(firstSeenTime.getTime());
		}

		LinkInfo newLinkInfo = new LinkInfo(firstSeenTime, lastLldpTime, lastBddpTime);

		addOrUpdateLink(lt, newLinkInfo);

		// Check if reverse link exists.
		// If it doesn't exist and if the forward link was seen
		// first seen within a small interval, send probe on the
		// reverse link.
		newLinkInfo = links.get(lt);
		if (newLinkInfo != null && isStandard && isReverse == false) {
			Link reverseLink = new Link(lt.getDst(), lt.getDstPort(), lt.getSrc(), lt.getSrcPort(), U64.ZERO); 
			/* latency not used; not important what the value is, since it's intentionally not in equals() */
			
			LinkInfo reverseInfo = links.get(reverseLink);
			if (reverseInfo == null) {
				// the reverse link does not exist.
				if (newLinkInfo.getFirstSeenTime().getTime() > System.currentTimeMillis() - LINK_TIMEOUT) {
					log.debug("Sending reverse LLDP for link {}", lt);
					this.sendDiscoveryMessage(lt.getDst(), lt.getDstPort(), isStandard, true);
				}
			}
		}

		// If the received packet is a BDDP packet, then create a reverse BDDP
		// link as well.
		if (!isStandard) {
			Link reverseLink = new Link(lt.getDst(), lt.getDstPort(), lt.getSrc(), lt.getSrcPort(), latency);

			// srcPortState and dstPort state are reversed.
			LinkInfo reverseInfo = new LinkInfo(firstSeenTime, lastLldpTime, lastBddpTime);

			addOrUpdateLink(reverseLink, reverseInfo);
		}

		// Queue removal of the node ports from the quarantine and maintenance queues.
		NodePortTuple nptSrc = new NodePortTuple(lt.getSrc(),lt.getSrcPort());
		NodePortTuple nptDst = new NodePortTuple(lt.getDst(),lt.getDstPort());

		flagToRemoveFromQuarantineQueue(nptSrc);
		flagToRemoveFromMaintenanceQueue(nptSrc);
		flagToRemoveFromQuarantineQueue(nptDst);
		flagToRemoveFromMaintenanceQueue(nptDst);
		
		
		long threadId = Thread.currentThread().getId();
		try {
			doLLDPUpdates(threadId);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// Consume this message
		ctrLldpEol.increment();
		return Command.STOP;
	}

	//***********************************
	//  Internal Methods - Port Status/ New Port Processing Related
	//***********************************
	/**
	 * Process a new port. If link discovery is disabled on the port, then do
	 * nothing. If autoportfast feature is enabled and the port is a fast port,
	 * then do nothing. Otherwise, send LLDP message. Add the port to
	 * quarantine.
	 *
	 * @param sw
	 * @param p
	 */
	private void processNewPort(DatapathId sw, OFPort p) {
		if (isLinkDiscoverySuppressed(sw, p)) {
			// Do nothing as link discovery is suppressed.
			return;
		}

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) {
			return;
		}
		
		NodePortTuple npt = new NodePortTuple(sw, p);
		discover(sw, p);
		addToQuarantineQueue(npt);
	}

	//***********************************
	//  Internal Methods - Discovery Related
	//***********************************

	protected boolean isLinkDiscoverySuppressed(DatapathId sw, OFPort portNumber) {
		return this.suppressLinkDiscovery.contains(new NodePortTuple(sw,
				portNumber));
	}

	protected void discoverLinks() {

		// timeout known links.
		timeoutLinks();

		// increment LLDP clock
		/*lldpClock = (lldpClock + 1) % LLDP_TO_ALL_INTERVAL;

		if (lldpClock == 0) {
			if (log.isTraceEnabled())
				log.trace("Sending LLDP out on all ports.");
			discoverOnAllPorts();
		}*/
	}

	/**
	 * Quarantine Ports.
	 */
	protected class QuarantineWorker implements Runnable {
		@Override
		public void run() {
			try {
				processBDDPLists();
			} catch (Exception e) {
				log.error("Error in quarantine worker thread", e);
			} finally {
				bddpTask.reschedule(BDDP_TASK_INTERVAL, TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Add a switch port to the quarantine queue. Schedule the quarantine task
	 * if the quarantine queue was empty before adding this switch port.
	 *
	 * @param npt
	 */
	protected void addToQuarantineQueue(NodePortTuple npt) {
		if (quarantineQueue.contains(npt) == false) {
			quarantineQueue.add(npt);
		}
	}

	/**
	 * Remove a switch port from the quarantine queue.
	 *
	protected void removeFromQuarantineQueue(NodePortTuple npt) {
		// Remove all occurrences of the node port tuple from the list.
		while (quarantineQueue.remove(npt));
	}*/
	protected void flagToRemoveFromQuarantineQueue(NodePortTuple npt) {
		if (toRemoveFromQuarantineQueue.contains(npt) == false) {
			toRemoveFromQuarantineQueue.add(npt);
		}
	}

	/**
	 * Add a switch port to maintenance queue.
	 *
	 * @param npt
	 */
	protected void addToMaintenanceQueue(NodePortTuple npt) {
		if (maintenanceQueue.contains(npt) == false) {
			maintenanceQueue.add(npt);
		}
	}

	/**
	 * Remove a switch port from maintenance queue.
	 *
	 * @param npt
	 *
	protected void removeFromMaintenanceQueue(NodePortTuple npt) {
		// Remove all occurrences of the node port tuple from the queue.
		while (maintenanceQueue.remove(npt));
	} */
	protected void flagToRemoveFromMaintenanceQueue(NodePortTuple npt) {
		if (toRemoveFromMaintenanceQueue.contains(npt) == false) {
			toRemoveFromMaintenanceQueue.add(npt);
		}
	}

	/**
	 * This method processes the quarantine list in bursts. The task is at most
	 * once per BDDP_TASK_INTERVAL. One each call, BDDP_TASK_SIZE number of
	 * switch ports are processed. Once the BDDP packets are sent out through
	 * the switch ports, the ports are removed from the quarantine list.
	 */
	protected void processBDDPLists() {
		int count = 0;
		Set<NodePortTuple> nptList = new HashSet<NodePortTuple>();
		
		while (count < BDDP_TASK_SIZE && quarantineQueue.peek() != null) {
			NodePortTuple npt;
			npt = quarantineQueue.remove();
			/*
			 * Do not send a discovery message if we already have received one
			 * from another switch on this same port. In other words, if
			 * handleLldp() determines there is a new link between two ports of
			 * two switches, then there is no need to re-discover the link again.
			 * 
			 * By flagging the item in handleLldp() and waiting to remove it 
			 * from the queue when processBDDPLists() runs, we can guarantee a 
			 * PORT_STATUS update is generated and dispatched below by
			 * generateSwitchPortStatusUpdate().
			 */
			if (!toRemoveFromQuarantineQueue.remove(npt)) {
				sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false, false);
			}
			/*
			 * Still add the item to the list though, so that the PORT_STATUS update
			 * is generated below at the end of this function.
			 */
			nptList.add(npt);
			count++;
		}

		count = 0;
		while (count < BDDP_TASK_SIZE && maintenanceQueue.peek() != null) {
			NodePortTuple npt;
			npt = maintenanceQueue.remove();
			/*
			 * Same as above, except we don't care about the PORT_STATUS message; 
			 * we only want to avoid sending the discovery message again.
			 */
			if (!toRemoveFromMaintenanceQueue.remove(npt)) {
				sendDiscoveryMessage(npt.getNodeId(), npt.getPortId(), false, false);
			}
			count++;
		}

		for (NodePortTuple npt : nptList) {
			generateSwitchPortStatusUpdate(npt.getNodeId(), npt.getPortId());
		}
	}

	private void generateSwitchPortStatusUpdate(DatapathId sw, OFPort port) {
		UpdateOperation operation;
		long threadId  = Thread.currentThread().getId();
		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) return;

		OFPortDesc ofp = iofSwitch.getPort(port);
		if (ofp == null) return;

		Set<OFPortState> srcPortState = ofp.getState();
		boolean portUp = !srcPortState.contains(OFPortState.STP_BLOCK);

		if (portUp) {
			operation = UpdateOperation.PORT_UP;
		} else {
			operation = UpdateOperation.PORT_DOWN;
		}

		if(updates.containsKey(threadId))
			updates.get(threadId).add(new LDUpdate(sw, port, operation));
		else{
			updates.put(threadId, new LinkedBlockingQueue<>());
			updates.get(threadId).add(new LDUpdate(sw, port, operation));
		}
		informListeners(threadId);
	}

	protected void discover(NodePortTuple npt) {
		discover(npt.getNodeId(), npt.getPortId());
	}

	protected void discover(DatapathId sw, OFPort port) {
		sendDiscoveryMessage(sw, port, true, false);
	}

	/**
	 * Check if incoming discovery messages are enabled or not.
	 * @param sw
	 * @param port
	 * @param isStandard
	 * @return
	 */
	protected boolean isIncomingDiscoveryAllowed(DatapathId sw, OFPort port,
			boolean isStandard) {

		if (isLinkDiscoverySuppressed(sw, port)) {
			/* Do not process LLDPs from this port as suppressLLDP is set */
			return false;
		}

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) {
			return false;
		}

		if (port == OFPort.LOCAL) return false;

		OFPortDesc ofpPort = iofSwitch.getPort(port);
		if (ofpPort == null) {
			if (log.isTraceEnabled()) {
				log.trace("Null physical port. sw={}, port={}",
						sw.toString(), port.getPortNumber());
			}
			return false;
		}

		return true;
	}

	/**
	 * Check if outgoing discovery messages are enabled or not.
	 * @param sw
	 * @param port
	 * @param isStandard
	 * @param isReverse
	 * @return
	 */
	protected boolean isOutgoingDiscoveryAllowed(DatapathId sw, OFPort port,
			boolean isStandard,
			boolean isReverse) {

		if (isLinkDiscoverySuppressed(sw, port)) {
			/* Dont send LLDPs out of this port as suppressLLDP is set */
			return false;
		}

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) {
			return false;
		} else if (iofSwitch.getControllerRole() == OFControllerRole.ROLE_SLAVE) {
			return false;
		}

		if (port == OFPort.LOCAL) return false;

		OFPortDesc ofpPort = iofSwitch.getPort(port);
		if (ofpPort == null) {
			if (log.isTraceEnabled()) {
				log.trace("Null physical port. sw={}, port={}",
						sw.toString(), port.getPortNumber());
			}
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Get the actions for packet-out corresponding to a specific port.
	 * This is a placeholder for adding actions if any port-specific
	 * actions are desired.  The default action is simply to output to
	 * the given port.
	 * @param port
	 * @return
	 */
	protected List<OFAction> getDiscoveryActions(IOFSwitch sw, OFPort port) {
		// set actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().buildOutput().setPort(port).build());
		return actions;
	}

	/**
	 * Send link discovery message out of a given switch port. The discovery
	 * message may be a standard LLDP or a modified LLDP, where the dst mac
	 * address is set to :ff. TODO: The modified LLDP will updated in the future
	 * and may use a different eth-type.
	 *
	 * @param sw
	 * @param port
	 * @param isStandard
	 *            indicates standard or modified LLDP
	 * @param isReverse
	 *            indicates whether the LLDP was sent as a response
	 */
	protected boolean sendDiscoveryMessage(DatapathId sw, OFPort port,
			boolean isStandard, boolean isReverse) {
		
		// Takes care of all checks including null pointer checks.
		if (!isOutgoingDiscoveryAllowed(sw, port, isStandard, isReverse)) {
			return false;
		}

		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		if (iofSwitch == null) { // fix dereference violations in case race conditions
			return false;
		}
		setEpochTLV();
		return iofSwitch.write(generateLLDPMessage(iofSwitch, port, isStandard, isReverse));
	}

	/**
	 * Send LLDPs to all switch-ports
	 */
	protected void discoverOnAllPorts() {
	/*	log.info("Sending LLDP packets out of all the enabled ports");
		// Send standard LLDPs
		setEpochTLV();
		for (DatapathId sw : switchService.getAllSwitchDpids()) {
			IOFSwitch iofSwitch = switchService.getSwitch(sw);
			if (iofSwitch == null) continue;
			if (!iofSwitch.isActive()) continue;  //can't do anything if the switch is SLAVE 
			Collection<OFPort> c = iofSwitch.getEnabledPortNumbers();
			if (c != null) {
				for (OFPort ofp : c) {
					if (isLinkDiscoverySuppressed(sw, ofp)) {			
						continue;
					}
					log.trace("Enabled port: {}", ofp);
					sendDiscoveryMessage(sw, ofp, true, false);

					// If the switch port is not already in the maintenance
					// queue, add it.
					//NodePortTuple npt = new NodePortTuple(sw, ofp);
					//addToMaintenanceQueue(npt);
				}
			}
		}*/
	}
	/**
	 * Send LLDPs to all switch-ports
	 */
	protected class discoverOnAllPorts2 implements Runnable {
		@Override
		public void run() {
			try{
				log.debug("Sending LLDP packets out of all the enabled ports");
				// Send standard LLDPs
				setEpochTLV();
				for (DatapathId sw : switchService.getAllSwitchDpids()) {
					IOFSwitch iofSwitch = switchService.getSwitch(sw);
					if (iofSwitch == null){
						log.debug("Sw: {}, iofSwitch == null...", sw);
						continue;
						}
					if (!iofSwitch.isActive()){
						log.debug("Sw: {}, NOT ACTIVE, not sending LLDP", sw);
						continue; // can't do anything if the switch is SLAVE 
					}
					Collection<OFPort> c = iofSwitch.getEnabledPortNumbers();
					if (c != null) {
						for (OFPort ofp : c) {
							if (isLinkDiscoverySuppressed(sw, ofp)) {
								log.info("Link Discovery Suppressed Sw: {}, port: {}", sw, ofp);	
								continue;
							}
							log.trace("Enabled port: {}", ofp);
							sendDiscoveryMessage(sw, ofp, true, false);

							// If the switch port is not already in the maintenance
							// queue, add it.
							/*NodePortTuple npt = new NodePortTuple(sw, ofp);
							addToMaintenanceQueue(npt);*/
						}
					}
				}
			}catch(NullPointerException npe){
				npe.printStackTrace();
			}
			finally {
				lldpDiscoverTask.reschedule(LLDP_TO_ALL_INTERVAL, TimeUnit.SECONDS);
				log.debug("Link discovery task, rescheduled...");
				timeoutLinks();
			}
			
			
		}
	}

	
	protected UpdateOperation getUpdateOperation(OFPortState srcPortState, OFPortState dstPortState) {
		boolean added = ((srcPortState != OFPortState.STP_BLOCK) && (dstPortState != OFPortState.STP_BLOCK));

		if (added) {
			return UpdateOperation.LINK_UPDATED;
		} else {
			return UpdateOperation.LINK_REMOVED;
		}
	}

	protected UpdateOperation getUpdateOperation(OFPortState srcPortState) {
		boolean portUp = (srcPortState != OFPortState.STP_BLOCK);

		if (portUp) {
			return UpdateOperation.PORT_UP;
		} else {
			return UpdateOperation.PORT_DOWN;
		}
	}

	//************************************
	// Internal Methods - Link Operations Related
	//************************************

	/**
	 * This method is used to specifically ignore/consider specific links.
	 */
	protected boolean isLinkAllowed(DatapathId src, OFPort srcPort,
			DatapathId dst, OFPort dstPort) {
		return true;
	}

	/*public void incKeyTracing(String key) {
		if (dataWrite.get(key) != null) {
			//log.info("Counting for LD, Key: {}",key);
			dataWrite.put(key, dataWrite.get(key) + 1); 
		} else {
			dataWrite.put(key, 1L);
		}	
	}*/
	
	@SuppressWarnings("unchecked")
	private boolean addLink(Link lt, LinkInfo newInfo) {
		NodePortTuple srcNpt, dstNpt;

		srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
		dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());

		/** After the implementation of Dynamic Batch, broken this part.
		 * Needs to fix this. 
		 */
		
		// index it by switch source	
		if(switchLinks.get(lt.getSrc()) == null){
			switchLinks.put(lt.getSrc(), new HashSet<Link>());
			//incKeyTracing(lt.getSrc().toString());
		}
		switchLinks.get(lt.getSrc()).add(lt);
		//incKeyTracing(lt.getSrc()+":"+lt.toKeyString());

		// index it by switch dest		
		if(switchLinks.get(lt.getDst()) == null){
			switchLinks.put(lt.getDst(),new HashSet<Link>());
			//incKeyTracing(lt.getDst().toString());
		}
		switchLinks.get(lt.getDst()).add(lt);
		//incKeyTracing(lt.getDst()+":"+lt.toKeyString());

		// index both ends by switch:port
		if (portLinks.get(srcNpt) == null){
			portLinks.put(srcNpt,new HashSet<Link>());
			//incKeyTracing(srcNpt.toKeyString());
		}
		portLinks.get(srcNpt).add(lt);
		//incKeyTracing(srcNpt.toKeyString()+":"+lt.toKeyString());

		if (portLinks.get(dstNpt) == null){
			portLinks.put(dstNpt,new HashSet<Link>());
			//incKeyTracing(dstNpt.toKeyString());
		}	
		portLinks.get(dstNpt).add(lt);
		//incKeyTracing(dstNpt.toKeyString()+":"+lt.toKeyString());
		
		
		newInfo.addObservedLatency(lt.getLatency());

		return true;
	}

	/**
	 * Determine if a link should be updated and set the time stamps if it should.
	 * Also, determine the correct latency value for the link. An existing link
	 * will have a list of latencies associated with its LinkInfo. If enough time has
	 * elapsed to determine a good latency baseline average and the new average is
	 * greater or less than the existing latency value by a set threshold, then the
	 * latency should be updated. This allows for latencies to be smoothed and reduces
	 * the number of link updates due to small fluctuations (or outliers) in instantaneous
	 * link latency values.
	 * 
	 * @param lt with observed latency. Will be replaced with latency to use.
	 * @param existingInfo with past observed latencies and time stamps
	 * @param newInfo with updated time stamps
	 * @return true if update occurred; false if no update should be dispatched
	 */
	protected boolean updateLink(@Nonnull Link lk, @Nonnull LinkInfo existingInfo, @Nonnull LinkInfo newInfo) {
		boolean linkChanged = false;
		boolean ignoreBDDP_haveLLDPalready = false;
		
		/*
		 * Check if we are transitioning from one link type to another.
		 * A transition is:
		 * -- going from no LLDP time to an LLDP time (is OpenFlow link)
		 * -- going from an LLDP time to a BDDP time (is non-OpenFlow link)
		 * 
		 * Note: Going from LLDP to BDDP means our LLDP link must have timed
		 * out already (null in existing LinkInfo). Otherwise, we'll flap
		 * between mulitcast and unicast links.
		 */
		if (existingInfo.getMulticastValidTime() == null && newInfo.getMulticastValidTime() != null) {
			if (existingInfo.getUnicastValidTime() == null) { /* unicast must be null to go to multicast */
				log.debug("Link is BDDP. Changed.");
				linkChanged = true; /* detected BDDP */
			} else {
				ignoreBDDP_haveLLDPalready = true;
				log.debug("Ignoring BBDP, Have LLDP Already.");
			}
		} else if (existingInfo.getUnicastValidTime() == null && newInfo.getUnicastValidTime() != null) {
			log.debug("Link is LLDP. Changed.");
			linkChanged = true; /* detected LLDP */
		}

		
		log.debug("At UpdateLink: NewINfo: {}", newInfo);
		/* 
		 * If we're undergoing an LLDP update (non-null time), grab the new LLDP time.
		 * If we're undergoing a BDDP update (non-null time), grab the new BDDP time.
		 * 
		 * Only do this if the new LinkInfo is non-null for each respective field.
		 * We want to overwrite an existing LLDP/BDDP time stamp with null if it's
		 * still valid.
		 */
		if (newInfo.getUnicastValidTime() != null) {
			existingInfo.setUnicastValidTime(newInfo.getUnicastValidTime());
			log.debug("existingInfo.setUnicastValidTime({})",newInfo.getUnicastValidTime() );
		} else if (newInfo.getMulticastValidTime() != null) {
			existingInfo.setMulticastValidTime(newInfo.getMulticastValidTime());
			log.debug("existingInfo.setMulticastValidTime({})",newInfo.getMulticastValidTime());
		}	

		/*
		 * Update Link latency if we've accumulated enough latency data points
		 * and if the average exceeds +/- the current stored latency by the
		 * defined update threshold.
		 */
		U64 currentLatency = existingInfo.getCurrentLatency();
		U64 latencyToUse = existingInfo.addObservedLatency(lk.getLatency());

		if (currentLatency == null) {
			/* no-op; already 'changed' as this is a new link */
		} else if (!latencyToUse.equals(currentLatency) && !ignoreBDDP_haveLLDPalready) {
			log.debug("Updating link {} latency to {}ms", lk.toKeyString(), latencyToUse.getValue());
			lk.setLatency(latencyToUse);
			linkChanged = true;
		} else {
			log.trace("No need to update link latency {}", lk.toString());
		}

		return linkChanged;
	}

	protected boolean addOrUpdateLink(Link lt, LinkInfo newInfo) {
		boolean linkChanged = false;
		long threadId  = Thread.currentThread().getId();
		lock.writeLock().lock();
		try {
			/*
			 * Put the new info only if new. We want a single LinkInfo
			 * to exist per Link. This will allow us to track latencies
			 * without having to conduct a deep, potentially expensive
			 * copy each time a link is updated.
			 */
			LinkInfo existingInfo = null;
			if (links.get(lt) == null) {
				//lt.setLatency(U64.ZERO);
				links.put(lt, newInfo); /* Only put if doesn't exist or null value */
				//incKeyTracing(lt.toKeyString());
				
			} else {
				existingInfo = links.get(lt);
				//log.info("existingInfo 1: {}", existingInfo);
			}

			/* Update existing LinkInfo with most recent time stamp */
			if (existingInfo != null && existingInfo.getFirstSeenTime().before(newInfo.getFirstSeenTime())) {
				existingInfo.setFirstSeenTime(newInfo.getFirstSeenTime());
				log.debug("existingInfo.setFirstSeenTime(newInfo.getFirstSeenTime()); NewInfo:{}", newInfo);
			}

			//if (log.isTraceEnabled()) {
				log.debug("addOrUpdateLink: {} {}", lt,
						(newInfo.getMulticastValidTime() != null) ? "multicast" : "unicast");
			//}

			UpdateOperation updateOperation = null;
			linkChanged = false;

			if (existingInfo == null) {
				addLink(lt, newInfo);
				updateOperation = UpdateOperation.LINK_UPDATED;
				linkChanged = true;

				// Log direct links only. Multi-hop links may be numerous
				// Add all to event history
				LinkType linkType = getLinkType(lt, newInfo);
				if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
					log.debug("Inter-switch link detected: {}", lt);
				}
			} else {
				linkChanged = updateLink(lt, existingInfo, newInfo);
				if (linkChanged) {
					updateOperation = UpdateOperation.LINK_UPDATED;
					LinkType linkType = getLinkType(lt, newInfo);
					if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
						log.debug("Inter-switch link updated: {}", lt);
					}
				}
				else {
					log.trace("Inter-switch link NOT updated: {}, \nExistingInfo:{}, \nnewInfo:{}", 
							new Object[] {lt, 
							existingInfo,
							newInfo});
				}
			}

			if (linkChanged) {
				// find out if the link was added or removed here.
				LDUpdate up = new LDUpdate(lt.getSrc(), lt.getSrcPort(),
						lt.getDst(), lt.getDstPort(),
						lt.getLatency(),
						getLinkType(lt, newInfo),
						updateOperation);
				
				
				if(updates.containsKey(threadId))
					updates.get(threadId).add(up);
				else{
					updates.put(threadId, new LinkedBlockingQueue<>());
					updates.get(threadId).add(up);
				}
				
				
				/* Update link structure (FIXME shouldn't have to do this, since it should be the same object) */
				Iterator<Entry<Link, LinkInfo>> it = links.entrySet().iterator();
				while (it.hasNext()) {
					Entry<Link, LinkInfo> entry = it.next();
					if (entry.getKey().equals(lt)) {
						entry.getKey().setLatency(lt.getLatency());
						log.trace("Latency Inter-switch link updated: {}", entry.getKey());
						break;
					}
				}
			}
			
			// Write changes to storage. This will always write the updated
			// valid time, plus the port states if they've changed (i.e. if
			// they weren't set to null in the previous block of code.
			//writeLinkToStorage(lt, newInfo);
			
		} finally {
			lock.writeLock().unlock();
		}

		return linkChanged;
	}

	/**
	 * Delete a link
	 *
	 * @param link
	 *            - link to be deleted.
	 * @param reason
	 *            - reason why the link is deleted.
	 */
	protected void deleteLink(Link link, String reason) {
		if (link == null)
			return;
		List<Link> linkList = new ArrayList<Link>();
		linkList.add(link);
		deleteLinks(linkList, reason);
	}
	/**
	 * Removes links from memory and storage.
	 *
	 * @param links
	 *            The List of @LinkTuple to delete.
	 */
	protected void deleteLinks(List<Link> links, String reason) {
		deleteLinks(links, reason, null);
	}

	/**
	 * Removes links from memory and storage.
	 *
	 * @param links
	 *            The List of @LinkTuple to delete.
	 */
	protected void deleteLinks(List<Link> links, String reason, List<LDUpdate> updateList) {

		NodePortTuple srcNpt, dstNpt;
		List<LDUpdate> linkUpdateList = new ArrayList<LDUpdate>();
		lock.writeLock().lock();
		try {
			for (Link lt : links) {
				srcNpt = new NodePortTuple(lt.getSrc(), lt.getSrcPort());
				dstNpt = new NodePortTuple(lt.getDst(), lt.getDstPort());
				
				
				
				
				if (switchLinks.containsKey(lt.getSrc())) {
					if(switchLinks.get(lt.getSrc()).contains(lt))
						switchLinks.get(lt.getSrc()).remove(lt);
					
					if (switchLinks.get(lt.getSrc()).isEmpty())
						this.switchLinks.remove(lt.getSrc());
					
					//incKeyTracing(lt.getSrc().toString());
					
				}
				if (this.switchLinks.containsKey(lt.getDst())) {
					if(switchLinks.get(lt.getSrc()).contains(lt))
						switchLinks.get(lt.getDst()).remove(lt);
					if (this.switchLinks.get(lt.getDst()).isEmpty())
						this.switchLinks.remove(lt.getDst());
					//incKeyTracing(lt.getDst().toString());
				}

				if (this.portLinks.get(srcNpt) != null) {
					if(this.portLinks.get(srcNpt).contains(lt))
						this.portLinks.get(srcNpt).remove(lt);
					if (this.portLinks.get(srcNpt).isEmpty())
						this.portLinks.remove(srcNpt);
					//incKeyTracing(srcNpt.toString());
				}
				if (this.portLinks.get(dstNpt) != null) {
					if(this.portLinks.get(dstNpt).contains(lt))
						this.portLinks.get(dstNpt).remove(lt);
					if (this.portLinks.get(dstNpt).isEmpty())
						this.portLinks.remove(dstNpt);
					//incKeyTracing(dstNpt.toString());
				}

				LinkInfo info = this.links.remove(lt);
				LinkType linkType = getLinkType(lt, info);
				linkUpdateList.add(new LDUpdate(lt.getSrc(),
						lt.getSrcPort(),
						lt.getDst(),
						lt.getDstPort(),
						lt.getLatency(),
						linkType,
						UpdateOperation.LINK_REMOVED));

				// remove link from storage.
				//removeLinkFromStorage(lt);

				// TODO Whenever link is removed, it has to checked if
				// the switchports must be added to quarantine.

				if (linkType == ILinkDiscovery.LinkType.DIRECT_LINK) {
					log.info("Inter-switch link removed: {}", lt);
				} else if (log.isTraceEnabled()) {
					log.trace("Deleted link {}", lt);
				}
			}
		} finally {
			if (updateList != null) linkUpdateList.addAll(updateList);
			long threadId = Thread.currentThread().getId();
			if(updates.containsKey(threadId))
				updates.get(threadId).addAll(linkUpdateList);
			else{
				updates.put(threadId, new LinkedBlockingQueue<>());
				updates.get(threadId).addAll(linkUpdateList);
			}
			lock.writeLock().unlock();
			try {
				doLLDPUpdates(threadId);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Delete links incident on a given switch port.
	 *
	 * @param npt
	 * @param reason
	 */
	protected void deleteLinksOnPort(NodePortTuple npt, String reason) {
		List<Link> eraseList = new ArrayList<Link>();
		if (this.portLinks.containsKey(npt)) {
			if (log.isTraceEnabled()) {
				log.trace("handlePortStatus: Switch {} port #{} "
						+ "removing links {}",
						new Object[] {
								npt.getNodeId().toString(),
								npt.getPortId(),
								this.portLinks.get(npt) });
			}
			eraseList.addAll(this.portLinks.get(npt));
			deleteLinks(eraseList, reason);
		}
	}

	/**
	 * Iterates through the list of links and deletes if the last discovery
	 * message reception time exceeds timeout values.
	 */
	protected void timeoutLinks() {
		List<Link> eraseList = new ArrayList<Link>();
		Long curTime = System.currentTimeMillis();
		boolean unicastTimedOut = false;
		long threadId  = Thread.currentThread().getId();
		/* Reentrant required here because deleteLink also write locks. */
		lock.writeLock().lock();
		try {
			Iterator<Entry<Link, LinkInfo>> it = this.links.entrySet().iterator();
			while (it.hasNext()) {
				Entry<Link, LinkInfo> entry = it.next();
				
				Link lt = entry.getKey();
				LinkInfo info = entry.getValue();

				/* Timeout the unicast and multicast LLDP valid times independently. */
				if ((info.getUnicastValidTime() != null)
						&& (info.getUnicastValidTime().getTime() + (this.LINK_TIMEOUT * 1000) < curTime)) {
					unicastTimedOut = true;
					info.setUnicastValidTime(null);
				}
				if ((info.getMulticastValidTime() != null)
						&& (info.getMulticastValidTime().getTime() + (this.LINK_TIMEOUT * 1000) < curTime)) {
					info.setMulticastValidTime(null);
				}
				/* 
				 * Add to the erase list only if the unicast time is null
				 * and the multicast time is null as well. Otherwise, if
				 * only the unicast time is null and we just set it to 
				 * null (meaning it just timed out), then we transition
				 * from unicast to multicast.
				 */
				if (info.getUnicastValidTime() == null 
						&& info.getMulticastValidTime() == null) {
					eraseList.add(entry.getKey());
					log.info("Link timed out: {}", lt);
				} else if (unicastTimedOut) {
					/* Just moved from unicast to multicast. */
					LDUpdate update = new LDUpdate(lt.getSrc(), lt.getSrcPort(),
							lt.getDst(), lt.getDstPort(), lt.getLatency(),
							getLinkType(lt, info),
							UpdateOperation.LINK_UPDATED);
					
					if(updates.containsKey(threadId))
						updates.get(threadId).add(update);
					else{
						updates.put(threadId, new LinkedBlockingQueue<>());
						updates.get(threadId).add(update);
					}
				}
			}

			if (!eraseList.isEmpty()) {
				deleteLinks(eraseList, "LLDP timeout");
			}
		} finally {
			lock.writeLock().unlock();
			try {
				doLLDPUpdates(threadId);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	//******************
	// Internal Helper Methods
	//******************
	private void setControllerTLV(int controllerId) {
		/**
		 * Setting the controllerTLVValue based on the controllerId.
		 * Heimdall has an unique Id for each controller, this is safely guaranteed by 
		 * the coordination service (DataStore). 
		 */
		byte[] controllerTLVValue = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 }; // 8
		ByteBuffer bb = ByteBuffer.allocate(10);
		bb.putLong((long) controllerId);
		bb.rewind();
		bb.get(controllerTLVValue, 0, 8);		
		this.controllerTLV = new LLDPTLV().setType((byte) 0x0c)
			.setLength((short) controllerTLVValue.length)
			.setValue(controllerTLVValue);
		log.trace("Controller ID TLV: {}", this.controllerTLV.toString());
	}
	
	private void setEpochTLV() {
		lldpEpoch++;
		byte[] epochTLVValue = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 }; // 8
		ByteBuffer bb = ByteBuffer.allocate(10);
		bb.putLong((long) lldpEpoch);
		bb.rewind();
		bb.get(epochTLVValue, 0, 8);
		this.epochTLV = new LLDPTLV().setType((byte) 0x0d)
			.setLength((short) epochTLVValue.length)
			.setValue(epochTLVValue);
		log.trace("Epoch TLV: {}", this.epochTLV.toString());
	}

	//******************
	// IOFSwitchListener
	//******************
	private void handlePortDown(DatapathId switchId, OFPort portNumber) {
		long threadId  = Thread.currentThread().getId();
		NodePortTuple npt = new NodePortTuple(switchId, portNumber);
		deleteLinksOnPort(npt, "Port Status Changed");
		LDUpdate update = new LDUpdate(switchId, portNumber, UpdateOperation.PORT_DOWN);
		
		if(updates.containsKey(threadId))
			updates.get(threadId).add(update);
		else{
			updates.put(threadId, new LinkedBlockingQueue<>());
			updates.get(threadId).add(update);
		}
		
		/*try {
			doUpdatesNotFromPipelinePkIn(threadId);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/
	}
	/**
	 * We don't react the port changed notifications here. we listen for
	 * OFPortStatus messages directly. Might consider using this notifier
	 * instead
	 */
	@Override
	public void switchPortChanged(DatapathId switchId,
			OFPortDesc port,
			PortChangeType type) {

		switch (type) {
		case UP:
			processNewPort(switchId, port.getPortNo());
			break;
		case DELETE: case DOWN:
			handlePortDown(switchId, port.getPortNo());
			break;
		case OTHER_UPDATE: case ADD:
			// This is something other than port add or delete.
			// Topology does not worry about this.
			// If for some reason the port features change, which
			// we may have to react.
			break;
		}
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		// no-op
		// We don't do anything at switch added, but we do only when the
		// switch is activated.
	}

	@Override
	public void switchRemoved(DatapathId sw) {
		List<Link> eraseList = new ArrayList<Link>();
		long threadId  = Thread.currentThread().getId();
		lock.writeLock().lock();
		List<LDUpdate> updateList = new ArrayList<LDUpdate>();
		try {
			if (switchLinks.containsKey(sw)) {
				if (log.isTraceEnabled()) {
					log.trace("Handle switchRemoved. Switch {}; removing links {}", sw.toString(), switchLinks.get(sw));
				}

				// add all tuples with an endpoint on this switch to erase list
				eraseList.addAll(switchLinks.get(sw));

				// Sending the updateList, will ensure the updates in this
				// list will be added at the end of all the link updates.
				// Thus, it is not necessary to explicitly add these updates
				// to the queue.
				deleteLinks(eraseList, "Switch Removed", updateList);
				updateList.add(new LDUpdate(sw, SwitchType.BASIC_SWITCH, UpdateOperation.SWITCH_REMOVED));
			} else {
				// Switch does not have any links.
				updateList.add(new LDUpdate(sw, SwitchType.BASIC_SWITCH, UpdateOperation.SWITCH_REMOVED));				
			}
		} finally {
			lock.writeLock().unlock();
			if(updates.containsKey(threadId))
				updates.get(threadId).addAll(updateList);
			else{
				updates.put(threadId, new LinkedBlockingQueue<>());
				updates.get(threadId).addAll(updateList);
			}
			/*try {
				doUpdatesNotFromPipelinePkIn(threadId);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
		}

	}


	@Override
	public void switchActivated(DatapathId switchId) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		long threadId = Thread.currentThread().getId();
		if (sw == null)       //fix dereference violation in case race conditions
			return;
		
		LDUpdate update = new LDUpdate(sw.getId(), SwitchType.BASIC_SWITCH, UpdateOperation.SWITCH_UPDATED);
		if(updates.containsKey(threadId))
			updates.get(threadId).add(update);
		else{
			updates.put(threadId, new LinkedBlockingQueue<>());
			updates.get(threadId).add(update);
		}
		informListeners(threadId);
		
		if (sw.getEnabledPortNumbers() != null) {
			for (OFPort p : sw.getEnabledPortNumbers()) {
				processNewPort(sw.getId(), p);
			}
		}
		
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// no-op
	}


	//*********************
	//   Storage Listener
	//*********************
	/**
	 * Sets the IStorageSource to use for Topology
	 *
	 * @param storageSource
	 *            the storage source to use
	 */
	public void setStorageSource(IStorageSourceService storageSourceService) {
		this.storageSourceService = storageSourceService;
	}

	/**
	 * Gets the storage source for this ITopology
	 *
	 * @return The IStorageSource ITopology is writing to
	 */
	public IStorageSourceService getStorageSource() {
		return storageSourceService;
	}

	@Override
	public void rowsModified(String tableName, Set<Object> rowKeys) {

		if (tableName.equals(TOPOLOGY_TABLE_NAME)) {
			readTopologyConfigFromStorage();
			return;
		}
	}

	@Override
	public void rowsDeleted(String tableName, Set<Object> rowKeys) {
		// Ignore delete events, the switch delete will do the
		// right thing on it's own.
		readTopologyConfigFromStorage();
	}


	//******************************
	// Internal methods - Config Related
	//******************************

	protected void readTopologyConfigFromStorage() {
		IResultSet topologyResult = storageSourceService.executeQuery(TOPOLOGY_TABLE_NAME,
				null, null,
				null);

		if (topologyResult.next()) {
			boolean apf = topologyResult.getBoolean(TOPOLOGY_AUTOPORTFAST);
			autoPortFastFeature = apf;
		} else {
			this.autoPortFastFeature = AUTOPORTFAST_DEFAULT;
		}

		if (autoPortFastFeature)
			log.debug("Setting autoportfast feature to ON");
		else
			log.debug("Setting autoportfast feature to OFF");
	}

	/**
	 * Deletes all links from storage
	 */
	void clearAllLinks() {
		storageSourceService.deleteRowsAsync(LINK_TABLE_NAME, null);
	}

	/**
	 * Writes a LinkTuple and corresponding LinkInfo to storage
	 *
	 * @param lt
	 *            The LinkTuple to write
	 * @param linkInfo
	 *            The LinkInfo to write
	 */
	protected void writeLinkToStorage(Link lt, LinkInfo linkInfo) {
		LinkType type = getLinkType(lt, linkInfo);

		// Write only direct links. Do not write links to external
		// L2 network.
		// if (type != LinkType.DIRECT_LINK && type != LinkType.TUNNEL) {
		// return;
		// }

		Map<String, Object> rowValues = new HashMap<String, Object>();
		String id = getLinkId(lt);
		rowValues.put(LINK_ID, id);
		rowValues.put(LINK_VALID_TIME, linkInfo.getUnicastValidTime());
		String srcDpid = lt.getSrc().toString();
		rowValues.put(LINK_SRC_SWITCH, srcDpid);
		rowValues.put(LINK_SRC_PORT, lt.getSrcPort());

		if (type == LinkType.DIRECT_LINK)
			rowValues.put(LINK_TYPE, "internal");
		else if (type == LinkType.MULTIHOP_LINK)
			rowValues.put(LINK_TYPE, "external");
		else if (type == LinkType.TUNNEL)
			rowValues.put(LINK_TYPE, "tunnel");
		else
			rowValues.put(LINK_TYPE, "invalid");

		String dstDpid = lt.getDst().toString();
		rowValues.put(LINK_DST_SWITCH, dstDpid);
		rowValues.put(LINK_DST_PORT, lt.getDstPort());

		storageSourceService.updateRowAsync(LINK_TABLE_NAME, rowValues);
	}

	/**
	 * Removes a link from storage using an asynchronous call.
	 *
	 * @param lt
	 *            The LinkTuple to delete.
	 */
	protected void removeLinkFromStorage(Link lt) {
		String id = getLinkId(lt);
		storageSourceService.deleteRowAsync(LINK_TABLE_NAME, id);
	}

	public Long readLinkValidTime(Link lt) {
		// FIXME: We're not currently using this right now, but if we start
		// to use this again, we probably shouldn't use it in its current
		// form, because it's doing synchronous storage calls. Depending
		// on the context this may still be OK, but if it's being called
		// on the packet in processing thread it should be reworked to
		// use asynchronous storage calls.
		Long validTime = null;
		IResultSet resultSet = null;
		try {
			String[] columns = { LINK_VALID_TIME };
			String id = getLinkId(lt);
			resultSet = storageSourceService.executeQuery(LINK_TABLE_NAME,
					columns,
					new OperatorPredicate(
							LINK_ID,
							OperatorPredicate.Operator.EQ,
							id),
							null);
			if (resultSet.next())
				validTime = resultSet.getLong(LINK_VALID_TIME);
		} finally {
			if (resultSet != null) resultSet.close();
		}
		return validTime;
	}

	/**
	 * Gets the storage key for a LinkTuple
	 *
	 * @param lt
	 *            The LinkTuple to get
	 * @return The storage key as a String
	 */
	private String getLinkId(Link lt) {
		return lt.getSrc().toString() + "-" + lt.getSrcPort()
				+ "-" + lt.getDst().toString() + "-"
				+ lt.getDstPort();
	}

	//***************
	// IFloodlightModule
	//***************

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ILinkDiscoveryService.class);
		// l.add(ITopologyService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		// We are the class that implements the service
		m.put(ILinkDiscoveryService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IStorageSourceService.class);
		l.add(IThreadPoolService.class);
		l.add(IRestApiService.class);
		l.add(IShutdownService.class);
		l.add(ITarService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		storageSourceService = context.getServiceImpl(IStorageSourceService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		debugCounterService = context.getServiceImpl(IDebugCounterService.class);
		shutdownService = context.getServiceImpl(IShutdownService.class);
		//tarService = context.getServiceImpl(ITarService.class);
		persistence = PersistentDomain.getInstance();
		// read our config options
		Map<String, String> configOptions = context.getConfigParams(this);
		try {
			String histSize = configOptions.get("event-history-size");
			if (histSize != null) {
				EVENT_HISTORY_SIZE = Short.parseShort(histSize);
			}
		} catch (NumberFormatException e) {
			log.warn("Error event history size. Using default of {} seconds", EVENT_HISTORY_SIZE);
		}
		log.debug("Event history size set to {}", EVENT_HISTORY_SIZE);

		try {
			String latencyHistorySize = configOptions.get("latency-history-size");
			if (latencyHistorySize != null) {
				LATENCY_HISTORY_SIZE = Integer.parseInt(latencyHistorySize);
			}
		} catch (NumberFormatException e) {
			log.warn("Error in latency history size. Using default of {} LLDP intervals", LATENCY_HISTORY_SIZE);
		}
		log.info("Link latency history set to {} LLDP data points", LATENCY_HISTORY_SIZE, LATENCY_HISTORY_SIZE);

		try {
			String latencyUpdateThreshold = configOptions.get("latency-update-threshold");
			if (latencyUpdateThreshold != null) {
				LATENCY_UPDATE_THRESHOLD = Double.parseDouble(latencyUpdateThreshold);
			}
		} catch (NumberFormatException e) {
			log.warn("Error in latency update threshold. Can be from 0 to 1.", LATENCY_UPDATE_THRESHOLD);
		}
		log.info("Latency update threshold set to +/-{} ({}%) of rolling historical average", LATENCY_UPDATE_THRESHOLD, LATENCY_UPDATE_THRESHOLD * 100);

		// Set the autoportfast feature to false.
		this.autoPortFastFeature = AUTOPORTFAST_DEFAULT;

		// We create this here because there is no ordering guarantee
		this.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
		this.lock = new ReentrantReadWriteLock();
		this.updates = new HashMap<>();
		
		
		this.suppressLinkDiscovery = Collections.synchronizedSet(new HashSet<NodePortTuple>());
		
		this.quarantineQueue = new LinkedBlockingQueue<NodePortTuple>();
		this.maintenanceQueue = new LinkedBlockingQueue<NodePortTuple>();
		this.toRemoveFromQuarantineQueue = new LinkedBlockingQueue<NodePortTuple>();
		this.toRemoveFromMaintenanceQueue = new LinkedBlockingQueue<NodePortTuple>();

		this.ignoreMACSet = Collections.newSetFromMap(
				new ConcurrentHashMap<MACRange,Boolean>());
		this.haListener = new HAListenerDelegate();
		this.floodlightProviderService.addHAListener(this.haListener);
		registerLinkDiscoveryDebugCounters();

		this.lldpLastForwarded = new HashMap<>();
		
		/**
		 * Heimdall uses the controller Id coming from .properties file to 
		 * set controller TLV. 
		 * TODO: automatically assign a controllerId based on DataStore.
		 */
		configOptions = context.getConfigParams(FloodlightProvider.class);
		int ctrlId = Integer.parseInt(configOptions.get("controllerId"));
		setControllerTLV(Tar.getInstance().getControllerId());
		
	}

	@SuppressWarnings("unchecked")
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {

		/*dataWrite = tarService.createMapTracing(new HashMap<String, Long>(), "WriteTaxa:LD", Scope.GLOBAL, null,
				serializer_K, deserializer_K, serializer_V, deserializer_V);*/
		
		/**
		 * Second level tracing, shall be LOCAL, otherwise will trigger a bad_version.
		 */
		this.links = new HashMap<Link, LinkInfo>();//original
		this.switchLinks = new HashMap<DatapathId, HashSet<Link>>();//original
		this.portLinks = new HashMap<NodePortTuple, HashSet<Link>>();
				
		// Initialize role to floodlight provider role.
		this.role = floodlightProviderService.getRole();

		// Create our storage tables
		if (storageSourceService == null) {
			log.error("No storage source found.");
			return;
		}

		storageSourceService.createTable(TOPOLOGY_TABLE_NAME, null);
		storageSourceService.setTablePrimaryKeyName(TOPOLOGY_TABLE_NAME,
				TOPOLOGY_ID);
		readTopologyConfigFromStorage();

		storageSourceService.createTable(LINK_TABLE_NAME, null);
		storageSourceService.setTablePrimaryKeyName(LINK_TABLE_NAME, LINK_ID);
		storageSourceService.deleteMatchingRows(LINK_TABLE_NAME, null);
		// Register for storage updates for the switch table
		try {
			storageSourceService.addListener(SWITCH_CONFIG_TABLE_NAME, this);
			storageSourceService.addListener(TOPOLOGY_TABLE_NAME, this);
		} catch (StorageException ex) {
			log.error("Error in installing listener for "
					+ "switch table {}", SWITCH_CONFIG_TABLE_NAME);
		}

		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();

		lldpDiscoverTask = new SingletonTask(ses, new discoverOnAllPorts2());
		lldpDiscoverTask.reschedule(LLDP_TO_ALL_INTERVAL, TimeUnit.SECONDS);
		
		// To be started by the first switch connection
		/*discoveryTask = new SingletonTask(ses, new Runnable() {
			@Override
			public void run() {
				try {
					if (role == null || role == HARole.ACTIVE) { // don't send if we just transitioned to STANDBY 
					    discoverLinks();
					}else{
						log.info("\n\nStopped LLDP rescheduling due to role = {}.", role);
					}
						
				} catch (StorageException e) {
					e.printStackTrace();
					shutdownService.terminate("Storage exception in LLDP send timer. Terminating process " + e, 0);
				} catch (Exception e) {
					log.error("Exception in LLDP send timer.", e);
					e.printStackTrace();
				} finally {
					if (!shuttingDown) {
						// null role implies HA mode is not enabled.
						if (role == null || role == HARole.ACTIVE) {
							log.info("Rescheduling discovery task as role = {}", role);
							discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL, TimeUnit.SECONDS);
						} else {
							log.info("\n\nStopped LLDP rescheduling due to role = {}.", role);
							System.exit(1);
						}
					}
					log.info("Finally... role = {}", role);
				}
			}
		});

		// null role implies HA mode is not enabled.
		if (role == null || role == HARole.ACTIVE) {
			log.info("Setup: Rescheduling discovery task. role = {}", role);
			discoveryTask.reschedule(DISCOVERY_TASK_INTERVAL, TimeUnit.SECONDS);
		} else {
			log.info("\n\nSetup: Not scheduling LLDP as role = {}.", role);
			System.exit(1);
		}*/

		// Setup the BDDP task. It is invoked whenever switch port tuples
		// are added to the quarantine list.
		bddpTask = new SingletonTask(ses, new QuarantineWorker());
		bddpTask.reschedule(BDDP_TASK_INTERVAL, TimeUnit.MILLISECONDS);

		// Register for the OpenFlow messages we want to receive
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProviderService.addOFMessageListener(OFType.PORT_STATUS, this);
		// Register for switch updates
		switchService.addOFSwitchListener(this);
		floodlightProviderService.addHAListener(this.haListener);
		floodlightProviderService.addInfoProvider("summary", this);
		if (restApiService != null)
			restApiService.addRestletRoutable(new LinkDiscoveryWebRoutable());

		ses = threadPoolService.getScheduledExecutor();
		summaryTopology = new SingletonTask(ses, new showDebugInfo());
		summaryTopology.reschedule(10, TimeUnit.SECONDS);
	}
	
	protected class showDebugInfo implements Runnable {
		@Override
		public void run() {
			try{
				log.debug("{}",getAllDebugInfo());
			}catch(NullPointerException npe){				
			}
			finally {
				summaryTopology.reschedule(15, TimeUnit.SECONDS);	
			}
		}
	}

	

	// ****************************************************
	// Link Discovery DebugCounters and DebugEvents
	// ****************************************************

	private void registerLinkDiscoveryDebugCounters() throws FloodlightModuleException {
		if (debugCounterService == null) {
			log.error("Debug Counter Service not found.");
		}
		debugCounterService.registerModule(PACKAGE);
		ctrIncoming = debugCounterService.registerCounter(PACKAGE, "incoming",
				"All incoming packets seen by this module");
		ctrLldpEol  = debugCounterService.registerCounter(PACKAGE, "lldp-eol",
				"End of Life for LLDP packets");
		ctrLinkLocalDrops = debugCounterService.registerCounter(PACKAGE, "linklocal-drops",
				"All link local packets dropped by this module");
		ctrIgnoreSrcMacDrops = debugCounterService.registerCounter(PACKAGE, "ignore-srcmac-drops",
				"All packets whose srcmac is configured to be dropped by this module");
		ctrQuarantineDrops = debugCounterService.registerCounter(PACKAGE, "quarantine-drops",
				"All packets arriving on quarantined ports dropped by this module", IDebugCounterService.MetaData.WARN);
	}

	//*********************
	//  IInfoProvider
	//*********************

	@Override
	public Map<String, Object> getInfo(String type) {
		if (!"summary".equals(type)) return null;

		Map<String, Object> info = new HashMap<String, Object>();

		int numDirectLinks = 0;
		for (Set<Link> links : switchLinks.values()) {
			for (Link link : links) {
				LinkInfo linkInfo = this.getLinkInfo(link);
				if (linkInfo != null &&
						linkInfo.getLinkType() == LinkType.DIRECT_LINK) {
					numDirectLinks++;
				}
			}
		}
		info.put("# inter-switch links", numDirectLinks / 2);
		info.put("# quarantine ports", quarantineQueue.size());
		return info;
	}

	//***************
	// IHAListener
	//***************

	private class HAListenerDelegate implements IHAListener {
		@Override
		public void transitionToActive() {
			log.warn("Sending LLDPs due to HA change from STANDBY->ACTIVE");
			LinkDiscoveryManager.this.role = HARole.ACTIVE;
			clearAllLinks();
			//readTopologyConfigFromStorage();
			log.info("\n\nRole Change to Master: Rescheduling discovery tasks");
			System.exit(1);
			//discoveryTask.reschedule(1, TimeUnit.MICROSECONDS);
		}

		@Override
		public void controllerNodeIPsChanged(Map<String, String> curControllerNodeIPs,
				Map<String, String> addedControllerNodeIPs,
				Map<String, String> removedControllerNodeIPs) {
			// ignore
		}

		@Override
		public String getName() {
			return MODULE_NAME;
		}

		@Override
		public boolean isCallbackOrderingPrereq(HAListenerTypeMarker type,
				String name) {
			return false;
		}

		@Override
		public boolean isCallbackOrderingPostreq(HAListenerTypeMarker type,
				String name) {
			return "tunnelmanager".equals(name);
		}

		@Override
		public void transitionToStandby() {
            log.warn("Disabling LLDPs due to HA change from ACTIVE->STANDBY");
            LinkDiscoveryManager.this.role = HARole.STANDBY;
		}
	}

	@Override
	public void switchDeactivated(DatapathId switchId) {
		switchRemoved(switchId);
	}
	
	public StringBuffer getAllDebugInfo(){
		StringBuffer sb = new StringBuffer();
		
		sb.append("LINK DISCOVERY MANAGER DEBUG:\n\n");
		
		Iterator<Link> it = links.keySet().iterator();
		
		sb.append("LINK: ");
		while (it.hasNext()) {
			sb.append("\n\tLink: "+it.next());
		}

		int numDirectLinks = 0;
		Iterator<DatapathId> itDpid = switchLinks.keySet().iterator();
		sb.append("\n\nSWITCH LINK: ");
		while (itDpid.hasNext()) {
			DatapathId datapathId = (DatapathId) itDpid.next();
			sb.append("\n\tDatapathID: "+datapathId.toString());
			Iterator<Link> itLink = switchLinks.get(datapathId).iterator();
			while (itLink.hasNext()) {
				Link link = (Link) itLink.next();
				sb.append("\n\t\tLink:"+link);
				LinkInfo linkInfo = this.getLinkInfo(link);
				if (linkInfo != null &&
						linkInfo.getLinkType() == LinkType.DIRECT_LINK) {
					numDirectLinks++;
				}
			}
		}
		
		Iterator<NodePortTuple> itNPT = portLinks.keySet().iterator();
		sb.append("\n\nPORT LINKs: ");
		while (itNPT.hasNext()) {
			NodePortTuple nodePortTuple = (NodePortTuple) itNPT.next();
			sb.append("\n\tNodePortTuple: " + nodePortTuple);
			Iterator<Link> itLink = portLinks.get(nodePortTuple).iterator();
			while (itLink.hasNext()) {
				Link link = (Link) itLink.next();
				sb.append("\n\t\tLink: "+link);
			}
		}
		sb.append("\n\n# inter-switch links: " + ( numDirectLinks / 2));
		sb.append("\n# quarantine ports: " +quarantineQueue.size());
		
		return sb;
	}
	
	
	private synchronized void informListeners(long threadId){
		
		if(!updates.containsKey(threadId))
			return;
		
		LinkedList<LDUpdate> updateList = new  LinkedList<>();		
		while (!updates.get(threadId).isEmpty()) {
			LDUpdate update = updates.get(threadId).poll();
			log.debug("Dispatching link discovery update, ThreadId: {}, Update: {}", threadId, update);
			updateList.add(update);
		}
		
		if (linkDiscoveryAware != null && !updateList.isEmpty()) 	
			//order maintained
				for (ILinkDiscoveryListener lda : linkDiscoveryAware) 					
					if (updateList != null)
						lda.linkDiscoveryUpdate(updateList);
				
	}
	
	private synchronized void doLLDPUpdates(long threadId) throws InterruptedException {

		
		//HashMap<Long, TreeMap<Long, Update>> treeUpdates =  TracePacketIn.getInstance().getLinkDiscoveryUpdates(threadId);
		HashMap<Long, TreeMap<Long, Update>> treeUpdates =  TracePacketIn.getInstance().getMyUpdates(threadId);
		
		ResultDS result = new ResultDS();
		
		if(treeUpdates.containsKey(threadId) && treeUpdates.get(threadId) != null) {
			if(treeUpdates.get(threadId).keySet().size() > 0) {
				log.info("Called: doLLDPUpdates(); ThreadID: {}, treeUpdates:{}", threadId, treeUpdates);
				result = persistence.storeData(treeUpdates.get(threadId));
			}else 
				log.info("NOT Called: doLLDPUpdates(); ThreadID: {}, treeUpdates: EMPTY", threadId);
		}
		
		
		
		if (result.getDatastoreStatus().equals(DatastoreStatus.WRITE_OK)) {
			log.info("consolidatedCoW, ThreadId:{}, Result:{}, ", threadId, result.getDatastoreStatus());			
			//dataWrite.onBatchConsumed(treeUpdates.get(threadId));
			//links.onBatchConsumed(treeUpdates.get(threadId));
			//switchLinks.onBatchConsumed(treeUpdates.get(threadId));
			//portLinks.onBatchConsumed(treeUpdates.get(threadId));
			informListeners(threadId);
			
		}else if(result.getDatastoreStatus().equals(DatastoreStatus.READ_ONLY) ){			
			log.debug("Dispatching message buffer, ThreadId:{}, Result:{}. ", threadId, result.getDatastoreStatus());
			//links.onBatchConsumed(treeUpdates.get(threadId));
			informListeners(threadId);
			
		}else {
			log.info("Not called consolidatedCoW, ThreadId:{}, Result:{}, ", threadId, result.getDatastoreStatus());
			//links.onBatchRollback(treeUpdates.get(threadId));
			//switchLinks.onBatchRollback(treeUpdates.get(threadId));
			//portLinks.onBatchRollback(treeUpdates.get(threadId));	
			
			updates.get(threadId).remove();
		}
	}
	
}