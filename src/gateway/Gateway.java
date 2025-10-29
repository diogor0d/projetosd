package gateway;

import common.*;
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
    private VisitedURLs visitedUrls;
    private BarrelRegistry barrelRegistry; // Registry for multicast support

    // Cache de resultados de pesquisa
    private Map<String, List<SearchResult>> searchCache;

    public Gateway() throws RemoteException {
        super();
        this.barrels = new ArrayList<>();
        this.searchFrequency = new ConcurrentHashMap<>();
        this.currentBarrelIndex = 0;
        this.searchCache = new ConcurrentHashMap<>();
        this.urlQueue = new URLQueue();
        this.visitedUrls = new VisitedURLs();
        this.barrelRegistry = new BarrelRegistry(); // Initialize barrel registry

        System.out.println("Gateway initialized with Barrel Registry");
    }

    /**
     * Get the barrel registry (for RMI access)
     */
    public BarrelRegistry getBarrelRegistry() {
        return barrelRegistry;
    }

    public void registerBarrel(IBarrel barrel) {
        barrels.add(barrel);
        System.out.println("Barrel registered. Total barrels: " + barrels.size());
    }

    @Override
    public void indexURL(String url) throws RemoteException {
        System.out.println("Indexing URL (user-submitted): " + url);
        urlQueue.addURL(url, true); // High priority for user-submitted URLs
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

    public VisitedURLs getVisitedUrls() {
        return visitedUrls;
    }

    @Override
    public String pollURL(long timeout) throws RemoteException {
        try {
            return urlQueue.pollURL(timeout, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrupted while polling URL", e);
        }
    }

    @Override
    public String getNextURL() throws RemoteException {
        try {
            return urlQueue.getNextURL();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrupted while waiting for URL", e);
        }
    }

    @Override
    public boolean isURLVisited(String url) throws RemoteException {
        return visitedUrls.isVisited(url);
    }

    @Override
    public boolean markURLAsVisited(String url) throws RemoteException {
        return visitedUrls.markAsVisited(url);
    }

    @Override
    public void addDiscoveredURL(String url, boolean highPriority) throws RemoteException {
        urlQueue.addURL(url, highPriority);
    }

    @Override
    public String getURLQueueStatus() throws RemoteException {
        return urlQueue.getQueueAsString();
    }

    public static void main(String[] args) {
        try {
            Gateway gateway = new Gateway();

            // Regista no RMI Registry (ou usa existente)
            try {
                java.rmi.registry.LocateRegistry.createRegistry(1099);
                System.out.println("RMI Registry created on port 1099");
            } catch (Exception e) {
                // Registry já existe, continua
                System.out.println("Using existing RMI Registry on port 1099");
            }

            java.rmi.Naming.rebind("rmi://localhost/Gateway", gateway);

            // Also bind the barrel registry for direct access
            java.rmi.Naming.rebind("rmi://localhost/BarrelRegistry", gateway.getBarrelRegistry());

            System.out.println("Gateway and BarrelRegistry ready and listening...");
            System.out.println("Waiting for barrels to register themselves...");

            // Thread para imprimir status da fila e barrels registrados a cada 5 segundos
            Thread statusThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000); // 5 segundos
                        int queueSize = gateway.urlQueue.size();
                        int visitedSize = gateway.visitedUrls.size();
                        int barrelCount = 0;
                        try {
                            barrelCount = gateway.barrelRegistry.getBarrelCount();
                        } catch (Exception e) {
                            // Ignore
                        }
                        System.out.println(
                                "[Gateway Status] URL Queue: " + queueSize + " | Visited URLs: " + visitedSize
                                        + " | Active Barrels: " + barrelCount);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            statusThread.setDaemon(true); // Thread daemon para não impedir shutdown
            statusThread.start();

            // Mantém o servidor ativo
            System.out.println("Press Ctrl+C to stop...");
            Object lock = new Object();
            synchronized (lock) {
                lock.wait(); // Espera indefinidamente
            }

        } catch (Exception e) {
            System.err.println("Gateway exception:");
            e.printStackTrace();
        }
    }
}
