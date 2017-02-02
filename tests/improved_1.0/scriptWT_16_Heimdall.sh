#!/bin/bash 

/opt/mtcbench/oflops/cbench/cbench -c 192.168.3.33 -p 6653 -l 105 -M 32 -m 10000 -D 1000 -S 1 -Z 1 -e 12000 -w 5 -t > Heimdall_WT_16-L105-S1-Z1.imp1.logCbench 2>&1
sleep 15
/opt/mtcbench/oflops/cbench/cbench -c 192.168.3.33 -p 6653 -l 105 -M 32 -m 10000 -D 1000 -S 1 -Z 2 -e 12000 -w 5 -t > Heimdall_WT_16-L105-S1-Z2.imp1.logCbench 2>&1
sleep 15
/opt/mtcbench/oflops/cbench/cbench -c 192.168.3.33 -p 6653 -l 105 -M 32 -m 10000 -D 1000 -S 1 -Z 4 -e 12000 -w 5 -t > Heimdall_WT_16-L105-S1-Z4.imp1.logCbench 2>&1
sleep 15
/opt/mtcbench/oflops/cbench/cbench -c 192.168.3.33 -p 6653 -l 105 -M 32 -m 10000 -D 1000 -S 1 -Z 8 -e 12000 -w 5 -t > Heimdall_WT_16-L105-S1-Z8.imp1.logCbench 2>&1
sleep 15
/opt/mtcbench/oflops/cbench/cbench -c 192.168.3.33 -p 6653 -l 105 -M 32 -m 10000 -D 1000 -S 1 -Z 16 -e 12000 -w 5 -t > Heimdall_WT_16-L105-S1-Z16.imp1.logCbench 2>&1
sleep 15
/opt/mtcbench/oflops/cbench/cbench -c 192.168.3.33 -p 6653 -l 105 -M 32 -m 10000 -D 1000 -S 2 -Z 16 -e 12000 -w 5 -t > Heimdall_WT_16-L105-S2-Z16.imp1.logCbench 2>&1
sleep 15
/opt/mtcbench/oflops/cbench/cbench -c 192.168.3.33 -p 6653 -l 105 -M 32 -m 10000 -D 1000 -S 4 -Z 16 -e 12000 -w 5 -t > Heimdall_WT_16-L105-S4-Z16.imp1.logCbench 2>&1
