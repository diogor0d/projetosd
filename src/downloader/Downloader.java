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
 * Downloader - Web Crawler que obtem e processa paginas
 * multiplos downloader threads trabalham em paralelo
 */
public class Downloader implements Runnable {

    private String downloaderId;
    private IGateway gateway; // usa Gateway via RMI em vez de objetos diretos
    private List<IBarrel> barrels;
    private volatile boolean running; // volatile para visibilidade entre threads
    private boolean debugMode; // modo debug abranda o processamento

    // suporte multi-threading
    private ThreadPoolExecutor threadPool;
    private static int WORKER_THREADS = 5;
    // semaforo para reservar slots de processamento (threads + capacidade da queue)
    private Semaphore capacitySemaphore;

    private static int MAX_DEPTH = 3; // Profundidade maxima de crawling
    private static int TIMEOUT = 5000; // Timeout para conexoes (ms)
    private static int DEBUG_DELAY_MS = 1500; // delay em modo debug

    // filtragem de stopwords
    private Set<String> stopwords;

    // construtor para downloader baseado em RMI (recomendado)
    public Downloader(String downloaderId, IGateway gateway, boolean debugMode) {
        this.downloaderId = downloaderId;
        this.gateway = gateway;
        this.barrels = new ArrayList<>();
        this.running = true;
        this.debugMode = debugMode;

        // carrega stopwords
        this.stopwords = TextUtils.loadStopwords(downloaderId);
        System.out.println("[" + downloaderId + "] Loaded " + stopwords.size() + " stopwords");

        // cria thread pool com threads nomeadas
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
        // inicializa semaforo de capacidade: threads do pool + capacidade da queue
        this.capacitySemaphore = new Semaphore(WORKER_THREADS + WORKER_THREADS);

        if (debugMode) {
            System.out.println("[" + downloaderId + "] DEBUG MODE ENABLED - Processing will be slowed down");
        }

        System.out.println("[" + downloaderId + "] Initialized with " + WORKER_THREADS + " worker threads");
    }

    // construtor sem modo debug (por defeito false)
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
                // verifica se o gateway esta disponivel antes de tentar usar
                if (gateway == null) {
                    System.err.println("[" + downloaderId + "] Failed to connect to gateway. Waiting to try again...");
                    Thread.sleep(3000);
                    continue;
                }

                // reserva um slot de processamento (bloqueia ate um slot de thread/queue estar
                // disponivel)
                capacitySemaphore.acquire();

                // bloqueia ate obter proximo URL via Gateway RMI (sem busy waiting)
                String url = gateway.getNextURL(); // Bloqueia ate ter URL disponivel
                System.out.println("[" + downloaderId + "] Fetched URL from queue: " + url);

                // submete tarefa ao thread pool (enqueue bloqueante se pool estiver cheio)
                submitBlocking(() -> {
                    try {
                        // processa URL diretamente (sem tracking de URLs visitados)
                        processURL(url);
                    } catch (Exception e) {
                        System.err.println(
                                "[" + downloaderId + "] Worker thread error for " + url + ": " + e.getMessage());
                    }
                });

            } catch (java.rmi.RemoteException e) {
                // gateway offline ou problema de rede - sai para desencadear reconnection
                System.err.println("[" + downloaderId + "] Gateway connection error: " + e.getMessage());
                System.err.println("[" + downloaderId + "] Gateway appears to be down. Exiting to reconnect...");
                break; // sai do loop run para desencadear reconnection no main()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[" + downloaderId + "] Error: " + e.getMessage());
            }
        }

        // limpeza adequada
        shutdown();
        System.out.println("[" + downloaderId + "] Downloader stopped");
    }

    /**
     * shutdown clean do thread pool
     */
    private void shutdown() {
        System.out.println("[" + downloaderId + "] Shutting down thread pool...");

        threadPool.shutdown(); // para de aceitar novas tarefas

        try {
            // aguarda conclusao de tarefas existentes (com timeout)
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                System.err.println("[" + downloaderId + "] Thread pool did not terminate in time, forcing shutdown...");
                threadPool.shutdownNow(); // forca shutdown

                // aguarda novamente para tarefas responderem ao cancelamento
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
     * submete uma tarefa ao thread pool, bloqueando ao colocar o runnable na
     * queue do executor se o pool estiver saturado. isto evita
     * RejectedExecutionException
     * e nao faz busy-wait nem sleep.
     */
    private void submitBlocking(Runnable task) {
        // encapsula a tarefa para libertar a permissao do semaforo quando terminar
        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                capacitySemaphore.release();
            }
        };

        try {
            // tenta passar para o executor primeiro
            threadPool.execute(wrapped);
        } catch (RejectedExecutionException rex) {
            // se rejeitado, enfileira de forma bloqueante na queue. isto vai aguardar ate
            // haver espaco disponivel. se interrompido enquanto aguarda, liberta a
            // permissao para evitar fugas e volta a interromper.
            try {
                BlockingQueue<Runnable> q = threadPool.getQueue();
                q.put(wrapped);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                // liberta permissao ja que tarefa nao foi enfileirada
                capacitySemaphore.release();
                throw new RuntimeException("Interrupted while enqueueing task", ie);
            }
        }
    }

    private void processURL(String urlStr) {
        // obtem nome da thread para logging
        String threadName = Thread.currentThread().getName();

        try {
            System.out.println("[" + downloaderId + "][" + threadName + "] Processing: " + urlStr);

            // modo debug: abranda o processamento
            if (debugMode) {
                System.out.println(
                        "[" + downloaderId + "][" + threadName + "] [DEBUG] Sleeping for " + DEBUG_DELAY_MS + "ms...");
                Thread.sleep(DEBUG_DELAY_MS);
            }

            // obtem e analisa a pagina
            Document doc = Jsoup.connect(urlStr)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (compatible; GoogolBot/1.0)")
                    .get();

            PageInfo pageInfo = extractPageInfo(urlStr, doc);

            // imprime informacao da pagina
            System.out.println("[" + downloaderId + "][" + threadName + "] Extracted PageInfo: " + pageInfo);

            // RMI MULTICAST: envia para todos os barrels via BarrelRegistry
            multicastToAllBarrels(pageInfo);

            // adiciona links encontrados a fila (com prioridade normal - crawled)
            for (String link : pageInfo.getOutlinkUrls()) {
                gateway.addDiscoveredURL(link, false); // false = prioridade normal (crawled)
            }

            // modo debug: abranda antes do proximo URL
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
     * Multicast RMI confiavel - envia PageInfo para TODOS os barrels registados
     * implementa logica de retry para confiabilidade
     */
    private void multicastToAllBarrels(PageInfo pageInfo) {
        try {
            // obtem registo de barrels
            IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup("rmi://localhost/BarrelRegistry");

            // obtem todos os barrels ativos
            List<IBarrel> allBarrels = registry.getAllBarrels();

            if (allBarrels.isEmpty()) {
                System.err.println("[" + downloaderId + "] WARNING: No barrels available!");
                System.err.println("[" + downloaderId + "] Retrying in 2 seconds: " + pageInfo.getUrl());

                // aguarda e tenta novamente apos 2 segundos
                Thread.sleep(2000);

                // tenta obter barrels novamente
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

            // rastreia sucesso/falha para cada barrel
            int successCount = 0;
            int failureCount = 0;

            // envia para TODOS os barrels (multicast confiavel via RMI)
            for (int i = 0; i < allBarrels.size(); i++) {
                IBarrel barrel = allBarrels.get(i);
                boolean indexed = false;
                int retries = 0;
                int maxRetries = 3;

                // logica de retry para confiabilidade
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
                            Thread.sleep(500); // aguarda antes de tentar novamente
                        } else {
                            failureCount++;
                            System.err.println("[" + downloaderId + "] Failed to index on barrel " + (i + 1)
                                    + " after " + maxRetries + " attempts");
                        }
                    }
                }
            }

            // regista resultado do multicast
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

                // apenas tenta novamente se nao estivermos em shutdown (verifica mensagens de
                // erro para "shutting
                // down")
                // aguarda brevemente e tenta MAIS UMA vez com lista de barrels atualizada
                System.err.println("[" + downloaderId + "] Retrying in 2 seconds...");
                Thread.sleep(2000);

                // tenta novamente com lista de barrels atualizada
                List<IBarrel> retryBarrels = registry.getAllBarrels();

                if (!retryBarrels.isEmpty()) {
                    System.out.println("[" + downloaderId + "] Retrying with " + retryBarrels.size() + " barrel(s)");

                    for (IBarrel barrel : retryBarrels) {
                        try {
                            barrel.indexPage(pageInfo);
                            successCount++;
                            System.out.println("[" + downloaderId + "] Retry successful for: " + pageInfo.getUrl());
                            break; // sucesso em pelo menos um barrel
                        } catch (Exception retryEx) {
                            // se barrel esta em shutdown, nao faz spam com erros
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

            // fallback para lista local de barrels se registo nao estiver disponivel
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

        // titulo
        pageInfo.setTitle(doc.title());

        // texto (snippet) - primeiros 200 caracteres
        String bodyText = doc.body().text();
        if (bodyText.length() > 200) {
            pageInfo.setTextSnippet(bodyText.substring(0, 200) + "...");
        } else {
            pageInfo.setTextSnippet(bodyText);
        }

        // extrai palavras e calcula Term Frequency (TF), filtrando stopwords

        // aproveita-se o facto do downloader efetuar o processamento das palavras de
        // qualquer forma: offload de trabalho para os barrels
        String[] words = bodyText.toLowerCase().split("\\W+");
        Map<String, Integer> termFrequencies = new HashMap<>();

        for (String word : words) {
            if (!word.isEmpty() && !stopwords.contains(word)) { // filtra stopwords
                pageInfo.addWord(word);
                // conta frequencia dos termos
                termFrequencies.put(word, termFrequencies.getOrDefault(word, 0) + 1);
            }
        }

        // armazena frequencias dos termos em PageInfo
        pageInfo.setTermFrequencies(termFrequencies);

        // extrai links
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            try {
                String absUrl = link.absUrl("href");
                if (isValidURL(absUrl)) {
                    pageInfo.addOutlink(absUrl);
                }
            } catch (Exception e) {
                // ignora URLs invalidos
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
        // analisa ID do downloader a partir do argumento da linha de comandos
        String downloaderId = "Downloader-1"; // ID por defeito
        if (args.length > 0 && !args[0].startsWith("--")) {
            // primeiro argumento e o ID (se nao comecar com --)
            downloaderId = "Downloader-" + args[0];
        }

        // carrega configuracao de system.properties
        boolean debugMode = false;
        int debugDelayMs = 1500;

        try {
            java.util.Properties props = new java.util.Properties();
            java.io.FileInputStream fis = new java.io.FileInputStream("config/system.properties");
            props.load(fis);
            fis.close();

            debugMode = Boolean.parseBoolean(props.getProperty("downloader.debug.mode", "false"));
            debugDelayMs = Integer.parseInt(props.getProperty("downloader.debug.delay.ms", "1500"));

            // carrega valores configuraveis
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

        // argumento da linha de comandos pode sobrepor ficheiro de config (flag
        // --debug)
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--debug")) {
                debugMode = true;
                System.out.println("=== DEBUG MODE ENABLED (from command-line) ===");
                break;
            }
        }

        final boolean debug = debugMode;
        final String finalDownloaderId = downloaderId;

        // referencia ao downloader atual para shutdown hook
        final Downloader[] currentDownloader = new Downloader[1];

        // adiciona shutdown hook para terminacao graciosa
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // faz flush das streams primeiro para evitar problemas de parsing ANSI do
                // Maven/Jansi
                System.out.flush();
                System.err.flush();

                System.out.println("\n=== Shutdown signal received ===");
                if (currentDownloader[0] != null) {
                    System.out.println("Initiating graceful shutdown...");
                    currentDownloader[0].stop();
                    // da um momento para shutdown limpo
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                System.out.println("=== Shutdown complete ===");
                System.out.flush();
            } catch (Exception e) {
                // suprime qualquer erro de output durante shutdown
            }
        }));

        // gestor de conexao continuo - trata reconnection quando Gateway faz shutdown
        while (true) {
            IGateway gateway = null;

            // fase 1: conecta ao Gateway (retry ate ter sucesso)
            System.out.println("Attempting to connect to Gateway...");
            while (gateway == null) {
                try {
                    IGateway tempGateway = (IGateway) java.rmi.Naming.lookup("rmi://localhost/Gateway");
                    // verifica se conexao esta realmente ativa
                    tempGateway.getSystemStats(); // chamada de teste para verificar conectividade
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

            // verifica e mostra barrels registados
            try {
                IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup("rmi://localhost/BarrelRegistry");
                int barrelCount = registry.getBarrelCount();
                System.out.println("Downloader initialized - discovered " + barrelCount + " barrel(s) in registry");
            } catch (Exception e) {
                System.err.println("Could not connect to BarrelRegistry: " + e.getMessage());
            }

            // fase 2: executa downloader ate conexao ao Gateway falhar
            try {
                Downloader downloader = new Downloader(finalDownloaderId, gateway, debug);
                currentDownloader[0] = downloader; // guarda referencia para shutdown hook

                Thread thread = new Thread(downloader);
                thread.start();

                // aguarda thread terminar (acontece quando conexao ao Gateway e perdida)
                thread.join();

                System.err.println(finalDownloaderId + ": Lost connection to Gateway!");
                System.err.println("Will attempt to reconnect...");

            } catch (Exception e) {
                System.err.println(finalDownloaderId + " error: " + e.getMessage());
            } finally {
                currentDownloader[0] = null; // limpa referencia
            }

            // pequeno delay antes de tentar reconnection
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
