# Heimdall - A Distributed update aware SDN Controller.

Heimdall is a Distributed SDN Controller which persist modules updates transparently. 
It leverages from Java data structures to catch the uptades ant persist them using ZooKeeper Service. 


## Heimdall initialization.

#### Controller 1
$bash heimdall.sh src/main/resources/heimdallDefault.properties

#### Controller 2
$bash heimdall.sh src/main/resources/heimdallDefault2.properties

#### Controller 3 
$bash heimdall.sh src/main/resources/heimdallDefault3.properties

## Mininet initialization
$mn --mac --controller=remote,ip=192.168.1.215,port=7753 --controller=remote,ip=192.168.1.215,port=6653 --topo tree,depth=4,fanout=2 --switch ovsk,protocols=OpenFlow14

## Dependencies.

### zookeeper-3.4.10.jar

ZooKeeper shall be installed and running at some machine. 
You shall configure ZooKeeper IP:PORT at net.floodlightcontroller.heimdall.PersistentDomain.
