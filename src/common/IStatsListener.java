package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote listener interface for receiving pushed SystemStats updates from the
 * Gateway.
 */
public interface IStatsListener extends Remote {
    /**
     * Called by the Gateway to push an updated snapshot of SystemStats.
     */
    void updateStats(SystemStats stats) throws RemoteException;
}
