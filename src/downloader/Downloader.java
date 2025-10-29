package downloader;

import common.*;
import gateway.URLQueue;
import gateway.VisitedURLs;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.*;

/**
 * Downloader - Web Crawler que obtém e processa páginas
 * Múltiplos downloaders trabalham em paralelo
 */
public class Downloader implements Runnable {

    private String downloaderId;
    private IGateway gateway; // Use Gateway via RMI instead of direct objects
    private List<IBarrel> barrels;
    private boolean running;
    private boolean debugMode; // Debug mode slows down processing

    private static final int MAX_DEPTH = 3; // Profundidade máxima de crawling
    private static final int TIMEOUT = 5000; // Timeout para conexões (ms)
    private static final int DEBUG_DELAY_MS = 1500; // Delay in debug mode

    // Constructor for RMI-based downloader (recommended)
    public Downloader(String downloaderId, IGateway gateway, boolean debugMode) {
        this.downloaderId = downloaderId;
        this.gateway = gateway;
        this.barrels = new ArrayList<>();
        this.running = true;
        this.debugMode = debugMode;

        if (debugMode) {
            System.out.println("[" + downloaderId + "] DEBUG MODE ENABLED - Processing will be slowed down");
        }
    }

    // Constructor without debug mode (defaults to false)
    public Downloader(String downloaderId, IGateway gateway) {
        this(downloaderId, gateway, false);
    }

    // Legacy constructor for local testing (deprecated)
    @Deprecated
    public Downloader(String downloaderId, URLQueue urlQueue, VisitedURLs visitedUrls) {
        this.downloaderId = downloaderId;
        this.gateway = null; // Not used in this mode
        this.barrels = new ArrayList<>();
        this.running = true;
    }

    public void addBarrel(IBarrel barrel) {
        barrels.add(barrel);
    }

    @Override
    public void run() {
        System.out.println("[" + downloaderId + "] Downloader started");

        while (running) {
            try {
                // Verifica se o gateway está disponível antes de tentar usar
                if (gateway == null) {
                    System.err.println("[" + downloaderId + "] Failed to connect to gateway. Waiting to try again...");
                    Thread.sleep(3000);
                    continue;
                }

                // Bloqueia até obter próximo URL via Gateway RMI (sem busy waiting)
                // System.out.println("[" + downloaderId + "] Waiting for next URL...");
                String url = gateway.getNextURL(); // Bloqueia até ter URL disponível

                // System.out.println("[" + downloaderId + "] Fetched URL from queue: " + url);

                if (!gateway.isURLVisited(url)) {
                    processURL(url);
                }

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

        System.out.println("[" + downloaderId + "] Downloader stopped");
    }

    private void processURL(String urlStr) {
        try {
            System.out.println("[" + downloaderId + "] Processing: " + urlStr);

            // Debug mode: slow down processing
            if (debugMode) {
                System.out.println("[" + downloaderId + "] [DEBUG] Sleeping for " + DEBUG_DELAY_MS + "ms...");
                Thread.sleep(DEBUG_DELAY_MS);
            }

            // Marca como visitado via Gateway
            gateway.markURLAsVisited(urlStr);

            // Obtém e analisa a página
            Document doc = Jsoup.connect(urlStr)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (compatible; GoogolBot/1.0)")
                    .get();

            PageInfo pageInfo = extractPageInfo(urlStr, doc);

            // RMI MULTICAST: Send to all barrels via BarrelRegistry
            multicastToAllBarrels(pageInfo);

            // Adiciona links encontrados à fila (com prioridade normal - crawled)
            for (String link : pageInfo.getOutlinks()) {
                if (!gateway.isURLVisited(link)) {
                    gateway.addDiscoveredURL(link, false); // false = prioridade normal (crawled)
                }
            }

            // Debug mode: slow down before next URL
            if (debugMode) {
                System.out.println("[" + downloaderId + "] [DEBUG] Finished processing, sleeping before next URL...");
                Thread.sleep(DEBUG_DELAY_MS);
            }

        } catch (Exception e) {
            System.err.println("[" + downloaderId + "] Failed to process " + urlStr + ": " + e.getMessage());
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
                System.err.println("[" + downloaderId + "] WARNING: No barrels registered! Page not indexed: "
                        + pageInfo.getUrl());
                return;
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
                        // "[" + downloaderId + "] ✓ Barrel " + (i + 1) + " indexed: " +
                        // pageInfo.getUrl());
                    } catch (Exception e) {
                        retries++;
                        if (retries < maxRetries) {
                            System.err.println("[" + downloaderId + "] Retry " + retries + "/" + maxRetries
                                    + " for barrel " + (i + 1));
                            Thread.sleep(500); // Wait before retry
                        } else {
                            failureCount++;
                            System.err.println("[" + downloaderId + "] ✗ Failed to index on barrel " + (i + 1)
                                    + " after " + maxRetries + " attempts");
                        }
                    }
                }
            }

            // Log multicast result
            // if (successCount == allBarrels.size()) {
            // System.out.println("[" + downloaderId + "] ✓✓✓ MULTICAST SUCCESS: All " +
            // successCount
            // + " barrels indexed " + pageInfo.getUrl());
            // } else if (successCount > 0) {
            // System.out.println("[" + downloaderId + "] ⚠ PARTIAL SUCCESS: " +
            // successCount + "/" + allBarrels.size()
            // + " barrels indexed " + pageInfo.getUrl());
            // } else {
            if (successCount == 0) {
                System.err.println(
                        "[" + downloaderId + "] ✗✗✗ MULTICAST FAILED: No barrels indexed " + pageInfo.getUrl());
            } else if (successCount < allBarrels.size()) {
                System.err.println("[" + downloaderId + "] ⚠ PARTIAL SUCCESS: " + successCount + "/" + allBarrels.size()
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
            if (word.length() > 2) { // Ignora palavras muito curtas
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
            URL u = new URL(url);
            return u.getProtocol().startsWith("http") && !url.contains("#");
        } catch (Exception e) {
            return false;
        }
    }

    public void stop() {
        running = false;
    }

    public static void main(String[] args) {
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

            if (debugMode) {
                System.out.println("=== DEBUG MODE ENABLED (from config) ===");
                System.out.println("Debug delay: " + debugDelayMs + "ms");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load config file. Using defaults.");
        }

        // Command-line argument can override config file
        if (args.length > 0 && args[0].equalsIgnoreCase("--debug")) {
            debugMode = true;
            System.out.println("=== DEBUG MODE ENABLED (from command-line) ===");
        }

        final boolean debug = debugMode;

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
                    System.out.println("✓ Connected to Gateway");
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
                Downloader downloader = new Downloader("Downloader-1", gateway, debug);
                System.out.println("Downloader waiting for URLs to process...");

                Thread thread = new Thread(downloader);
                thread.start();

                // Wait for thread to finish (happens when Gateway connection is lost)
                thread.join();

                System.err.println("✗ Lost connection to Gateway!");
                System.err.println("Will attempt to reconnect...");

            } catch (Exception e) {
                System.err.println("Downloader error: " + e.getMessage());
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
