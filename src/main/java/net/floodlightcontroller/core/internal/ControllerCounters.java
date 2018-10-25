package net.floodlightcontroller.core.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;

@SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
                    justification = "It is ok to predefine Debug Counters that are not yet used")
public class ControllerCounters {

    public final String prefix = ControllerCounters.class.getSimpleName();
    public final String statsPrefix = IOFSwitchBackend.class.getPackage().getName();

    public final IDebugCounter packetParsingError;
    public final IDebugCounter dispatchMessageWhileStandby;
    public final IDebugCounter dispatchMessage;
    public final IDebugCounter packetIn;
    public final IDebugCounter packetInRollback;
    public final IDebugCounter batchRollback;
    public final IDebugCounter txInvalid;
    public final IDebugCounter packetInStopped;
    public final IDebugCounter dataStoreAccess;
    public final IDebugCounter write;
    public final IDebugCounter read;
    public final IDebugCounter remove;
    public final IDebugCounter badVersion;

    public ControllerCounters(IDebugCounterService debugCounters) {
        debugCounters.registerModule(prefix);
        debugCounters.registerModule(OFConnectionCounters.COUNTER_MODULE);

        dispatchMessageWhileStandby = debugCounters.registerCounter(prefix,
                                                                    "dispatch-message-while-slave",
                                                                    "Number of times an OF message was received "
                                                                            + "and supposed to be dispatched but the "
                                                                            + "controller was in SLAVE role and the message "
                                                                            + "was not dispatched");
        // does this cnt make sense? more specific?? per type?
        // count stops?
        dispatchMessage = debugCounters.registerCounter(prefix,
                                                        "dispatch-message",
                                                        "Number of times an OF message was dispatched "
                                                                + "to registered modules");

        // TODO: FIXME
        // Need a better way to handle these
        packetParsingError = debugCounters.registerCounter(prefix,
                                                           "packet-parsing-error",
                                                           "Number of times the packet parsing "
                                                                   + "encountered an error",
                                                           MetaData.ERROR);
        
        packetIn = debugCounters.registerCounter(prefix, "packet-in", "Number of packet_in's seen");
        
        packetInRollback = debugCounters.registerCounter(prefix, "packet-inRollback", "Number of rollback packet_in's");
        packetInStopped = debugCounters.registerCounter(prefix, "packet-inStopped", "Number of stopped packet_in's");
        
        dataStoreAccess = debugCounters.registerCounter(prefix, "dataStoreAccess", "Number of Data Store Access.");
        
        write = debugCounters.registerCounter(prefix, "WRITE", "Number of WRITE.");
        read = debugCounters.registerCounter(prefix, "READ", "Number of READ.");
        remove = debugCounters.registerCounter(prefix, "Remove", "Number of REMOVE.");
        badVersion = debugCounters.registerCounter(prefix, "BadVersion", "Number of BarVersion reveived from Data Store.");
        batchRollback = debugCounters.registerCounter(prefix, "BatchRollback", "Number of Batches rollback before goes to Data Store.");
        txInvalid = debugCounters.registerCounter(prefix, "InvalidTransaction", "Number of invalid transactions.");
        
        
    }
}
