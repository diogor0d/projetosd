package downloader;

import common.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/**
 * Downloader - Web Crawler que obtém e processa páginas
 * Múltiplos downloaders trabalham em paralelo
 */
public class Downloader implements Runnable {

    private String downloaderId;
    private IGateway gateway; // Use Gateway via RMI instead of direct objects
    private List<IBarrel> barrels;
    private volatile boolean running; // volatile for thread visibility
    private boolean debugMode; // Debug mode slows down processing

    // Multi-threading support
    private ThreadPoolExecutor threadPool;
    private static int WORKER_THREADS = 5;
    // Semaphore to reserve processing slots (threads + queue capacity)
    private Semaphore capacitySemaphore;

    private static int MAX_DEPTH = 3; // Profundidade máxima de crawling
    private static int TIMEOUT = 5000; // Timeout para conexões (ms)
    private static int DEBUG_DELAY_MS = 1500; // Delay in debug mode

    // Constructor for RMI-based downloader (recommended)
    public Downloader(String downloaderId, IGateway gateway, boolean debugMode) {
        this.downloaderId = downloaderId;
        this.gateway = gateway;
        this.barrels = new ArrayList<>();
        this.running = true;
        this.debugMode = debugMode;

        // Create thread pool with named threads
        ThreadFactory namedThreadFactory = new ThreadFactory() {
            private int threadCount = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(downloaderId + "-Worker-" + (++threadCount));
                return thread;
            }
        };

        this.threadPool = new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(WORKER_THREADS), namedThreadFactory);
        // Initialize capacity semaphore: pool threads + queue capacity
        this.capacitySemaphore = new Semaphore(WORKER_THREADS + WORKER_THREADS);

        if (debugMode) {
            System.out.println("[" + downloaderId + "] DEBUG MODE ENABLED - Processing will be slowed down");
        }

        System.out.println("[" + downloaderId + "] Initialized with " + WORKER_THREADS + " worker threads");
    }

    // Constructor without debug mode (defaults to false)
    public Downloader(String downloaderId, IGateway gateway) {
        this(downloaderId, gateway, false);
    }

    public void addBarrel(IBarrel barrel) {
        barrels.add(barrel);
    }

    @Override
    public void run() {
        System.out.println("[" + downloaderId + "] Downloader started with " + WORKER_THREADS + " worker threads");

        while (running) {
            try {
                // Verifica se o gateway está disponível antes de tentar usar
                if (gateway == null) {
                    System.err.println("[" + downloaderId + "] Failed to connect to gateway. Waiting to try again...");
                    Thread.sleep(3000);
                    continue;
                }

                // Reserve a processing slot (blocks until a thread/queue slot available)
                capacitySemaphore.acquire();

                // Bloqueia até obter próximo URL via Gateway RMI (sem busy waiting)
                String url = gateway.getNextURL(); // Bloqueia até ter URL disponível
                System.out.println("[" + downloaderId + "] Fetched URL from queue: " + url);

                // Submit task to thread pool (blocking enqueue if pool is full)
                submitBlocking(() -> {
                    try {
                        // Process URL directly (no visited URL tracking)
                        processURL(url);
                    } catch (Exception e) {
                        System.err.println(
                                "[" + downloaderId + "] Worker thread error for " + url + ": " + e.getMessage());
                    }
                });

            } catch (java.rmi.RemoteException e) {
                // Gateway offline ou problema de rede - exit to trigger reconnection
                System.err.println("[" + downloaderId + "] Gateway connection error: " + e.getMessage());
                System.err.println("[" + downloaderId + "] Gateway appears to be down. Exiting to reconnect...");
                break; // Exit run loop to trigger reconnection in main()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[" + downloaderId + "] Error: " + e.getMessage());
            }
        }

        // Proper cleanup
        shutdown();
        System.out.println("[" + downloaderId + "] Downloader stopped");
    }

    /**
     * Graceful shutdown of thread pool
     */
    private void shutdown() {
        System.out.println("[" + downloaderId + "] Shutting down thread pool...");

        threadPool.shutdown(); // Stop accepting new tasks

        try {
            // Wait for existing tasks to complete (with timeout)
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                System.err.println("[" + downloaderId + "] Thread pool did not terminate in time, forcing shutdown...");
                threadPool.shutdownNow(); // Force shutdown

                // Wait again for tasks to respond to cancellation
                if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("[" + downloaderId + "] Thread pool did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            System.err.println("[" + downloaderId + "] Shutdown interrupted, forcing shutdown...");
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[" + downloaderId + "] Thread pool shutdown complete");
    }

    /**
     * Submit a task to the thread pool, blocking by placing the runnable in the
     * executor queue if the pool is saturated. This avoids
     * RejectedExecutionException
     * and does not busy-wait or sleep.
     */
    private void submitBlocking(Runnable task) {
        // Wrap the task so we release the semaphore permit when it finishes
        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                capacitySemaphore.release();
            }
        };

        try {
            // Try to hand off to executor first
            threadPool.execute(wrapped);
        } catch (RejectedExecutionException rex) {
            // If rejected, blockingly enqueue into the queue. This will wait until
            // space becomes available. If interrupted while waiting, release the
            // permit to avoid leaks and re-interrupt.
            try {
                BlockingQueue<Runnable> q = threadPool.getQueue();
                q.put(wrapped);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                // release permit since task wasn't enqueued
                capacitySemaphore.release();
                throw new RuntimeException("Interrupted while enqueueing task", ie);
            }
        }
    }

    private void processURL(String urlStr) {
        // Get thread name for logging
        String threadName = Thread.currentThread().getName();

        try {
            System.out.println("[" + downloaderId + "][" + threadName + "] Processing: " + urlStr);

            // Debug mode: slow down processing
            if (debugMode) {
                System.out.println(
                        "[" + downloaderId + "][" + threadName + "] [DEBUG] Sleeping for " + DEBUG_DELAY_MS + "ms...");
                Thread.sleep(DEBUG_DELAY_MS);
            }

            // Obtém e analisa a página
            Document doc = Jsoup.connect(urlStr)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (compatible; GoogolBot/1.0)")
                    .get();

            PageInfo pageInfo = extractPageInfo(urlStr, doc);

            // print page info
            System.out.println("[" + downloaderId + "][" + threadName + "] Extracted PageInfo: " + pageInfo);

            // RMI MULTICAST: Send to all barrels via BarrelRegistry
            multicastToAllBarrels(pageInfo);

            // Adiciona links encontrados à fila (com prioridade normal - crawled)
            for (String link : pageInfo.getOutlinks()) {
                gateway.addDiscoveredURL(link, false); // false = prioridade normal (crawled)
            }

            // Debug mode: slow down before next URL
            if (debugMode) {
                System.out.println("[" + downloaderId + "][" + threadName
                        + "] [DEBUG] Finished processing, sleeping before next URL...");
                Thread.sleep(DEBUG_DELAY_MS);
            }

        } catch (Exception e) {
            String threadName2 = Thread.currentThread().getName();
            System.err.println(
                    "[" + downloaderId + "][" + threadName2 + "] Failed to process " + urlStr + ": " + e.getMessage());
        }
    }

    /**
     * Reliable RMI Multicast - Send PageInfo to ALL registered barrels
     * Implements retry logic for reliability
     */
    private void multicastToAllBarrels(PageInfo pageInfo) {
        try {
            // Get barrel registry
            IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup("rmi://localhost/BarrelRegistry");

            // Get all active barrels
            List<IBarrel> allBarrels = registry.getAllBarrels();

            if (allBarrels.isEmpty()) {
                System.err.println("[" + downloaderId + "] WARNING: No barrels available!");
                System.err.println("[" + downloaderId + "] Retrying in 2 seconds: " + pageInfo.getUrl());

                // Wait and retry once after 2 seconds
                Thread.sleep(2000);

                // Try to get barrels again
                allBarrels = registry.getAllBarrels();

                if (allBarrels.isEmpty()) {
                    System.err.println("[" + downloaderId + "] CRITICAL: Still no barrels available after retry!");
                    System.err.println("[" + downloaderId + "] Page data will be LOST: " + pageInfo.getUrl());
                    return;
                }

                System.out.println("[" + downloaderId + "] Barrels now available, proceeding with indexing");
            }

            // System.out.println(
            // "[" + downloaderId + "] Multicasting to " + allBarrels.size() + " barrel(s):
            // " + pageInfo.getUrl());

            // Track success/failure for each barrel
            int successCount = 0;
            int failureCount = 0;

            // Send to ALL barrels (reliable multicast via RMI)
            for (int i = 0; i < allBarrels.size(); i++) {
                IBarrel barrel = allBarrels.get(i);
                boolean indexed = false;
                int retries = 0;
                int maxRetries = 3;

                // Retry logic for reliability
                while (!indexed && retries < maxRetries) {
                    try {
                        barrel.indexPage(pageInfo);
                        indexed = true;
                        successCount++;
                        // System.out.println(
                        // "[" + downloaderId + "] Barrel " + (i + 1) + " indexed: " +
                        // pageInfo.getUrl());
                    } catch (Exception e) {
                        retries++;
                        if (retries < maxRetries) {
                            System.err.println("[" + downloaderId + "] Retry " + retries + "/" + maxRetries
                                    + " for barrel " + (i + 1));
                            Thread.sleep(500); // Wait before retry
                        } else {
                            failureCount++;
                            System.err.println("[" + downloaderId + "] Failed to index on barrel " + (i + 1)
                                    + " after " + maxRetries + " attempts");
                        }
                    }
                }
            }

            // Log multicast result
            // if (successCount == allBarrels.size()) {
            // System.out.println("[" + downloaderId + "] MULTICAST SUCCESS: All " +
            // successCount
            // + " barrels indexed " + pageInfo.getUrl());
            // } else if (successCount > 0) {
            // System.out.println("[" + downloaderId + "] PARTIAL SUCCESS: " +
            // successCount + "/" + allBarrels.size()
            // + " barrels indexed " + pageInfo.getUrl());
            // } else {
            if (successCount == 0) {
                System.err.println(
                        "[" + downloaderId + "] MULTICAST FAILED: No barrels indexed " + pageInfo.getUrl());

                // Only retry if we're not shutting down (check error messages for "shutting
                // down")
                // Wait briefly and try ONE more time with a fresh barrel list
                System.err.println("[" + downloaderId + "] Retrying in 2 seconds...");
                Thread.sleep(2000);

                // Try again with fresh barrel list
                List<IBarrel> retryBarrels = registry.getAllBarrels();

                if (!retryBarrels.isEmpty()) {
                    System.out.println("[" + downloaderId + "] Retrying with " + retryBarrels.size() + " barrel(s)");

                    for (IBarrel barrel : retryBarrels) {
                        try {
                            barrel.indexPage(pageInfo);
                            successCount++;
                            System.out.println("[" + downloaderId + "] Retry successful for: " + pageInfo.getUrl());
                            break; // Success on at least one barrel
                        } catch (Exception retryEx) {
                            // If barrel is shutting down, don't spam with errors
                            if (!retryEx.getMessage().contains("shutting down")) {
                                System.err.println("[" + downloaderId + "] Retry failed: " + retryEx.getMessage());
                            }
                        }
                    }
                }

                if (successCount == 0) {
                    System.err.println("[" + downloaderId + "] WARNING: Page data lost after retry: "
                            + pageInfo.getUrl());
                }
            } else if (successCount < allBarrels.size()) {
                System.err.println("[" + downloaderId + "] PARTIAL SUCCESS: " + successCount + "/" + allBarrels.size()
                        + " barrels indexed " + pageInfo.getUrl());
            }

        } catch (Exception e) {
            System.err.println("[" + downloaderId + "] Multicast error: " + e.getMessage());

            // Fallback to local barrel list if registry not available
            System.err.println("[" + downloaderId + "] Falling back to local barrel list");
            for (IBarrel barrel : barrels) {
                try {
                    barrel.indexPage(pageInfo);
                } catch (Exception ex) {
                    System.err.println("[" + downloaderId + "] Failed to index on fallback barrel: " + ex.getMessage());
                }
            }
        }
    }

    private PageInfo extractPageInfo(String url, Document doc) {
        PageInfo pageInfo = new PageInfo(url);

        // Título
        pageInfo.setTitle(doc.title());

        // Texto (snippet) - primeiros 200 caracteres
        String bodyText = doc.body().text();
        if (bodyText.length() > 200) {
            pageInfo.setTextSnippet(bodyText.substring(0, 200) + "...");
        } else {
            pageInfo.setTextSnippet(bodyText);
        }

        // Extrai palavras do texto
        String[] words = bodyText.toLowerCase().split("\\W+");
        for (String word : words) {
            if (!word.isEmpty()) { // Apenas ignora strings vazias
                pageInfo.addWord(word);
            }
        }

        // Extrai links
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            try {
                String absUrl = link.absUrl("href");
                if (isValidURL(absUrl)) {
                    pageInfo.addOutlink(absUrl);
                }
            } catch (Exception e) {
                // Ignora URLs inválidos
            }
        }

        return pageInfo;
    }

    private boolean isValidURL(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return scheme != null &&
                    scheme.startsWith("http") &&
                    !url.contains("#");
        } catch (Exception e) {
            return false;
        }
    }

    public void stop() {
        System.out.println("[" + downloaderId + "] Stop requested");
        running = false;
    }

    public static void main(String[] args) {
        // Parse downloader ID from command-line argument
        String downloaderId = "Downloader-1"; // Default ID
        if (args.length > 0 && !args[0].startsWith("--")) {
            // First argument is the ID (if it doesn't start with --)
            downloaderId = "Downloader-" + args[0];
        }

        // Load configuration from system.properties
        boolean debugMode = false;
        int debugDelayMs = 1500;

        try {
            java.util.Properties props = new java.util.Properties();
            java.io.FileInputStream fis = new java.io.FileInputStream("config/system.properties");
            props.load(fis);
            fis.close();

            debugMode = Boolean.parseBoolean(props.getProperty("downloader.debug.mode", "false"));
            debugDelayMs = Integer.parseInt(props.getProperty("downloader.debug.delay.ms", "1500"));

            // Load configurable values
            DEBUG_DELAY_MS = debugDelayMs;
            TIMEOUT = Integer.parseInt(props.getProperty("downloader.timeout", "5000"));
            MAX_DEPTH = Integer.parseInt(props.getProperty("downloader.max.depth", "3"));
            WORKER_THREADS = Integer.parseInt(props.getProperty("downloader.worker.threads", "5"));

            if (debugMode) {
                System.out.println("=== DEBUG MODE ENABLED (from system.properties) ===");
                System.out.println("Debug delay: " + debugDelayMs + "ms");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load config file. Using defaults.");
        }

        // Command-line argument can override config file (--debug flag)
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--debug")) {
                debugMode = true;
                System.out.println("=== DEBUG MODE ENABLED (from command-line) ===");
                break;
            }
        }

        final boolean debug = debugMode;
        final String finalDownloaderId = downloaderId;

        // Reference to current downloader for shutdown hook
        final Downloader[] currentDownloader = new Downloader[1];

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n=== Shutdown signal received ===");
            if (currentDownloader[0] != null) {
                System.out.println("Initiating graceful shutdown...");
                currentDownloader[0].stop();
                // Give it a moment to shutdown cleanly
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("=== Shutdown complete ===");
        }));

        // Continuous connection manager - handles reconnection on Gateway shutdown
        while (true) {
            IGateway gateway = null;

            // Phase 1: Connect to Gateway (retry until successful)
            System.out.println("Attempting to connect to Gateway...");
            while (gateway == null) {
                try {
                    IGateway tempGateway = (IGateway) java.rmi.Naming.lookup("rmi://localhost/Gateway");
                    // Verify connection is actually alive
                    tempGateway.getSystemStats(); // Test call to verify connectivity
                    gateway = tempGateway;
                    System.out.println("Connected to Gateway");
                } catch (Exception e) {
                    System.err.println("Connection attempt with gateway failed. Retrying in 3 seconds...");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            // Check and display registered barrels
            try {
                IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup("rmi://localhost/BarrelRegistry");
                int barrelCount = registry.getBarrelCount();
                System.out.println("Downloader initialized - discovered " + barrelCount + " barrel(s) in registry");
            } catch (Exception e) {
                System.err.println("Could not connect to BarrelRegistry: " + e.getMessage());
            }

            // Phase 2: Run downloader until Gateway connection fails
            try {
                Downloader downloader = new Downloader(finalDownloaderId, gateway, debug);
                currentDownloader[0] = downloader; // Store reference for shutdown hook

                Thread thread = new Thread(downloader);
                thread.start();

                // Wait for thread to finish (happens when Gateway connection is lost)
                thread.join();

                System.err.println(finalDownloaderId + ": Lost connection to Gateway!");
                System.err.println("Will attempt to reconnect...");

            } catch (Exception e) {
                System.err.println(finalDownloaderId + " error: " + e.getMessage());
            } finally {
                currentDownloader[0] = null; // Clear reference
            }

            // Small delay before reconnection attempt
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
