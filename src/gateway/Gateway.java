package gateway;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.net.URI;

/**
 * RPC/RMI Gateway - Porta de entrada para clientes
 * Distribui pedidos entre os Storage Barrels disponíveis
 */
public class Gateway extends UnicastRemoteObject implements IGateway {

    private List<IBarrel> barrels;
    private Map<String, Integer> searchFrequency; // Tracking de pesquisas mais comuns
    private int currentBarrelIndex; // Para round-robin
    private URLQueue urlQueue;
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

    /**
     * Validates if a string is a proper URL
     */
    private boolean isValidURL(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        try {
            URI uri = URI.create(urlString);

            // Check if URI is absolute (has scheme)
            if (!uri.isAbsolute()) {
                return false;
            }

            // Check protocol
            String scheme = uri.getScheme();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }

            // Check host exists
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return false;
            }

            // Additional host validation
            // Reject hosts ending with a dot
            if (host.endsWith(".")) {
                return false;
            }

            // Reject hosts with invalid characters or patterns
            if (host.startsWith(".") || host.contains("..")) {
                return false;
            }

            // Require at least one dot for domain (reject single-word hosts like
            // "localhost" in production)
            // But allow IP addresses
            if (!host.matches(".*\\..*") && !host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                // Single label without dots - only accept "localhost"
                if (!host.equals("localhost")) {
                    return false;
                }
            }

            // Check for valid domain structure (at least 2 characters in TLD)
            if (host.matches(".*\\..$") || host.matches(".*\\.$")) {
                return false; // TLD too short or ends with dot
            }

            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void indexURL(String url) throws RemoteException {
        // Validate URL before processing
        if (!isValidURL(url)) {
            System.out.println("Invalid URL rejected: " + url);
            throw new RemoteException(
                    "Invalid URL: " + url + ". URL must start with http:// or https:// and have a valid format.");
        }

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
        try {
            // Query ALL barrels and combine results (data might be distributed)
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();

            if (activeBarrels.isEmpty()) {
                throw new RemoteException("No active barrels available");
            }

            // Collect inlinks from all barrels and deduplicate
            Set<String> allInlinks = new HashSet<>();

            for (IBarrel barrel : activeBarrels) {
                try {
                    List<String> inlinks = barrel.getInlinks(url);
                    if (inlinks != null) {
                        allInlinks.addAll(inlinks);
                    }
                } catch (RemoteException e) {
                    // Skip barrels that fail, continue with others
                    System.err.println("Failed to get inlinks from barrel: " + e.getMessage());
                }
            }

            return new ArrayList<>(allInlinks);
        } catch (Exception e) {
            System.err.println("Error getting pages pointing to URL: " + e.getMessage());
            throw new RemoteException("Error querying barrels for inlinks", e);
        }
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

        // Estatísticas dos barrels - use BarrelRegistry instead of old list
        List<BarrelStats> barrelStatsList = new ArrayList<>();
        int totalPages = 0;

        try {
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();
            for (IBarrel barrel : activeBarrels) {
                try {
                    BarrelStats bs = barrel.getStats();
                    barrelStatsList.add(bs);
                    totalPages += bs.getIndexSize(); // Sum all pages across barrels
                } catch (Exception e) {
                    System.err.println("Failed to get stats from barrel: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get barrels from registry: " + e.getMessage());
        }

        stats.setBarrelStats(barrelStatsList);
        stats.setTotalIndexedPages(totalPages);

        return stats;
    }

    /**
     * Seleciona um barrel usando estratégia round-robin
     */
    private synchronized IBarrel selectBarrel() {
        try {
            // Get barrels from registry (not the old list)
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();

            if (activeBarrels.isEmpty()) {
                return null;
            }

            // Round-robin selection
            IBarrel selected = activeBarrels.get(currentBarrelIndex % activeBarrels.size());
            currentBarrelIndex = (currentBarrelIndex + 1) % activeBarrels.size();

            return selected;
        } catch (Exception e) {
            System.err.println("Error selecting barrel: " + e.getMessage());
            return null;
        }
    }

    public URLQueue getUrlQueue() {
        return urlQueue;
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
        // No longer tracking visited URLs - always return false
        return false;
    }

    @Override
    public boolean markURLAsVisited(String url) throws RemoteException {
        // No longer tracking visited URLs - always return true (success)
        return true;
    }

    @Override
    public boolean tryMarkAsVisited(String url) throws RemoteException {
        // No longer tracking visited URLs - always return true (success)
        return true;
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

            java.rmi.Naming.rebind("rmi://localhost:1099/Gateway", gateway);

            // Also bind the barrel registry for direct access
            java.rmi.Naming.rebind("rmi://localhost:1099/BarrelRegistry", gateway.getBarrelRegistry());

            System.out.println("Gateway bound to rmi://localhost:1099/Gateway");
            System.out.println("BarrelRegistry bound to rmi://localhost:1099/BarrelRegistry");
            System.out.println("Waiting for barrels to register themselves...");

            // Thread para imprimir status da fila e barrels registrados a cada 5 segundos
            final boolean[] keepRunning = { true };
            Thread statusThread = new Thread(() -> {
                while (keepRunning[0]) {
                    try {
                        Thread.sleep(5000); // 5 segundos
                        int queueSize = gateway.urlQueue.size();
                        int barrelCount = 0;
                        try {
                            barrelCount = gateway.barrelRegistry.getBarrelCount();
                        } catch (Exception e) {
                            // Ignore
                        }
                        System.out.println(
                                "[Gateway Status] URL Queue: " + queueSize
                                        + " | Active Barrels: " + barrelCount);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            statusThread.setDaemon(true); // Thread daemon para não impedir shutdown
            statusThread.start();

            // Add shutdown hook for clean exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // Flush output streams to avoid Maven/Jansi issues
                    System.out.flush();
                    System.err.flush();

                    System.out.println("\n\nShutting down Gateway...");
                    keepRunning[0] = false;

                    try {
                        // Unbind from RMI registry
                        java.rmi.Naming.unbind("rmi://localhost/Gateway");
                        java.rmi.Naming.unbind("rmi://localhost/BarrelRegistry");
                        System.out.println("Unbound from RMI registry");
                    } catch (Exception e) {
                        // Ignore errors during shutdown
                    }

                    try {
                        // Unexport the gateway
                        UnicastRemoteObject.unexportObject(gateway, true);
                        System.out.println("Gateway unexported");
                    } catch (Exception e) {
                        // Ignore errors during shutdown
                    }

                    System.out.println("Gateway shutdown complete. Goodbye!");
                    System.out.flush();
                } catch (Exception e) {
                    // Suppress output errors during shutdown (Maven/Jansi issues)
                }
            }));

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
