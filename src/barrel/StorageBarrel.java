package barrel;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.*;
import java.nio.file.*;

/**
 * Storage Barrel - Servidor que armazena o índice invertido
 * Implementa replicação para tolerância a falhas
 */
public class StorageBarrel extends UnicastRemoteObject implements IBarrel {

    private String barrelId;

    // Índice invertido: palavra -> conjunto de URLs
    private ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<String, Boolean>> invertedIndex;

    // Informação completa sobre cada página: URL -> PageInfo
    private ConcurrentHashMap<String, PageInfo> pageDatabase;

    // Shutdown flag to stop accepting new requests
    private volatile boolean shuttingDown = false;

    // Configuration: whether to use JSON saved index
    private boolean useJsonIndex;

    public StorageBarrel(String barrelId) throws RemoteException {
        super();
        this.barrelId = barrelId;
        this.invertedIndex = new ConcurrentHashMap<>();
        this.pageDatabase = new ConcurrentHashMap<>();

        // Load configuration
        boolean useSavedIndex = true;
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.FileInputStream fis = new java.io.FileInputStream("config/system.properties");
            props.load(fis);
            fis.close();
            useSavedIndex = Boolean.parseBoolean(props.getProperty("barrel.use.saved.index", "true"));
        } catch (Exception e) {
            System.err.println("Warning: Could not load config file for barrel. Using defaults.");
        }
        this.useJsonIndex = useSavedIndex;
        System.out.println("Storage Barrel " + barrelId + " initialized (JSON index: " + useJsonIndex + ")");

        // Load existing index from disk if available
        loadIndexFromDisk();
    }

    @Override
    public void indexPage(PageInfo pageInfo) throws RemoteException {
        // Reject new indexing requests during shutdown
        if (shuttingDown) {
            throw new RemoteException("Barrel is shutting down, not accepting new requests");
        }

        String url = pageInfo.getUrl();

        System.out.println("[" + barrelId + "] Indexing: " + url);

        // Atualiza informação da página
        PageInfo existing = pageDatabase.get(url);
        if (existing != null) {
            // Merge de inlinks se a página já existia
            existing.getInlinks().addAll(pageInfo.getInlinks());
        } else {
            pageDatabase.put(url, pageInfo);
        }

        // Atualiza índice invertido - optimized with computeIfAbsent
        for (String word : pageInfo.getWords()) {
            String lowerWord = word.toLowerCase();
            invertedIndex.computeIfAbsent(lowerWord, k -> ConcurrentHashMap.newKeySet()).add(url);
        }

        // Atualiza backlinks (inlinks) das páginas referenciadas
        // Create placeholder PageInfo for pages not yet indexed to track inlinks
        for (String outlink : pageInfo.getOutlinks()) {
            PageInfo outlinkedPage = pageDatabase.computeIfAbsent(outlink, k -> new PageInfo(k));
            outlinkedPage.addInlink(url);
        }
    }

    @Override
    public List<SearchResult> search(Set<String> terms, int page) throws RemoteException {
        Set<String> normalizedTerms = terms.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Encontra URLs que contêm todos os termos
        Set<String> resultUrls = null;

        for (String term : normalizedTerms) {
            Set<String> urlsForTerm = invertedIndex.get(term);

            if (urlsForTerm == null || urlsForTerm.isEmpty()) {
                // Se algum termo não existe, não há resultados
                return new ArrayList<>();
            }

            if (resultUrls == null) {
                resultUrls = new HashSet<>(urlsForTerm);
            } else {
                resultUrls.retainAll(urlsForTerm); // Interseção
            }
        }

        if (resultUrls == null || resultUrls.isEmpty()) {
            return new ArrayList<>();
        }

        // Cria resultados e ordena por relevância (número de inlinks)
        List<SearchResult> results = resultUrls.stream()
                .map(url -> {
                    PageInfo pageInfo = pageDatabase.get(url);
                    return new SearchResult(
                            url,
                            pageInfo.getTitle() != null ? pageInfo.getTitle() : url,
                            pageInfo.getTextSnippet() != null ? pageInfo.getTextSnippet() : "",
                            pageInfo.getInlinkCount());
                })
                .sorted((r1, r2) -> Integer.compare(r2.getRelevance(), r1.getRelevance()))
                .collect(Collectors.toList());

        // Paginação (10 resultados por página)
        int startIdx = page * 10;
        int endIdx = Math.min(startIdx + 10, results.size());

        if (startIdx >= results.size()) {
            return new ArrayList<>();
        }

        // Create a new ArrayList from subList to ensure RMI serialization works
        return new ArrayList<>(results.subList(startIdx, endIdx));
    }

    @Override
    public List<String> getInlinks(String url) throws RemoteException {
        PageInfo pageInfo = pageDatabase.get(url);
        if (pageInfo == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(pageInfo.getInlinks());
    }

    @Override
    public BarrelStats getStats() throws RemoteException {
        // Response time is now managed by Gateway
        return new BarrelStats(barrelId, pageDatabase.size(), 0.0);
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    @Override
    public int getIndexSize() throws RemoteException {
        return pageDatabase.size();
    }

    /**
     * Saves the index to disk using JSON format (simple and reliable)
     */
    private void saveIndexToDisk() {
        if (!useJsonIndex) {
            System.out.println("[" + barrelId + "] JSON index disabled, skipping save to disk");
            return;
        }

        String filename = "data/barrel_" + barrelId + "_index.json";
        System.out.println("[" + barrelId + "] Saving index to " + filename + "...");

        try {
            // Ensure data directory exists
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            // Create snapshot in synchronized block
            Map<String, Set<String>> invertedIndexSnapshot;
            Map<String, PageInfo> pageDatabaseSnapshot;

            synchronized (this) {
                invertedIndexSnapshot = new HashMap<>();
                for (Map.Entry<String, ConcurrentHashMap.KeySetView<String, Boolean>> entry : invertedIndex
                        .entrySet()) {
                    invertedIndexSnapshot.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
                pageDatabaseSnapshot = new HashMap<>(pageDatabase);
            }

            // Create JSON data structure
            Map<String, Object> jsonData = new HashMap<>();
            jsonData.put("barrelId", barrelId);
            jsonData.put("invertedIndex", invertedIndexSnapshot);
            jsonData.put("pageDatabase", pageDatabaseSnapshot);

            // Write to file using Gson
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create();

            try (java.io.FileWriter writer = new java.io.FileWriter(filename)) {
                gson.toJson(jsonData, writer);
            }

            System.out.println("[" + barrelId + "] Index saved successfully (" +
                    pageDatabase.size() + " pages, " +
                    invertedIndex.size() + " terms)");
        } catch (Exception e) {
            System.err.println("[" + barrelId + "] Failed to save index: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads the index from disk using JSON format
     */
    private void loadIndexFromDisk() {
        if (!useJsonIndex) {
            System.out.println("[" + barrelId + "] JSON index disabled, skipping load from disk");
            return;
        }

        String filename = "data/barrel_" + barrelId + "_index.json";
        File indexFile = new File(filename);

        if (!indexFile.exists()) {
            System.out.println("[" + barrelId + "] No saved index found, starting fresh");
            return;
        }

        System.out.println("[" + barrelId + "] Loading index from " + filename + "...");

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();

            try (java.io.FileReader reader = new java.io.FileReader(filename)) {
                // Read JSON
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonData = gson.fromJson(reader, Map.class);

                // Restore inverted index
                @SuppressWarnings("unchecked")
                Map<String, List<String>> invertedIndexData = (Map<String, List<String>>) jsonData.get("invertedIndex");

                invertedIndex.clear();
                if (invertedIndexData != null) {
                    for (Map.Entry<String, List<String>> entry : invertedIndexData.entrySet()) {
                        ConcurrentHashMap.KeySetView<String, Boolean> keySet = ConcurrentHashMap.newKeySet();
                        keySet.addAll(entry.getValue());
                        invertedIndex.put(entry.getKey(), keySet);
                    }
                }

                // Restore page database
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> pageDatabaseData = (Map<String, Map<String, Object>>) jsonData
                        .get("pageDatabase");

                pageDatabase.clear();
                if (pageDatabaseData != null) {
                    for (Map.Entry<String, Map<String, Object>> entry : pageDatabaseData.entrySet()) {
                        Map<String, Object> pageData = entry.getValue();
                        PageInfo pageInfo = new PageInfo(entry.getKey());

                        if (pageData.get("title") != null) {
                            pageInfo.setTitle((String) pageData.get("title"));
                        }
                        if (pageData.get("textSnippet") != null) {
                            pageInfo.setTextSnippet((String) pageData.get("textSnippet"));
                        }
                        // Note: inlinkCount will be recalculated when pages are re-indexed

                        pageDatabase.put(entry.getKey(), pageInfo);
                    }
                }

                System.out.println("[" + barrelId + "] Index loaded successfully (" +
                        pageDatabase.size() + " pages, " +
                        invertedIndex.size() + " terms)");

            }
        } catch (Exception e) {
            System.err.println("[" + barrelId + "] Failed to load index: " + e.getMessage());
            e.printStackTrace();
            System.out.println("[" + barrelId + "] Starting with empty index");
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java barrel.StorageBarrel <barrel-id>");
            System.exit(1);
        }

        try {
            String barrelId = args[0];
            StorageBarrel barrel = new StorageBarrel(barrelId);

            // Don't create registry - Gateway should have already created it
            // Just use the existing registry
            java.rmi.Naming.rebind("rmi://localhost:1099/Barrel" + barrelId, barrel);

            System.out.println("Storage Barrel " + barrelId + " bound to RMI registry");

            // Register with BarrelRegistry for multicast support - retry until successful
            // and keep monitoring connection health
            final boolean[] keepRunning = { true };
            Thread registrationThread = new Thread(() -> {
                System.out.println("Starting BarrelRegistry connection manager...");
                while (keepRunning[0]) {
                    boolean registered = false;

                    // Phase 1: Initial registration (retry until successful)
                    System.out.println("Attempting to register with BarrelRegistry...");
                    while (!registered && keepRunning[0]) {
                        try {
                            // Get fresh registry reference each time
                            IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming
                                    .lookup("rmi://localhost:1099/BarrelRegistry");
                            registry.registerBarrel(barrelId, barrel);
                            registered = true;
                            System.out.println("Storage Barrel " + barrelId + " registered with BarrelRegistry");
                            System.out.println("Starting health monitoring...");
                        } catch (Exception e) {
                            System.err.println("Connection attempt to Gateway failed. Retrying in 3 seconds...");
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }

                    // Phase 2: Health monitoring (ping registry periodically)
                    System.out.println("Entering health monitoring loop...");
                    while (registered && keepRunning[0]) {
                        try {
                            Thread.sleep(5000); // Check every 5 seconds
                            // Get fresh registry reference for each ping
                            IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming
                                    .lookup("rmi://localhost:1099/BarrelRegistry");
                            registry.getBarrelCount(); // Ping to check if still alive
                            // If we get here, connection is still good
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            System.out.println("Health monitoring interrupted, exiting...");
                            return;
                        } catch (Exception e) {
                            // Gateway down - connection lost
                            System.err.println("Lost connection to BarrelRegistry! Gateway may have shutdown.");
                            System.err.println("Error: " + e.getMessage());
                            System.err.println("Will attempt to re-register...");
                            registered = false;
                            break; // Exit health monitoring, go back to registration phase
                        }
                    }
                    System.out.println("Exited health monitoring loop. registered=" + registered + ", keepRunning="
                            + keepRunning[0]);
                }
            });
            registrationThread.setDaemon(false);
            registrationThread.start();

            System.out.println("Storage Barrel " + barrelId + " ready");

            // Add shutdown hook to unregister from registry
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // Flush output streams to avoid Maven/Jansi issues
                    System.out.flush();
                    System.err.flush();

                    System.out.println("\n\nShutting down Barrel " + barrelId + "...");

                    // FIRST: Stop accepting new indexing requests
                    barrel.shuttingDown = true;
                    keepRunning[0] = false; // Stop registration thread

                    // Give a moment for in-flight requests to complete
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // THEN: Save index to disk with timeout
                    Thread saveThread = new Thread(() -> {
                        try {
                            barrel.saveIndexToDisk();
                        } catch (Exception e) {
                            System.err.println("[" + barrelId + "] Error saving index: " + e.getMessage());
                        }
                    });

                    saveThread.start();

                    try {
                        // Wait up to 10 seconds for save to complete
                        saveThread.join(10000);

                        if (saveThread.isAlive()) {
                            System.err.println("[" + barrelId + "] Save operation timed out after 10 seconds!");
                            System.err.println("[" + barrelId + "] Forcing shutdown without waiting...");
                            saveThread.interrupt();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    try {
                        IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming
                                .lookup("rmi://localhost:1099/BarrelRegistry");
                        registry.unregisterBarrel(barrelId);
                        System.out.println("Unregistered from BarrelRegistry");
                    } catch (Exception e) {
                        // Ignore errors during shutdown
                    }

                    try {
                        // Unbind from RMI registry
                        java.rmi.Naming.unbind("rmi://localhost:1099/Barrel" + barrelId);
                        System.out.println("Unbound from RMI registry");
                    } catch (Exception e) {
                        // Ignore errors during shutdown
                    }

                    try {
                        // Unexport the barrel
                        UnicastRemoteObject.unexportObject(barrel, true);
                        System.out.println("Barrel unexported");
                    } catch (Exception e) {
                        // Ignore errors during shutdown
                    }

                    System.out.println("Barrel " + barrelId + " shutdown complete. Goodbye!");
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
            System.err.println("Barrel exception:");
            e.printStackTrace();
        }
    }
}
