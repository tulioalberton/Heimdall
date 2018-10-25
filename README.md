# Heimdall


## Mininet initialization
mn --mac --controller=remote,ip=192.168.1.215,port=7753 --controller=remote,ip=192.168.1.215,port=6653 --topo tree,depth=4,fanout=2 --switch ovsk,protocols=OpenFlow14

