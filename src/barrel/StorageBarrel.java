package barrel;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.io.*;

/**
 * Storage Barrel - Servidor que armazena o índice invertido
 * Implementa replicação para tolerância a falhas
 */
public class StorageBarrel extends UnicastRemoteObject implements IBarrel {

    private String barrelId;

    // Document ID system for URL storage optimization
    // URL -> Document ID
    private ConcurrentHashMap<String, Integer> urlToDocId;
    // Document ID -> URL
    private ConcurrentHashMap<Integer, String> docIdToUrl;
    // Next available document ID
    private AtomicInteger nextDocId;

    // Índice invertido: palavra -> conjunto de Document IDs (instead of URLs)
    private ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Integer, Boolean>> invertedIndex;

    // Informação completa sobre cada página: Document ID -> PageInfo
    private ConcurrentHashMap<Integer, PageInfo> pageDatabase;

    // Shutdown flag to stop accepting new requests
    private volatile boolean shuttingDown = false;

    // Configuration: whether to use JSON saved index
    private boolean useJsonIndex;

    // Stopwords for filtering search queries
    private Set<String> stopwords;

    public StorageBarrel(String barrelId) throws RemoteException {
        super();
        this.barrelId = barrelId;
        this.urlToDocId = new ConcurrentHashMap<>();
        this.docIdToUrl = new ConcurrentHashMap<>();
        this.nextDocId = new java.util.concurrent.atomic.AtomicInteger(1);
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

        // Load stopwords
        this.stopwords = TextUtils.loadStopwords(barrelId);
        System.out.println("[" + barrelId + "] Loaded " + stopwords.size() + " stopwords");

        // Load existing index from disk if available
        loadIndexFromDisk();
    }

    /**
     * Gets or creates a document ID for a URL
     */
    private int getOrCreateDocId(String url) {
        return urlToDocId.computeIfAbsent(url, k -> {
            int docId = nextDocId.getAndIncrement();
            docIdToUrl.put(docId, url);
            return docId;
        });
    }

    /**
     * Gets the URL for a document ID
     */
    private String getUrlForDocId(int docId) {
        return docIdToUrl.get(docId);
    }

    @Override
    public void indexPage(PageInfo pageInfo) throws RemoteException {
        // Reject new indexing requests during shutdown
        if (shuttingDown) {
            throw new RemoteException("Barrel is shutting down, not accepting new requests");
        }

        String url = pageInfo.getUrl();
        int docId = getOrCreateDocId(url);

        System.out.println("[" + barrelId + "] Indexing: " + url + " (docId=" + docId + ")");

        // Convert URL outlinks to document IDs (from Downloader)
        int outlinkCount = 0;
        for (String outlinkUrl : pageInfo.getOutlinkUrls()) {
            int outlinkDocId = getOrCreateDocId(outlinkUrl);
            pageInfo.addOutlink(outlinkDocId);
            outlinkCount++;
        }

        // Clear outlinkUrls after conversion - no longer needed, saves memory
        pageInfo.getOutlinkUrls().clear();

        System.out.println("[" + barrelId + "] [DEBUG] Converted " + outlinkCount + " outlinks to docIds. " +
                "PageInfo now has: words=" + pageInfo.getWords().size() +
                " outlinks=" + pageInfo.getOutlinks().size());

        // Atualiza informação da página usando document ID
        PageInfo existing = pageDatabase.get(docId);
        if (existing != null) {
            // Update existing page with new content
            existing.setTitle(pageInfo.getTitle());
            existing.setTextSnippet(pageInfo.getTextSnippet());
            existing.setWords(pageInfo.getWords());
            existing.setTermFrequencies(pageInfo.getTermFrequencies()); // Copy term frequencies!
            // Merge inlinks and outlinks
            existing.getInlinks().addAll(pageInfo.getInlinks());
            existing.getOutlinks().addAll(pageInfo.getOutlinks());
        } else {
            pageDatabase.put(docId, pageInfo);
        }

        // Debug: Check if term frequencies were received
        int tfSize = (pageInfo.getTermFrequencies() != null) ? pageInfo.getTermFrequencies().size() : 0;
        if (tfSize == 0) {
            System.out.println("[" + barrelId + "] [WARNING] PageInfo has no term frequencies for: " + url);
        } else if (docId <= 3) { // Log first 3 pages
            System.out.println(
                    "[" + barrelId + "] [DEBUG] Indexed docId=" + docId + " with " + tfSize + " term frequencies");
        }

        // Atualiza índice invertido usando document IDs
        for (String word : pageInfo.getWords()) {
            String lowerWord = word.toLowerCase();
            invertedIndex.computeIfAbsent(lowerWord, k -> ConcurrentHashMap.newKeySet()).add(docId);
        }

        // Atualiza backlinks (inlinks) das páginas referenciadas
        // Create placeholder PageInfo for pages not yet indexed to track inlinks
        for (Integer outlinkDocId : pageInfo.getOutlinks()) {
            String outlinkUrl = getUrlForDocId(outlinkDocId);
            PageInfo outlinkedPage = pageDatabase.computeIfAbsent(outlinkDocId, k -> new PageInfo(outlinkUrl));
            outlinkedPage.addInlink(docId);
        }
    }

    @Override
    public List<SearchResult> search(Set<String> terms, int page) throws RemoteException {
        // Normalize and filter stopwords from search terms
        Set<String> normalizedTerms = terms.stream()
                .map(String::toLowerCase)
                .filter(term -> !stopwords.contains(term)) // Remove stopwords
                .collect(Collectors.toSet());

        // If all terms were stopwords, return empty results
        if (normalizedTerms.isEmpty()) {
            System.out.println("[" + barrelId + "] Search query contained only stopwords");
            return new ArrayList<>();
        }

        // Encontra Document IDs que contêm todos os termos
        Set<Integer> resultDocIds = null;

        for (String term : normalizedTerms) {
            Set<Integer> docIdsForTerm = invertedIndex.get(term);

            if (docIdsForTerm == null || docIdsForTerm.isEmpty()) {
                // Se algum termo não existe, não há resultados
                return new ArrayList<>();
            }

            if (resultDocIds == null) {
                resultDocIds = new HashSet<>(docIdsForTerm);
            } else {
                resultDocIds.retainAll(docIdsForTerm); // Interseção
            }
        }

        if (resultDocIds == null || resultDocIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Total documents for IDF calculation
        int totalDocs = pageDatabase.size();

        // Calculate TF-IDF score for each result
        List<SearchResult> results = resultDocIds.stream()
                .map(docId -> {
                    String url = getUrlForDocId(docId);
                    PageInfo pageInfo = pageDatabase.get(docId);

                    // Calculate TF-IDF score for this document
                    double tfIdfScore = calculateTfIdfScore(pageInfo, normalizedTerms, totalDocs);

                    return new SearchResult(
                            url,
                            pageInfo.getTitle() != null ? pageInfo.getTitle() : url,
                            pageInfo.getTextSnippet() != null ? pageInfo.getTextSnippet() : "",
                            pageInfo.getInlinkCount(),
                            tfIdfScore);
                })
                // Sort by TF-IDF score (descending), then by inlink count (tie-breaker)
                .sorted((r1, r2) -> {
                    int scoreCompare = Double.compare(r2.getTfIdfScore(), r1.getTfIdfScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    // Tie-breaker: use inlink count
                    return Integer.compare(r2.getRelevance(), r1.getRelevance());
                })
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

    /**
     * Calculate TF-IDF score for a document given search terms
     * 
     * @param pageInfo  The document
     * @param terms     Search terms
     * @param totalDocs Total number of documents in corpus
     * @return TF-IDF score (sum of TF-IDF for each term)
     */
    private double calculateTfIdfScore(PageInfo pageInfo, Set<String> terms, int totalDocs) {
        double score = 0.0;

        for (String term : terms) {
            // Get term frequency from PageInfo (calculated by Downloader)
            int termFreq = pageInfo.getTermFrequency(term);

            if (termFreq == 0) {
                continue; // Term not in document
            }

            // TF (normalized): frequency / max frequency in document
            // Using raw frequency is simpler and works well
            double tf = termFreq;

            // IDF: log(total docs / docs containing term)
            Set<Integer> docsWithTerm = invertedIndex.get(term);
            int docsContainingTerm = (docsWithTerm != null) ? docsWithTerm.size() : 1;
            double idf = Math.log((double) totalDocs / docsContainingTerm);

            // TF-IDF for this term
            score += tf * idf;
        }

        return score;
    }

    @Override
    public List<String> getInlinks(String url) throws RemoteException {
        // Convert URL to document ID
        Integer docId = urlToDocId.get(url);
        if (docId == null) {
            // URL not indexed
            return new ArrayList<>();
        }

        PageInfo pageInfo = pageDatabase.get(docId);
        if (pageInfo == null) {
            return new ArrayList<>();
        }

        // Convert inlink docIds back to URLs for the API
        return pageInfo.getInlinks().stream()
                .map(this::getUrlForDocId)
                .collect(Collectors.toList());
    }

    /**
     * Load stopwords from data/stopwords-en.txt and data/stopwords-pt.txt
     */
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
            Map<String, Set<Integer>> invertedIndexSnapshot;
            Map<Integer, PageInfo> pageDatabaseSnapshot;
            Map<String, Integer> urlToDocIdSnapshot;
            Map<Integer, String> docIdToUrlSnapshot;
            int nextDocIdSnapshot;

            synchronized (this) {
                invertedIndexSnapshot = new HashMap<>();
                for (Map.Entry<String, ConcurrentHashMap.KeySetView<Integer, Boolean>> entry : invertedIndex
                        .entrySet()) {
                    invertedIndexSnapshot.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
                pageDatabaseSnapshot = new HashMap<>(pageDatabase);
                urlToDocIdSnapshot = new HashMap<>(urlToDocId);
                docIdToUrlSnapshot = new HashMap<>(docIdToUrl);
                nextDocIdSnapshot = nextDocId.get();

                // Debug: Check if PageInfo objects have data including term frequencies
                for (Map.Entry<Integer, PageInfo> entry : pageDatabase.entrySet()) {
                    PageInfo pi = entry.getValue();
                    if (entry.getKey() <= 3) { // Log first 3 pages
                        int tfSize = (pi.getTermFrequencies() != null) ? pi.getTermFrequencies().size() : 0;
                        System.out.println("[" + barrelId + "] [DEBUG] Saving PageInfo docId=" + entry.getKey() +
                                " words=" + pi.getWords().size() +
                                " termFreqs=" + tfSize +
                                " inlinks=" + pi.getInlinks().size() +
                                " outlinks=" + pi.getOutlinks().size());
                    }
                }
            }

            // Create JSON data structure
            Map<String, Object> jsonData = new HashMap<>();
            jsonData.put("barrelId", barrelId);
            jsonData.put("invertedIndex", invertedIndexSnapshot);
            jsonData.put("pageDatabase", pageDatabaseSnapshot);
            jsonData.put("urlToDocId", urlToDocIdSnapshot);
            jsonData.put("docIdToUrl", docIdToUrlSnapshot);
            jsonData.put("nextDocId", nextDocIdSnapshot);

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

                // Restore URL to docId mappings
                @SuppressWarnings("unchecked")
                Map<String, Number> urlToDocIdData = (Map<String, Number>) jsonData.get("urlToDocId");

                urlToDocId.clear();
                if (urlToDocIdData != null) {
                    for (Map.Entry<String, Number> entry : urlToDocIdData.entrySet()) {
                        urlToDocId.put(entry.getKey(), entry.getValue().intValue());
                    }
                }

                // Restore docId to URL mappings
                @SuppressWarnings("unchecked")
                Map<String, String> docIdToUrlData = (Map<String, String>) jsonData.get("docIdToUrl");

                docIdToUrl.clear();
                if (docIdToUrlData != null) {
                    for (Map.Entry<String, String> entry : docIdToUrlData.entrySet()) {
                        // JSON keys are always strings, convert back to Integer
                        docIdToUrl.put(Integer.parseInt(entry.getKey()), entry.getValue());
                    }
                }

                // Restore nextDocId counter
                Number nextDocIdValue = (Number) jsonData.get("nextDocId");
                if (nextDocIdValue != null) {
                    nextDocId.set(nextDocIdValue.intValue());
                }

                // Restore inverted index (now with Integer docIds)
                @SuppressWarnings("unchecked")
                Map<String, List<Number>> invertedIndexData = (Map<String, List<Number>>) jsonData.get("invertedIndex");

                invertedIndex.clear();
                if (invertedIndexData != null) {
                    for (Map.Entry<String, List<Number>> entry : invertedIndexData.entrySet()) {
                        ConcurrentHashMap.KeySetView<Integer, Boolean> keySet = ConcurrentHashMap.newKeySet();
                        for (Number docId : entry.getValue()) {
                            keySet.add(docId.intValue());
                        }
                        invertedIndex.put(entry.getKey(), keySet);
                    }
                }

                // Restore page database (now keyed by Integer docId)
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> pageDatabaseData = (Map<String, Map<String, Object>>) jsonData
                        .get("pageDatabase");

                pageDatabase.clear();
                if (pageDatabaseData != null) {
                    for (Map.Entry<String, Map<String, Object>> entry : pageDatabaseData.entrySet()) {
                        // JSON keys are always strings, convert back to Integer
                        int docId = Integer.parseInt(entry.getKey());
                        String url = getUrlForDocId(docId);

                        Map<String, Object> pageData = entry.getValue();
                        PageInfo pageInfo = new PageInfo(url);

                        if (pageData.get("title") != null) {
                            pageInfo.setTitle((String) pageData.get("title"));
                        }
                        if (pageData.get("textSnippet") != null) {
                            pageInfo.setTextSnippet((String) pageData.get("textSnippet"));
                        }

                        // Restore words (Set<String>)
                        if (pageData.get("words") != null) {
                            @SuppressWarnings("unchecked")
                            List<String> wordsData = (List<String>) pageData.get("words");
                            for (String word : wordsData) {
                                pageInfo.addWord(word);
                            }
                        }

                        // Restore inlinks (Set<Integer>)
                        if (pageData.get("inlinks") != null) {
                            @SuppressWarnings("unchecked")
                            List<Number> inlinksData = (List<Number>) pageData.get("inlinks");
                            for (Number inlinkDocId : inlinksData) {
                                pageInfo.addInlink(inlinkDocId.intValue());
                            }
                        }

                        // Restore outlinks (Set<Integer>)
                        if (pageData.get("outlinks") != null) {
                            @SuppressWarnings("unchecked")
                            List<Number> outlinksData = (List<Number>) pageData.get("outlinks");
                            for (Number outlinkDocId : outlinksData) {
                                pageInfo.addOutlink(outlinkDocId.intValue());
                            }
                        }

                        pageDatabase.put(docId, pageInfo);
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

            // Wait for Gateway to be available before binding
            System.out.println("Waiting for Gateway to be available...");
            boolean gatewayAvailable = false;

            while (!gatewayAvailable) {
                try {
                    // Try to lookup the Gateway to check if RMI registry exists
                    java.rmi.Naming.lookup("rmi://localhost:1099/Gateway");
                    gatewayAvailable = true;
                    System.out.println("Gateway detected. Proceeding with registration...");
                } catch (Exception e) {
                    System.out.println("Gateway not ready yet. Waiting 3 seconds...");
                    Thread.sleep(3000);
                }
            }

            // Now bind to RMI registry (Gateway is running)
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
