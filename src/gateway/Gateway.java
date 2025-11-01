package gateway;

import common.*;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
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
    private int currentBarrelIndex; // Para round-robin (fallback)
    private URLQueue urlQueue;
    private BarrelRegistry barrelRegistry; // Registry for multicast support

    // Cache de resultados de pesquisa (LRU with size limit)
    private Map<String, List<SearchResult>> searchCache;
    private static final int SEARCH_CACHE_SIZE = 1000; // Keep 1000 most popular searches

    // Visited URLs tracking using Bloom filter (space-efficient)
    private BloomFilter<String> visitedURLsBloom;
    // Recent URLs cache (last 10K) to eliminate false positives from Bloom filter
    private Map<String, Boolean> recentVisitedURLs;
    private static final int RECENT_CACHE_SIZE = 10000;
    private static final int EXPECTED_URLS = 10_000_000; // Expected 10M URLs
    private static final double FALSE_POSITIVE_RATE = 0.01; // 1% false positive rate

    // Registered remote listeners for pushed system statistics
    private Set<common.IStatsListener> statsListeners;
    // Last snapshot sent to listeners (string form) to avoid sending identical
    // updates. We only push when the stats representation actually changes.
    private volatile String lastStatsSnapshotString = "";

    // Response time tracking for load balancing
    // Map: barrel identity hashcode -> list of recent response times (in ms)
    private Map<Integer, List<Long>> barrelResponseTimes;
    private static final int MAX_RESPONSE_SAMPLES = 20; // Keep last 10 response times

    public Gateway() throws RemoteException {
        super();
        this.barrels = new ArrayList<>();
        this.searchFrequency = new ConcurrentHashMap<>();
        this.currentBarrelIndex = 0;

        // Initialize search cache with LRU eviction
        this.searchCache = Collections.synchronizedMap(
                new LinkedHashMap<String, List<SearchResult>>(SEARCH_CACHE_SIZE + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest) {
                        return size() > SEARCH_CACHE_SIZE;
                    }
                });

        this.urlQueue = new URLQueue();
        this.barrelRegistry = new BarrelRegistry(); // Initialize barrel registry

        // Initialize Bloom filter for visited URLs tracking
        this.visitedURLsBloom = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_URLS,
                FALSE_POSITIVE_RATE);

        // Initialize LRU cache for recent URLs (eliminates false positives)
        this.recentVisitedURLs = Collections.synchronizedMap(
                new LinkedHashMap<String, Boolean>(RECENT_CACHE_SIZE + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                        return size() > RECENT_CACHE_SIZE;
                    }
                });

        this.statsListeners = ConcurrentHashMap.newKeySet();
        this.barrelResponseTimes = new ConcurrentHashMap<>();

        System.out.println("[Gateway] Initialized with Bloom filter (expected: " +
                EXPECTED_URLS + " URLs, FPR: " + (FALSE_POSITIVE_RATE * 100) + "%)");
        System.out.println("[Gateway] Recent cache size: " + RECENT_CACHE_SIZE + " URLs");

        System.out.println("Gateway initialized with Barrel Registry");
    }

    /**
     * Get the barrel registry (for RMI access)
     */
    public BarrelRegistry getBarrelRegistry() {
        return barrelRegistry;
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

        System.out.println("New user index request: " + url);
        urlQueue.addURL(url, true); // High priority for user-submitted URLs
        // Notify listeners about stats change (new user-submitted URL affects queue
        // size)
        try {
            notifyStatsListeners(getSystemStats());
        } catch (Exception e) {
            // ignore notification failures here
        }
    }

    @Override
    public List<SearchResult> search(String query, int page) throws RemoteException {
        // Normaliza query
        String normalizedQuery = query.toLowerCase().trim();

        // Atualiza estatísticas de pesquisa
        searchFrequency.merge(normalizedQuery, 1, Integer::sum);
        // Notify listeners that search frequencies changed
        try {
            notifyStatsListeners(getSystemStats());
        } catch (Exception e) {
            // ignore
        }

        // Verifica cache
        String cacheKey = normalizedQuery + "_" + page;
        if (searchCache.containsKey(cacheKey)) {
            System.out.println("Cache hit for: " + query);
            return searchCache.get(cacheKey);
        }

        // Divide query em termos
        Set<String> terms = new HashSet<>(Arrays.asList(normalizedQuery.split("\\s+")));

        // Seleciona barrel (response-time based)
        IBarrel barrel = selectBarrel();

        if (barrel == null) {
            throw new RemoteException("No active barrels available");
        }

        try {
            long startTime = System.currentTimeMillis();
            List<SearchResult> results = barrel.search(terms, page);
            long responseTime = System.currentTimeMillis() - startTime;

            // Record response time for this barrel
            recordBarrelResponseTime(barrel, responseTime);

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

        // Estatísticas dos barrels - use BarrelRegistry and enrich with Gateway's
        // response time data
        List<BarrelStats> barrelStatsList = new ArrayList<>();
        int totalPages = 0;

        try {
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();
            for (IBarrel barrel : activeBarrels) {
                try {
                    BarrelStats bs = barrel.getStats();

                    // Enrich with actual response time from Gateway's tracking
                    int barrelId = System.identityHashCode(barrel);
                    List<Long> responseTimes = barrelResponseTimes.get(barrelId);

                    if (responseTimes != null && !responseTimes.isEmpty()) {
                        double avgResponseTime = responseTimes.stream()
                                .mapToLong(Long::longValue)
                                .average()
                                .orElse(0.0);
                        // Create new BarrelStats with actual response time
                        bs = new BarrelStats(bs.getBarrelId(), bs.getIndexSize(), avgResponseTime);
                    }

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
        // Add URL queue size
        try {
            stats.setUrlQueueSize(urlQueue.size());
        } catch (Exception e) {
            // ignore
        }

        return stats;
    }

    /**
     * Seleciona um barrel usando estratégia de menor tempo de resposta
     * Falls back to round-robin if no response time data available
     */
    private synchronized IBarrel selectBarrel() {
        try {
            // Get barrels from registry (not the old list)
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();

            if (activeBarrels.isEmpty()) {
                return null;
            }

            // Filter out barrels that are not ready (still syncing)
            List<IBarrel> readyBarrels = new ArrayList<>();
            for (IBarrel barrel : activeBarrels) {
                try {
                    if (barrel.isReady()) {
                        readyBarrels.add(barrel);
                    } else {
                        System.out.println("[Gateway] Barrel not ready (syncing), skipping...");
                    }
                } catch (RemoteException e) {
                    // If we can't check readiness, skip this barrel
                    System.err.println("[Gateway] Error checking barrel readiness: " + e.getMessage());
                }
            }

            if (readyBarrels.isEmpty()) {
                System.out.println("[Gateway] No ready barrels available (all syncing)");
                return null;
            }

            // If only one ready barrel, return it
            if (readyBarrels.size() == 1) {
                return readyBarrels.get(0);
            }

            // Response-time based selection: choose barrel with lowest average response
            // time
            IBarrel fastestBarrel = null;
            double lowestAvgResponseTime = Double.MAX_VALUE;

            for (IBarrel barrel : readyBarrels) {
                int barrelId = System.identityHashCode(barrel);
                List<Long> responseTimes = barrelResponseTimes.get(barrelId);

                if (responseTimes == null || responseTimes.isEmpty()) {
                    // No data for this barrel yet - give it a chance (assume it's fast)
                    // This ensures new barrels get tried
                    if (fastestBarrel == null) {
                        fastestBarrel = barrel;
                        lowestAvgResponseTime = 0; // Optimistic: assume it's very fast
                    }
                } else {
                    // Calculate average response time
                    double avgResponseTime = responseTimes.stream()
                            .mapToLong(Long::longValue)
                            .average()
                            .getAsDouble();
                    if (avgResponseTime < lowestAvgResponseTime) {
                        lowestAvgResponseTime = avgResponseTime;
                        fastestBarrel = barrel;
                    }
                }
            }

            // Fallback to round-robin if somehow no barrel was selected
            if (fastestBarrel == null) {
                fastestBarrel = readyBarrels.get(currentBarrelIndex % readyBarrels.size());
                currentBarrelIndex = (currentBarrelIndex + 1) % readyBarrels.size();
            }

            return fastestBarrel;
        } catch (Exception e) {
            System.err.println("Error selecting barrel: " + e.getMessage());
            return null;
        }
    }

    /**
     * Records the response time for a barrel and maintains a sliding window
     * of recent response times for load balancing decisions
     */
    private void recordBarrelResponseTime(IBarrel barrel, long responseTimeMs) {
        int barrelId = System.identityHashCode(barrel);

        barrelResponseTimes.compute(barrelId, (k, times) -> {
            if (times == null) {
                times = new ArrayList<>();
            }

            times.add(responseTimeMs);

            // Keep only the last MAX_RESPONSE_SAMPLES measurements
            if (times.size() > MAX_RESPONSE_SAMPLES) {
                times.remove(0); // Remove oldest
            }

            return times;
        });
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
    public void addDiscoveredURL(String url, boolean highPriority) throws RemoteException {
        if (highPriority) {
            // User request: always add for reindexing, even if already visited
            urlQueue.addURL(url, true);
            // Mark as visited (idempotent operation)
            markURLAsVisited(url);
            System.out.println("[Gateway] Added high-priority URL to queue: " + url);
        } else {
            // Crawled discovery: only add if not previously visited
            if (tryMarkAsVisited(url)) {
                urlQueue.addURL(url, false);
                // System.out.println("[Gateway] Added crawled URL to queue: " + url);
                try {
                    notifyStatsListeners(getSystemStats());
                } catch (Exception e) {
                    // ignore
                }
            } else {
                // System.out.println("[Gateway] Discarded already visited URL: " + url);
            }
        }
    }

    @Override
    public void registerStatsListener(common.IStatsListener listener) throws RemoteException {
        if (listener == null)
            return;
        statsListeners.add(listener);
        // Send initial snapshot
        try {
            listener.updateStats(getSystemStats());
        } catch (Exception e) {
            System.err.println("Failed to deliver initial stats to listener: " + e.getMessage());
        }
    }

    @Override
    public void unregisterStatsListener(common.IStatsListener listener) throws RemoteException {
        if (listener == null)
            return;
        statsListeners.remove(listener);
    }

    /**
     * Push the provided stats snapshot to all registered listeners.
     */
    private void notifyStatsListeners(SystemStats stats) {
        // Only send if snapshot changed to ensure clients are updated only on
        // real changes (no periodic refreshes).
        String snapshot = stats.toString();
        if (snapshot.equals(lastStatsSnapshotString)) {
            return; // nothing changed
        }
        lastStatsSnapshotString = snapshot;

        // Copy to avoid concurrent modification while iterating
        List<common.IStatsListener> copy = new ArrayList<>(statsListeners);
        for (common.IStatsListener l : copy) {
            try {
                l.updateStats(stats);
            } catch (RemoteException re) {
                System.err.println("Removing unreachable stats listener: " + re.getMessage());
                statsListeners.remove(l);
            } catch (Exception e) {
                System.err.println("Error sending stats to listener: " + e.getMessage());
            }
        }
    }

    @Override
    public String getURLQueueStatus() throws RemoteException {
        return urlQueue.getQueueAsString();
    }

    @Override
    public boolean isURLVisited(String url) throws RemoteException {
        // Check recent cache first (exact match, no false positives)
        if (recentVisitedURLs.containsKey(url)) {
            return true;
        }

        // Check Bloom filter (can have false positives)
        return visitedURLsBloom.mightContain(url);
    }

    @Override
    public boolean markURLAsVisited(String url) throws RemoteException {
        // Add to recent cache
        recentVisitedURLs.put(url, Boolean.TRUE);

        // Add to Bloom filter
        visitedURLsBloom.put(url);

        // Return true to indicate URL was marked (always succeeds)
        return true;
    }

    @Override
    public boolean tryMarkAsVisited(String url) throws RemoteException {
        // Check if already visited
        boolean wasVisited = isURLVisited(url);

        if (!wasVisited) {
            // Mark as visited if not already
            markURLAsVisited(url);
            return true; // Successfully marked (was not visited before)
        }

        return false; // Was already visited
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
                        Thread.sleep(500); // 500 milissegundos
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
                        // Push periodic stats snapshot to registered listeners
                        try {
                            gateway.notifyStatsListeners(gateway.getSystemStats());
                        } catch (Exception e) {
                            // Ignore notification errors from status thread
                        }
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
