package gateway;

import common.*;
import queue.URLQueue;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RPC/RMI Gateway - Porta de entrada para clientes
 * Distribui pedidos entre os Storage Barrels disponíveis
 */
public class Gateway extends UnicastRemoteObject implements IGateway {

    private List<IBarrel> barrels;
    private Map<String, Integer> searchFrequency; // Tracking de pesquisas mais comuns
    private int currentBarrelIndex; // Para round-robin
    private URLQueue urlQueue;

    // Cache de resultados de pesquisa
    private Map<String, List<SearchResult>> searchCache;

    public Gateway() throws RemoteException {
        super();
        this.barrels = new ArrayList<>();
        this.searchFrequency = new ConcurrentHashMap<>();
        this.currentBarrelIndex = 0;
        this.searchCache = new ConcurrentHashMap<>();
        this.urlQueue = new URLQueue();

        System.out.println("Gateway initialized");
    }

    public void registerBarrel(IBarrel barrel) {
        barrels.add(barrel);
        System.out.println("Barrel registered. Total barrels: " + barrels.size());
    }

    @Override
    public void indexURL(String url) throws RemoteException {
        System.out.println("Indexing URL: " + url);
        urlQueue.addURL(url);
    }

    @Override
    public List<SearchResult> search(String query, int page) throws RemoteException {
        // Normaliza query
        String normalizedQuery = query.toLowerCase().trim();

        // Atualiza estatísticas de pesquisa
        searchFrequency.merge(normalizedQuery, 1, Integer::sum);

        // Verifica cache
        String cacheKey = normalizedQuery + "_" + page;
        if (searchCache.containsKey(cacheKey)) {
            System.out.println("Cache hit for: " + query);
            return searchCache.get(cacheKey);
        }

        // Divide query em termos
        Set<String> terms = new HashSet<>(Arrays.asList(normalizedQuery.split("\\s+")));

        // Seleciona barrel (round-robin)
        IBarrel barrel = selectBarrel();

        if (barrel == null) {
            throw new RemoteException("No active barrels available");
        }

        try {
            List<SearchResult> results = barrel.search(terms, page);

            // Armazena em cache
            searchCache.put(cacheKey, results);

            return results;

        } catch (RemoteException e) {
            System.err.println("Error searching on barrel: " + e.getMessage());
            // Tenta outro barrel
            barrels.remove(barrel);
            if (!barrels.isEmpty()) {
                return search(query, page); // Retry
            }
            throw e;
        }
    }

    @Override
    public List<String> getPagesPointingTo(String url) throws RemoteException {
        IBarrel barrel = selectBarrel();

        if (barrel == null) {
            throw new RemoteException("No active barrels available");
        }

        return barrel.getInlinks(url);
    }

    @Override
    public SystemStats getSystemStats() throws RemoteException {
        SystemStats stats = new SystemStats();

        // Top 10 pesquisas
        List<String> topSearches = searchFrequency.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        stats.setTopSearches(topSearches);

        // Estatísticas dos barrels
        List<BarrelStats> barrelStatsList = new ArrayList<>();
        int totalPages = 0;

        for (IBarrel barrel : barrels) {
            try {
                if (barrel.ping()) {
                    BarrelStats bs = barrel.getStats();
                    barrelStatsList.add(bs);
                    totalPages = Math.max(totalPages, bs.getIndexSize());
                }
            } catch (Exception e) {
                System.err.println("Failed to get stats from barrel: " + e.getMessage());
            }
        }

        stats.setBarrelStats(barrelStatsList);
        stats.setTotalIndexedPages(totalPages);
        stats.setActiveDownloaders(0); // TODO: implementar tracking de downloaders

        return stats;
    }

    /**
     * Seleciona um barrel usando estratégia round-robin
     */
    private synchronized IBarrel selectBarrel() {
        if (barrels.isEmpty()) {
            return null;
        }

        // Remove barrels inativos
        barrels.removeIf(barrel -> {
            try {
                return !barrel.ping();
            } catch (RemoteException e) {
                return true; // Remove se não conseguir fazer ping
            }
        });

        if (barrels.isEmpty()) {
            return null;
        }

        // Round-robin
        IBarrel selected = barrels.get(currentBarrelIndex);
        currentBarrelIndex = (currentBarrelIndex + 1) % barrels.size();

        return selected;
    }

    public URLQueue getUrlQueue() {
        return urlQueue;
    }

    public static void main(String[] args) {
        try {
            Gateway gateway = new Gateway();

            // Regista no RMI Registry
            java.rmi.registry.LocateRegistry.createRegistry(1099);
            java.rmi.Naming.rebind("rmi://localhost/Gateway", gateway);

            System.out.println("Gateway ready and listening...");

            // Conecta aos barrels existentes
            try {
                IBarrel barrel1 = (IBarrel) java.rmi.Naming.lookup("rmi://localhost/Barrel1");
                gateway.registerBarrel(barrel1);
            } catch (Exception e) {
                System.err.println("Could not connect to Barrel1: " + e.getMessage());
            }

            try {
                IBarrel barrel2 = (IBarrel) java.rmi.Naming.lookup("rmi://localhost/Barrel2");
                gateway.registerBarrel(barrel2);
            } catch (Exception e) {
                System.err.println("Could not connect to Barrel2: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Gateway exception:");
            e.printStackTrace();
        }
    }
}
