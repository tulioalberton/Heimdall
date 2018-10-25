==================================================================================
Your module needs to be added in our module dependencies, we need start after all modules: 

getModuleDependencies()...
l.add(YourModuleInterface.class);

on init() function:
	tarService = context.getServiceImpl(ITarService.class);

on startUp function:
	rules = tarService.createArrayListWatcher(new ArrayList<FirewallRule>(), "firewall:rules");
	deviceMap = tarService.createConcurrentHashMapWatcher(new ConcurrentHashMap<Long, Device>(), "deviceMap");
and go on...


Add on your service the correct manner to recover your data.
See RecoverDeviceManager class. 
You will need implement or change to public in your service
See IDeviceService and DeviceManagerImpl
public boolean recoverDeviceByEntity(Entity entity); 
 
==================================================================================
Controller.java - search for /*Tulio Ribeiro*/
/*Tulio Ribeiro*/ 
long threadId = Thread.currentThread().getId();
	if(m.getType().equals(OFType.PACKET_IN))
		TracePacketIn.getInstance().addMsgBuffer(threadId, new Object[]{sw});
		
on function setConfigParams(Map<String, String> configParams);		
 String controllerId = configParams.get("controllerId");
        if (!Strings.isNullOrEmpty(controllerId)) {
            try {
                this.controllerId = controllerId;
            } catch (Exception e) {
                log.error("Invalid controlelrId {}, {}", controllerId, e);
                throw new FloodlightModuleException("Invalid controllerId of " + controllerId + " in config");
            }
        }
        log.info("ControllerId set to {}", controllerId);

IFloodlightProvider...
public Long getControllerId();        
 ==================================================================================   					
 DeviceManagerImplTest.java commented some lines:
 Lines 1934, 1945, 1954
 //deviceManager.deviceMap = new ConcurrentlyModifiedDeviceMap(false);
==================================================================================
net.floodlightcontroller.core.IOFSwitch.java
	void write(OFMessage m, boolean isFromPacketIn);
 
net.floodlightcontroller.core.internal.OFSwitch.java
	@Override
	public void write(OFMessage m, boolean isFromPacketIn) {
		// TODO Auto-generated method stub
		TracePacketIn.getInstance().addMsgBuffer(
				Thread.currentThread().getId(), 
				new Object[]{connections.get(OFAuxId.MAIN),m});
	}
	
==================================================================================
Forwarding.java	
	/*try {
			if (log.isTraceEnabled()) {
				log.trace("Writing flood PacketOut switch={} packet-in={} packet-out={}",
						new Object[] {sw, pi, pob.build()});
			}
			messageDamper.write(sw, pob.build());
		} catch (IOException e) {
			log.error("Failure writing PacketOut switch={} packet-in={} packet-out={}",
					new Object[] {sw, pi, pob.build()}, e);
		}*/
		
		sw.write(pob.build(), true);
Include on IOFSwitch the new method signature	    					
==================================================================================
OFMessageDamperMockSwitch.java	
Add this: 
@Override
	public void write(OFMessage m, boolean isFromPacketIn) {
		// TODO Auto-generated method stub
	}	
==================================================================================
net.floodlightcontroller.learningswitch.LearningSwitch

Comment and change
	//sw.write(pob.build());
	sw.write(pob.build(), true);
==================================================================================	
net.floodlightcontroller.loadbalancer.LoadBalancer	
Comment and change
	//sw.write(pob.build());
	sw.write(pob.build(), true);
==================================================================================	
Firewall.java e IFirewallService
Added parameter dispatch memory	
public void addRule(FirewallRule rule, boolean dispatchMemory);
And at end of Firewall.java, function addRule(...);
rules = tarService.createArrayListWatcher(new ArrayList<FirewallRule>(), "firewall:rules");

if(dispatchMemory)
			TracePacketIn.getInstance().dispatchMemoryChanges(11000);
==================================================================================
ITopologyService add this:
	public void addPortToSwitch(DatapathId s, OFPort p);
	public void addSwitchPortLinks(NodePortTuple npt, Set<Link> l);

TopologyManager add this:
	public void addSwitchPortLinks(NodePortTuple npt, Set<Link> l){
		switchPortLinks.put(npt,l);
	}
	Change to public: addPortToSwitch(DatapathId s, OFPort p);	
==================================================================================	
LinkDiscoveryManager 
//Commented line, we don't need the storage anymore. Need to see better this claim. 	
//writeLinkToStorage(lt, newInfo);// not commented

==================================================================================
At class OFSwitchHandshakeHandler in WaitInitialRoleState function 

@Override
		void enterState(){
			//original
			//sendRoleRequest(roleManager.getOFControllerRole());
			/**
			 * Tulio Ribeiro
			 * Retrieve role from floodlightdefault.properties configuration file
			 * swId;Role 00:00:00:00:00:00:00:01;ROLE_SLAVE
			 * If not defined there, the role will be set as MASTER
			 */
			OFControllerRole role = OFControllerRole.ROLE_MASTER;
			if(OFSwitchManager.switchInitialRole.containsKey(mainConnection.getDatapathId())){
				role = OFSwitchManager.switchInitialRole.get(mainConnection.getDatapathId());
				log.info("Defining switch role from config file: {}", role);				
			}
			sendRoleRequest(role);
		}

==================================================================================
In OFChannelHandler and OFSwitchManager search for Tulio Ribeiro changes		
	