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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.annotation.Nonnull;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;

import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.AttachmentPoint;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.devicemanager.internal.Entity;
import net.floodlightcontroller.heimdall.Tar;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;

public class RecoverDeviceManager{
	private static Logger logger;

	public RecoverDeviceManager(){
		logger = LoggerFactory.getLogger(RecoverDeviceManager.class);	
	}
	
	/**
	 * 
	 * @param zkData from durable storage
	 * @param deviceManagerService, interface called to recover 
	 * @return
	 */
	public Entity[] recoverDeviceManager( Object [] zkData, String pathKey ) {
		//We need first retrieve information, process it and call the method to recover. 

		Entity[] e = stringToEntity(zkData, pathKey);
		return e;
	}
	
	
	   /**
  * Tulio Ribeiro
  * @param String json
  * @return Map<DatapathId, OFControllerRole>
  */
 @SuppressWarnings("unchecked")
private static Entity[] stringToEntity( Object [] zkData, String pathKey ){

	 
	 //We need first retrieve information, process it and call the method to recover. 
			HashMap <String, Object> m;
			Entity[] e=null;
			String mac;
			MacAddress macAddress;
			String vlanId;
			VlanVid vlan;
			String ipv4;
			IPv4Address ipv4Address;
			String ipv6;
			IPv6Address ipv6Address;
			ArrayList<LinkedHashMap<String, Object>> AP;
			LinkedHashMap<String, Object> hm;
			DatapathId switchDPID = DatapathId.NONE;
			OFPort switchPort = OFPort.ZERO;
			Date lastSeen;

			switch (zkData[1].toString()) {
			case "put(K,V)":
			case "replace(K,V,V)":
				m =  (HashMap<String, Object>) zkData[3];
				mac = m.get("mac").toString().replace("[", "");
				macAddress = MacAddress.of(mac.replace("]", ""));

				vlanId = m.get("vlan").toString().replace("[", "");
				vlanId = vlanId.replace("]", "");
				vlan = VlanVid.ofVlan(0x0);

				ipv4 = m.get("ipv4").toString().replace("[","");
				ipv4 = ipv4.replace("]","");
				if(!ipv4.equals(""))
					ipv4Address = IPv4Address.of(ipv4);			
				else
					ipv4Address = IPv4Address.NONE;

				ipv6 = m.get("ipv6").toString().replace("[","");
				ipv6 = ipv6.replace("]","");
				if(!ipv6.equals(""))
					ipv6Address = IPv6Address.of(ipv6);
				else
					ipv6Address = IPv6Address.NONE;
				
				//List<AttachmentPoint> lAP = new ArrayList<>();				
				AP = (ArrayList<LinkedHashMap<String, Object>>) m.get("attachmentPoint");
				e = new Entity[AP.size()];				
				for (int i = 0; i < AP.size(); i++) {
					hm = AP.get(i);
					switchDPID = DatapathId.of(hm.get("switch").toString());
					switchPort = OFPort.ofInt(Integer.parseInt(hm.get("port").toString()));
					//AttachmentPoint ap = new AttachmentPoint(switchDPID, switchPort, new Date());					
					e[i] = new Entity(macAddress, vlan, ipv4Address, ipv6Address, switchDPID, switchPort, new Date());
					logger.debug("New Entity; Mac:{}, Vlan:{}, IPv4:{}, IPv6:{}, Dpid:{}, Port:{}", 
							new Object[]{macAddress, vlan, ipv4Address, ipv6Address, switchDPID, switchPort});
				}
				return e;
				
			/*case "replace(K,V,V)":
				m =  (HashMap<String, Object>) zkData[4];
				mac = m.get("mac").toString().replace("[", "");
				macAddress = MacAddress.of(mac.replace("]", ""));

				vlanId = m.get("vlan").toString().replace("[", "");
				vlanId = vlanId.replace("]", "");
				vlan = VlanVid.ofVlan(0x0);

				ipv4 = m.get("ipv4").toString().replace("[","");
				ipv4 = ipv4.replace("]","");
				if(!ipv4.equals(""))
					ipv4Address = IPv4Address.of(ipv4);			
				else
					ipv4Address = IPv4Address.NONE;

				ipv6 = m.get("ipv6").toString().replace("[","");
				ipv6 = ipv6.replace("]","");
				if(!ipv6.equals(""))
					ipv6Address = IPv6Address.of(ipv6);
				else
					ipv6Address = IPv6Address.NONE;

				//List<AttachmentPoint> lAP = new ArrayList<>();				
				AP = (ArrayList<LinkedHashMap<String, Object>>) m.get("attachmentPoint");
				e = new Entity[AP.size()];				
				for (int i = 0; i < AP.size(); i++) {
					hm = AP.get(i);
					switchDPID = DatapathId.of(hm.get("switch").toString());
					switchPort = OFPort.ofInt(Integer.parseInt(hm.get("port").toString()));
					//AttachmentPoint ap = new AttachmentPoint(switchDPID, switchPort, new Date());					
					e[i] = new Entity(macAddress, vlan, ipv4Address, ipv6Address, switchDPID, switchPort, new Date());
					logger.debug("Creating Entity recover. {}", e);
				}
			
				
				logger.debug("New Entity; Mac:{}, Vlan:{}, IPv4:{}, IPv6:{}, Dpid:{}, Port:{}", 
						new Object[]{macAddress, vlan, ipv4Address, ipv6Address, switchDPID, switchPort});
				return e;
			*/
			default:

				break;
			}
			
			return null;
 	
 }

}
