package net.floodlightcontroller.heimdall.modulesRecover;
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

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.firewall.FirewallRule;
import net.floodlightcontroller.firewall.FirewallRule.FirewallAction;

public class RecoverFirewall {

	public RecoverFirewall() {
	}

	public FirewallRule recoverFirewall(Object [] zkData, String pathKey){
		
		FirewallRule fr = new FirewallRule();
		
		HashMap<String, Object> m = (HashMap<String, Object>) zkData[3];
		DatapathId dpid=null;
		fr.dpid = dpid.of(m.get("dpid").toString()); 
		OFPort in_port =null;
		//logger.debug("in_port:{}:", m.get("in_port"));
		fr.in_port = in_port.ofInt(Integer.parseInt(m.get("in_port").toString()));
		MacAddress dl_src=null;
		fr.dl_src = dl_src.of(m.get("dl_src").toString());
		MacAddress dl_dst=null;
		fr.dl_dst = dl_dst.of(m.get("dl_dst").toString());
		EthType dl_type=null;
		fr.dl_type = dl_type.of(Integer.parseInt(m.get("dl_type").toString()));
		//logger.debug("dl_type: {}", fr.dl_type);
		IPv4Address ipv4_src=null;
		ipv4_src = ipv4_src.of(m.get("nw_src_prefix").toString());
		IPv4Address ipv4_mask=null;
		ipv4_mask = ipv4_mask.ofCidrMaskLength(Integer.parseInt(m.get("nw_src_maskbits").toString()));
		IPv4AddressWithMask nw_src_prefix_and_mask=null;		    
		fr.nw_src_prefix_and_mask  = nw_src_prefix_and_mask.of(ipv4_src, ipv4_mask);

		IPv4Address ipv4_dst=null;
		ipv4_dst = ipv4_dst.of(m.get("nw_dst_prefix").toString());
		ipv4_mask = ipv4_mask.ofCidrMaskLength(Integer.parseInt(m.get("nw_dst_maskbits").toString()));
		IPv4AddressWithMask nw_dst_prefix_and_mask=null;
		fr.nw_dst_prefix_and_mask = nw_dst_prefix_and_mask.of(ipv4_dst, ipv4_mask);
		//logger.debug("fr.nw_dst_prefix_and_mask: {}",fr.nw_dst_prefix_and_mask);
		IpProtocol nw_proto=null;
		fr.nw_proto = nw_proto.of((short) Integer.parseInt(m.get("nw_proto").toString()));
		TransportPort tp_src=null;
		fr.tp_src = tp_src.of(Integer.parseInt(m.get("tp_src").toString()));
		TransportPort tp_dst=null;
		fr.tp_dst = tp_dst.of(Integer.parseInt(m.get("tp_dst").toString()));
		//boolean any_dpid;
		fr.any_dpid = Boolean.parseBoolean(m.get("any_dpid").toString());
		//boolean any_in_port;
		fr.any_in_port = Boolean.parseBoolean(m.get("any_in_port").toString());
		//boolean any_dl_src;
		fr.any_dl_src = Boolean.parseBoolean(m.get("any_dl_src").toString());
		//boolean any_dl_dst;
		fr.any_dl_dst = Boolean.parseBoolean(m.get("any_dl_dst").toString());
		//boolean any_dl_type;
		fr.any_dl_type = Boolean.parseBoolean(m.get("any_dl_type").toString());
		//boolean any_nw_src;
		fr.any_nw_src = Boolean.parseBoolean(m.get("any_nw_src").toString());
		//boolean any_nw_dst;
		fr.any_nw_dst = Boolean.parseBoolean(m.get("any_nw_dst").toString());
		//boolean any_nw_proto;
		fr.any_nw_proto = Boolean.parseBoolean(m.get("any_nw_proto").toString());
		//boolean any_tp_src;
		fr.any_tp_src = Boolean.parseBoolean(m.get("any_tp_src").toString());
		//boolean any_tp_dst;
		fr.any_tp_dst = Boolean.parseBoolean(m.get("any_tp_dst").toString());

		fr.priority = Integer.parseInt(m.get("priority").toString());

		FirewallAction fa=null;
		if(m.get("action").toString().equals("ALLOW"))
			fr.action = fa.ALLOW;
		else
			fr.action = fa.DROP;

		fr.ruleid = Integer.parseInt(m.get("ruleid").toString());
		
		return fr;
		
	}
}
