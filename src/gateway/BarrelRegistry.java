package gateway;

import common.IBarrel;
import common.IBarrelRegistry;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Barrel Registry - regista todos os barrels ativos no sistema
 * permite multicast RMI confiavel de downloaders para todos os barrels
 */
public class BarrelRegistry extends UnicastRemoteObject implements IBarrelRegistry {

    private Map<String, IBarrel> activeBarrels;

    public BarrelRegistry() throws RemoteException {
        super();
        this.activeBarrels = new ConcurrentHashMap<>();
        System.out.println("Barrel Registry initialized");
    }

    @Override
    public synchronized void registerBarrel(String barrelId, IBarrel barrel) throws RemoteException {
        // se barrel ja registado, remove o antigo primeiro
        if (activeBarrels.containsKey(barrelId)) {
            System.out.println("[BarrelRegistry] Barrel " + barrelId + " already registered, replacing...");
            activeBarrels.remove(barrelId);
        }

        // verifica se barrel esta acessivel
        try {
            barrel.ping();
            activeBarrels.put(barrelId, barrel);
            System.out.println(
                    "[BarrelRegistry] Registered barrel: " + barrelId + " (total: " + activeBarrels.size() + ")");
        } catch (RemoteException e) {
            System.err.println("[BarrelRegistry] Failed to register barrel " + barrelId + ": " + e.getMessage());
            throw e;
        }
    }

    @Override
    public synchronized void unregisterBarrel(String barrelId) throws RemoteException {
        IBarrel removed = activeBarrels.remove(barrelId);
        if (removed != null) {
            System.out.println(
                    "[BarrelRegistry] Unregistered barrel: " + barrelId + " (remaining: " + activeBarrels.size() + ")");
        } else {
            System.out.println("[BarrelRegistry] Barrel not found for unregister: " + barrelId);
        }
    }

    @Override
    public synchronized List<IBarrel> getAllBarrels() throws RemoteException {
        // limpa barrels mortos
        List<String> deadBarrels = new ArrayList<>();
        for (Map.Entry<String, IBarrel> entry : activeBarrels.entrySet()) {
            try {
                entry.getValue().ping();
            } catch (RemoteException e) {
                System.err.println("[BarrelRegistry] Barrel " + entry.getKey() + " is not responding, removing");
                deadBarrels.add(entry.getKey());
            }
        }

        // Remove barrels mortos
        for (String barrelId : deadBarrels) {
            activeBarrels.remove(barrelId);
        }

        return new ArrayList<>(activeBarrels.values());
    }

    @Override
    public int getBarrelCount() throws RemoteException {
        return activeBarrels.size();
    }

    @Override
    public boolean isBarrelRegistered(String barrelId) throws RemoteException {
        return activeBarrels.containsKey(barrelId);
    }

    /**
     * obtem estatisticas sobre barrels registados
     */
    public Map<String, Integer> getBarrelStats() throws RemoteException {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, IBarrel> entry : activeBarrels.entrySet()) {
            try {
                int size = entry.getValue().getIndexSize();
                stats.put(entry.getKey(), size);
            } catch (RemoteException e) {
                stats.put(entry.getKey(), -1); // indica erro
            }
        }
        return stats;
    }
}
