#!/bin/bash 
mkdir -p 0w
propertiesFile="0w/heimdallCbench_0w.properties"

echo "" > $propertiesFile
cat configBase.properties > $propertiesFile
echo "net.floodlightcontroller.heimdall.test.Z1.loadWrite=0" >> $propertiesFile
echo "net.floodlightcontroller.heimdall.test.Z1.conflictDegree=NoConflict" >> $propertiesFile
echo "net.floodlightcontroller.heimdall.test.Z1.switchLoad=FullLoad" >> $propertiesFile
echo "net.floodlightcontroller.core.internal.FloodlightProvider.waitBefore=0" >> $propertiesFile
echo "net.floodlightcontroller.core.internal.FloodlightProvider.waitAfter=0" >> $propertiesFile

echo "0% write done."

port=6653

for ctrl in {1,2,3};
	do
	for write in {0,1,2,5,10,25,50,75,100};
	    do
	    mkdir -p $write"w"
	    for waitB in {0..0};
	        do
	        for waitA in {9..9};
	            do
	            for conflictDegree in {NoConflict,C_100,C_50,C_25,C_10,C_1,C_05,C_025};
	            	do
					propertiesFile=$write"w/heimdallCbench_"$write"w_"$conflictDegree"_Ctrl"$ctrl".properties"
					echo "" > $propertiesFile
					cat configBase.properties > $propertiesFile
					echo "net.floodlightcontroller.heimdall.test.Z1.loadWrite=$write" >> $propertiesFile
					echo "net.floodlightcontroller.heimdall.test.Z1.conflictDegree=$conflictDegree" >> $propertiesFile
					echo "net.floodlightcontroller.heimdall.test.Z1.switchLoad=FullLoad" >> $propertiesFile
					echo "net.floodlightcontroller.core.internal.FloodlightProvider.waitBefore=$waitB" >> $propertiesFile
					echo "net.floodlightcontroller.core.internal.FloodlightProvider.waitAfter=$waitA" >> $propertiesFile
					echo "net.floodlightcontroller.core.internal.OFSwitchManager.openFlowPort=$port" >> $propertiesFile
					echo "net.floodlightcontroller.core.internal.FloodlightProvider.openFlowPort=$port" >> $propertiesFile
					echo "net.floodlightcontroller.core.internal.FloodlightProvider.controllerId=$ctrl" >> $propertiesFile
	        	done
	        done
	    done
	    echo $write"% write done."
	done
	echo "Ctrl: " $ctrl ", port:" $port, "done."
	port=$((port + 1))	
done	
