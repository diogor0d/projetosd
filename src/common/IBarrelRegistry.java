package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Registry service to track all active barrels
 * Enables downloader multicast functionality
 */
public interface IBarrelRegistry extends Remote {

    /**
     * Register a barrel in the system
     * 
     * @param barrelId Unique barrel identifier
     * @param barrel   The barrel RMI reference
     */
    void registerBarrel(String barrelId, IBarrel barrel) throws RemoteException;

    /**
     * Unregister a barrel from the system
     * 
     * @param barrelId Barrel identifier to remove
     */
    void unregisterBarrel(String barrelId) throws RemoteException;

    /**
     * Get list of all active barrels
     * 
     * @return List of active barrel RMI references
     */
    List<IBarrel> getAllBarrels() throws RemoteException;

    /**
     * Get count of active barrels
     * 
     * @return Number of registered barrels
     */
    int getBarrelCount() throws RemoteException;

    /**
     * Check if a specific barrel is registered
     * 
     * @param barrelId Barrel identifier
     * @return true if barrel is registered
     */
    boolean isBarrelRegistered(String barrelId) throws RemoteException;
}
