package barrel;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Storage Barrel - Servidor que armazena o índice invertido
 * Implementa replicação para tolerância a falhas
 */
public class StorageBarrel extends UnicastRemoteObject implements IBarrel {

    private String barrelId;

    // Índice invertido: palavra -> conjunto de URLs
    private Map<String, Set<String>> invertedIndex;

    // Informação completa sobre cada página: URL -> PageInfo
    private Map<String, PageInfo> pageDatabase;

    // Estatísticas
    private List<Long> responseTimes;
    private int queryCount;

    public StorageBarrel(String barrelId) throws RemoteException {
        super();
        this.barrelId = barrelId;
        this.invertedIndex = new ConcurrentHashMap<>();
        this.pageDatabase = new ConcurrentHashMap<>();
        this.responseTimes = new ArrayList<>();
        this.queryCount = 0;

        System.out.println("Storage Barrel " + barrelId + " initialized");
    }

    @Override
    public synchronized void indexPage(PageInfo pageInfo) throws RemoteException {
        String url = pageInfo.getUrl();

        System.out.println("[" + barrelId + "] Indexing: " + url);

        // Atualiza informação da página
        if (pageDatabase.containsKey(url)) {
            // Merge de inlinks se a página já existia
            PageInfo existing = pageDatabase.get(url);
            existing.getInlinks().addAll(pageInfo.getInlinks());
        } else {
            pageDatabase.put(url, pageInfo);
        }

        // Atualiza índice invertido
        for (String word : pageInfo.getWords()) {
            invertedIndex.putIfAbsent(word.toLowerCase(), ConcurrentHashMap.newKeySet());
            invertedIndex.get(word.toLowerCase()).add(url);
        }

        // Atualiza backlinks (inlinks) das páginas referenciadas
        for (String outlink : pageInfo.getOutlinks()) {
            if (pageDatabase.containsKey(outlink)) {
                pageDatabase.get(outlink).addInlink(url);
            }
        }
    }

    @Override
    public List<SearchResult> search(Set<String> terms, int page) throws RemoteException {
        long startTime = System.currentTimeMillis();

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

        long endTime = System.currentTimeMillis();
        recordResponseTime(endTime - startTime);

        if (startIdx >= results.size()) {
            return new ArrayList<>();
        }

        return results.subList(startIdx, endIdx);
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
        double avgTime = responseTimes.isEmpty() ? 0.0
                : responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 100.0;

        return new BarrelStats(barrelId, pageDatabase.size(), avgTime);
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    @Override
    public int getIndexSize() throws RemoteException {
        return pageDatabase.size();
    }

    private synchronized void recordResponseTime(long timeMs) {
        responseTimes.add(timeMs);
        queryCount++;

        // Mantém apenas as últimas 100 medições
        if (responseTimes.size() > 100) {
            responseTimes.remove(0);
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

            // Regista no RMI Registry
            java.rmi.registry.LocateRegistry.createRegistry(1099);
            java.rmi.Naming.rebind("rmi://localhost/Barrel" + barrelId, barrel);

            System.out.println("Storage Barrel " + barrelId + " ready");

        } catch (Exception e) {
            System.err.println("Barrel exception:");
            e.printStackTrace();
        }
    }
}
