# Heimdall


## Heimdall initialization 

#### Controller 1
$bash heimdall.sh src/main/resources/heimdallDefault.properties

#### Controller 2
$bash heimdall.sh src/main/resources/heimdallDefault2.properties

#### Controller 3 
$bash heimdall.sh src/main/resources/heimdallDefault3.properties

## Mininet initialization
$mn --mac --controller=remote,ip=192.168.1.215,port=7753 --controller=remote,ip=192.168.1.215,port=6653 --topo tree,depth=4,fanout=2 --switch ovsk,protocols=OpenFlow14

