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
 * RPC/RMI Gateway - porta de entrada para clientes
 * distribui pedidos entre os Storage Barrels disponiveis
 */
public class Gateway extends UnicastRemoteObject implements IGateway {

    private List<IBarrel> barrels;
    private Map<String, Integer> searchFrequency; // tracking de pesquisas mais comuns
    private int currentBarrelIndex; // para round-robin (fallback)
    private URLQueue urlQueue;
    private BarrelRegistry barrelRegistry; // registo para suporte multicast

    // cache de resultados de pesquisa (LRU com limite de tamanho)
    private Map<String, List<SearchResult>> searchCache;
    private static final int SEARCH_CACHE_SIZE = 1000; // mantem as 1000 pesquisas mais populares

    // rastreio de URLs visitados usando Bloom filter (eficiente em espaco)
    private BloomFilter<String> visitedURLsBloom;
    // cache de URLs recentes (ultimos 10K) para eliminar falsos positivos do Bloom
    // filter
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
    private static final int MAX_RESPONSE_SAMPLES = 20; // Keep last 20 response times

    public Gateway() throws RemoteException {
        super();
        this.barrels = new ArrayList<>();
        this.searchFrequency = new ConcurrentHashMap<>();
        this.currentBarrelIndex = 0;

        // inicializa cache de pesquisa com eviction LRU
        this.searchCache = Collections.synchronizedMap(
                new LinkedHashMap<String, List<SearchResult>>(SEARCH_CACHE_SIZE + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest) {
                        return size() > SEARCH_CACHE_SIZE;
                    }
                });

        this.urlQueue = new URLQueue();
        this.barrelRegistry = new BarrelRegistry(); // inicializa registo de barrels

        // inicializa Bloom filter para rastreio de URLs visitados
        this.visitedURLsBloom = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_URLS,
                FALSE_POSITIVE_RATE);

        // inicializa cache LRU para URLs recentes (elimina falsos positivos)
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
     * obtem o registo de barrels (para acesso RMI)
     */
    public BarrelRegistry getBarrelRegistry() {
        return barrelRegistry;
    }

    /**
     * valida se uma string e um URL valido
     */
    private boolean isValidURL(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        try {
            URI uri = URI.create(urlString);

            // verifica se o URI e absoluto (tem scheme)
            if (!uri.isAbsolute()) {
                return false;
            }

            // verifica protocolo
            String scheme = uri.getScheme();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }

            // verifica se o host existe
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return false;
            }

            // validacao adicional de host
            // rejeita hosts que terminam com ponto
            if (host.endsWith(".")) {
                return false;
            }

            // rejeita hosts com caracteres ou padroes invalidos
            if (host.startsWith(".") || host.contains("..")) {
                return false;
            }

            // requer pelo menos um ponto no dominio (rejeita hosts de palavra unica como
            // "localhost" em producao)
            // mas permite enderecos IP
            if (!host.matches(".*\\..*") && !host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                // label unica sem pontos - apenas aceita "localhost"
                if (!host.equals("localhost")) {
                    return false;
                }
            }

            // verifica estrutura de dominio valida (pelo menos 2 caracteres no TLD)
            if (host.matches(".*\\..$") || host.matches(".*\\.$")) {
                return false; // TLD muito curto ou termina com ponto
            }

            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void indexURL(String url) throws RemoteException {
        // valida URL antes de processar
        if (!isValidURL(url)) {
            System.out.println("Invalid URL rejected: " + url);
            throw new RemoteException(
                    "Invalid URL: " + url + ". URL must start with http:// or https:// and have a valid format.");
        }

        System.out.println("New user index request: " + url);
        urlQueue.addURL(url, true); // alta prioridade para URLs submetidos por utilizadores
        // notifica listeners sobre mudanca de stats (novo URL submetido por utilizador
        // afeta tamanho
        // da queue)
        try {
            notifyStatsListeners(getSystemStats());
        } catch (Exception e) {
            // ignora falhas de notificacao aqui
        }
    }

    @Override
    public List<SearchResult> search(String query, int page) throws RemoteException {
        // normaliza query
        String normalizedQuery = query.toLowerCase().trim();

        // atualiza estatisticas de pesquisa
        searchFrequency.merge(normalizedQuery, 1, Integer::sum);
        // notifica listeners que as frequencias de pesquisa mudaram
        try {
            notifyStatsListeners(getSystemStats());
        } catch (Exception e) {
            // ignora
        }

        // verifica cache
        String cacheKey = normalizedQuery + "_" + page;
        if (searchCache.containsKey(cacheKey)) {
            System.out.println("Cache hit for: " + query);
            return searchCache.get(cacheKey);
        }

        // divide query em termos
        Set<String> terms = new HashSet<>(Arrays.asList(normalizedQuery.split("\\s+")));

        // seleciona barrel (baseado em tempo de resposta)
        IBarrel barrel = selectBarrel();

        if (barrel == null) {
            throw new RemoteException("No active barrels available");
        }

        try {
            long startTime = System.currentTimeMillis();
            List<SearchResult> results = barrel.search(terms, page);
            long responseTime = System.currentTimeMillis() - startTime;

            // regista tempo de resposta para este barrel
            recordBarrelResponseTime(barrel, responseTime);

            // armazena em cache
            searchCache.put(cacheKey, results);

            return results;

        } catch (RemoteException e) {
            System.err.println("Error searching on barrel: " + e.getMessage());
            // tenta outro barrel
            barrels.remove(barrel);
            if (!barrels.isEmpty()) {
                return search(query, page); // retry
            }
            throw e;
        }
    }

    @Override
    public List<String> getPagesPointingTo(String url) throws RemoteException {
        try {
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();

            if (activeBarrels.isEmpty()) {
                throw new RemoteException("No active barrels available");
            }

            // armazena inlinks de todos os barrels e remove duplicados
            Set<String> allInlinks = new HashSet<>();

            // consulta todos os barrels (desnecessario mas util na eventualidade de um
            // deles nao ter algum dado)
            for (IBarrel barrel : activeBarrels) {
                try {
                    List<String> inlinks = barrel.getInlinks(url);
                    if (inlinks != null) {
                        allInlinks.addAll(inlinks);
                    }
                } catch (RemoteException e) {
                    // ignora barrels que falham, continua com outros
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

        // top 10 pesquisas
        List<String> topSearches = searchFrequency.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        stats.setTopSearches(topSearches);

        // estatisticas dos barrels - usa BarrelRegistry e enriquece com dados de tempo
        // de resposta
        // do Gateway
        List<BarrelStats> barrelStatsList = new ArrayList<>();
        int totalPages = 0;

        try {
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();
            for (IBarrel barrel : activeBarrels) {
                try {
                    BarrelStats bs = barrel.getStats();

                    // enriquece com tempo de resposta real do rastreio do Gateway
                    int barrelId = System.identityHashCode(barrel);
                    List<Long> responseTimes = barrelResponseTimes.get(barrelId);

                    if (responseTimes != null && !responseTimes.isEmpty()) {
                        double avgResponseTime = responseTimes.stream()
                                .mapToLong(Long::longValue)
                                .average()
                                .orElse(0.0);
                        // cria novo BarrelStats com tempo de resposta real
                        bs = new BarrelStats(bs.getBarrelId(), bs.getIndexSize(), avgResponseTime);
                    }

                    barrelStatsList.add(bs);
                    totalPages += bs.getIndexSize(); // soma todas as paginas entre barrels
                } catch (Exception e) {
                    System.err.println("Failed to get stats from barrel: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get barrels from registry: " + e.getMessage());
        }

        stats.setBarrelStats(barrelStatsList);
        stats.setTotalIndexedPages(totalPages);
        // adiciona tamanho da queue de URLs
        try {
            stats.setUrlQueueSize(urlQueue.size());
        } catch (Exception e) {
            // ignora
        }

        return stats;
    }

    /**
     * seleciona um barrel usando estrategia de menor tempo de resposta
     * fallback para round-robin se nao houver dados de tempo de resposta
     * disponiveis
     */
    private synchronized IBarrel selectBarrel() {
        try {
            // obtem barrels do registo (nao a lista antiga)
            List<IBarrel> activeBarrels = barrelRegistry.getAllBarrels();

            if (activeBarrels.isEmpty()) {
                return null;
            }

            // filtra barrels que nao estao prontos (ainda a sincronizar)
            List<IBarrel> readyBarrels = new ArrayList<>();
            for (IBarrel barrel : activeBarrels) {
                try {
                    if (barrel.isReady()) {
                        readyBarrels.add(barrel);
                    } else {
                        System.out.println("[Gateway] Barrel not ready (syncing), skipping...");
                    }
                } catch (RemoteException e) {
                    // se nao conseguimos verificar prontidao, ignora este barrel
                    System.err.println("[Gateway] Error checking barrel readiness: " + e.getMessage());
                }
            }

            if (readyBarrels.isEmpty()) {
                System.out.println("[Gateway] No ready barrels available (all syncing)");
                return null;
            }

            // se so ha um barrel pronto, devolve-o
            if (readyBarrels.size() == 1) {
                return readyBarrels.get(0);
            }

            // selecao baseada em tempo de resposta: escolhe barrel com menor tempo medio
            // de resposta
            IBarrel fastestBarrel = null;
            double lowestAvgResponseTime = Double.MAX_VALUE;

            for (IBarrel barrel : readyBarrels) {
                int barrelId = System.identityHashCode(barrel);
                List<Long> responseTimes = barrelResponseTimes.get(barrelId);

                if (responseTimes == null || responseTimes.isEmpty()) {
                    // sem dados para este barrel ainda - da-lhe uma oportunidade (assume que e
                    // rapido)
                    // isto garante que novos barrels sao testados
                    if (fastestBarrel == null) {
                        fastestBarrel = barrel;
                        lowestAvgResponseTime = 0; // otimista: assume que e muito rapido
                    }
                } else {
                    // calcula tempo medio de resposta
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

            // fallback para round-robin se por alguma razao nenhum barrel foi selecionado
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
     * regista o tempo de resposta de um barrel e mantem uma janela deslizante
     * de tempos de resposta recentes para decisoes de balanceamento de carga
     */
    private void recordBarrelResponseTime(IBarrel barrel, long responseTimeMs) {
        int barrelId = System.identityHashCode(barrel);

        barrelResponseTimes.compute(barrelId, (k, times) -> {
            if (times == null) {
                times = new ArrayList<>();
            }

            times.add(responseTimeMs);

            // mantem apenas as ultimas MAX_RESPONSE_SAMPLES medicoes
            if (times.size() > MAX_RESPONSE_SAMPLES) {
                times.remove(0); // remove a mais antiga
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
    public void registerStatsListener(common.IStatsListener listener) throws RemoteException {
        if (listener == null)
            return;
        statsListeners.add(listener);
        // envia snapshot inicial
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
     * envia o snapshot de stats fornecido para todos os listeners registados
     */
    private void notifyStatsListeners(SystemStats stats) {
        // so envia se o snapshot mudou para garantir que clientes sao atualizados
        // apenas em
        // mudancas reais (sem refreshes periodicos)
        String snapshot = stats.toString();
        if (snapshot.equals(lastStatsSnapshotString)) {
            return; // nada mudou
        }
        lastStatsSnapshotString = snapshot;

        // copia para evitar modificacao concorrente durante iteracao
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

    public static void main(String[] args) {
        try {
            // Load RMI configuration
            String gatewayHost = common.RMIConfig.getGatewayHost();
            int gatewayPort = common.RMIConfig.getGatewayPortInt();
            String baseUrl = common.RMIConfig.getRMIBaseUrl();

            // IMPORTANT: Set RMI hostname for external access
            System.setProperty("java.rmi.server.hostname", gatewayHost);

            System.out.println("===========================================");
            System.out.println("        GOOGOL GATEWAY STARTING");
            System.out.println("===========================================");
            common.RMIConfig.printConfig("Gateway");
            System.out.println("===========================================");
            System.out.println();

            Gateway gateway = new Gateway();

            // regista no RMI Registry (ou usa existente)
            try {
                java.rmi.registry.LocateRegistry.createRegistry(gatewayPort);
                System.out.println("RMI Registry created on port " + gatewayPort);
            } catch (Exception e) {
                // registo ja existe, continua
                System.out.println("Using existing RMI Registry on port " + gatewayPort);
            }

            java.rmi.Naming.rebind(baseUrl + "/Gateway", gateway);

            // tambem faz bind do registo de barrels para acesso direto
            java.rmi.Naming.rebind(baseUrl + "/BarrelRegistry", gateway.getBarrelRegistry());

            System.out.println("Gateway bound to " + baseUrl + "/Gateway");
            System.out.println("BarrelRegistry bound to " + baseUrl + "/BarrelRegistry");
            System.out.println("Waiting for barrels to register themselves...");

            // thread para imprimir status da fila e barrels registados a cada 5 segundos
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
                            // ignora
                        }
                        System.out.println(
                                "[Gateway Status] URL Queue: " + queueSize
                                        + " | Active Barrels: " + barrelCount);
                        // envia snapshot periodico de stats aos listeners registados
                        try {
                            gateway.notifyStatsListeners(gateway.getSystemStats());
                        } catch (Exception e) {
                            // ignora erros de notificacao da thread de status
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            statusThread.setDaemon(true); // thread daemon para nao impedir shutdown
            statusThread.start();

            // adiciona shutdown hook para saida limpa
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // flush dos streams de output para evitar problemas Maven/Jansi
                    System.out.flush();
                    System.err.flush();

                    System.out.println("\n\nShutting down Gateway...");
                    keepRunning[0] = false;

                    try {
                        // remove bind do registo RMI
                        String unbindUrl = common.RMIConfig.getRMIBaseUrl();
                        java.rmi.Naming.unbind(unbindUrl + "/Gateway");
                        java.rmi.Naming.unbind(unbindUrl + "/BarrelRegistry");
                        System.out.println("Unbound from RMI registry");
                    } catch (Exception e) {
                        // ignora erros durante shutdown
                    }

                    try {
                        // faz unexport do gateway
                        UnicastRemoteObject.unexportObject(gateway, true);
                        System.out.println("Gateway unexported");
                    } catch (Exception e) {
                        // ignora erros durante shutdown
                    }

                    System.out.println("Gateway shutdown complete. Goodbye!");
                    System.out.flush();
                } catch (Exception e) {
                    // suprime erros de output durante shutdown (problemas Maven/Jansi)
                }
            }));

            // mantem o servidor ativo
            System.out.println("Press Ctrl+C to stop...");
            Object lock = new Object();
            synchronized (lock) {
                lock.wait(); // espera indefinidamente
            }

        } catch (Exception e) {
            System.err.println("Gateway exception:");
            e.printStackTrace();
        }
    }
}
