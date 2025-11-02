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
 * Storage Barrel - servidor que armazena o indice invertido
 * implementa replicacao para tolerancia a falhas
 */
public class StorageBarrel extends UnicastRemoteObject implements IBarrel {

    private String barrelId;

    // sistema de Document ID para otimizacao de armazenamento de URLs
    // URL -> Document ID
    private ConcurrentHashMap<String, Integer> urlToDocId;
    // Document ID -> URL
    private ConcurrentHashMap<Integer, String> docIdToUrl;
    // proximo Document ID disponivel
    private AtomicInteger nextDocId;

    // indice invertido: palavra -> conjunto de Document IDs (em vez de URLs)
    private ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Integer, Boolean>> invertedIndex;

    // informacao completa sobre cada pagina: Document ID -> PageInfo
    private ConcurrentHashMap<Integer, PageInfo> pageDatabase;

    // flag de shutdown para parar de aceitar novos pedidos
    private volatile boolean shuttingDown = false;

    // configuracao: se deve usar indice JSON guardado
    private boolean useJsonIndex;

    // stopwords para filtragem de queries de pesquisa
    private Set<String> stopwords;

    // estado de sincronizacao
    private volatile boolean isSyncing = false;
    private volatile boolean syncCompleted = false;
    private long barrelStartTimestamp; // quando este barrel iniciou (para filtragem de sync)
    private volatile String syncPeerBarrelId = null; // de que peer estamos a fazer sync
    private volatile int syncProgress = 0; // percentagem de progresso do sync (0-100)

    public StorageBarrel(String barrelId) throws RemoteException {
        super();
        this.barrelId = barrelId;
        this.urlToDocId = new ConcurrentHashMap<>();
        this.docIdToUrl = new ConcurrentHashMap<>();
        this.nextDocId = new java.util.concurrent.atomic.AtomicInteger(1);
        this.invertedIndex = new ConcurrentHashMap<>();
        this.pageDatabase = new ConcurrentHashMap<>();
        // barrelStartTimestamp sera definido mais tarde, logo antes de aceitar paginas

        // carrega configuracao
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

        // carrega stopwords
        this.stopwords = TextUtils.loadStopwords(barrelId);
        System.out.println("[" + barrelId + "] Loaded " + stopwords.size() + " stopwords");

        // carrega indice existente do disco se disponivel
        loadIndexFromDisk();

        // conta paginas com conteudo real (exclui placeholders)
        int currentIndexSize = 0;
        for (PageInfo page : pageDatabase.values()) {
            if (page.getWords() != null && !page.getWords().isEmpty()) {
                currentIndexSize++;
            }
        }

        if (currentIndexSize == 0) {
            // indice vazio - vai definitivamente precisar de sincronizar com peers
            System.out.println("[" + barrelId + "] Empty index detected, will sync with peers after registration");
        } else {
            // tem dados - vai verificar se estao desatualizados comparando com peers
            long mostRecentTimestamp = 0;
            for (PageInfo page : pageDatabase.values()) {
                if (page.getWords() != null && !page.getWords().isEmpty() &&
                        page.getIndexedTimestamp() > mostRecentTimestamp) {
                    mostRecentTimestamp = page.getIndexedTimestamp();
                }
            }
            System.out.println("[" + barrelId + "] Loaded existing index with " + currentIndexSize +
                    " pages (most recent: " + new java.util.Date(mostRecentTimestamp) + ")");
            System.out.println("[" + barrelId + "] Will check if index is outdated after registration");
            // nao define syncCompleted ainda - vai verificar com peers primeiro
        }
    }

    /**
     * obtem ou cria um document ID para um URL
     */
    private int getOrCreateDocId(String url) {
        return urlToDocId.computeIfAbsent(url, k -> {
            int docId = nextDocId.getAndIncrement();
            docIdToUrl.put(docId, url);
            return docId;
        });
    }

    /**
     * obtem o URL para um document ID
     */
    private String getUrlForDocId(int docId) {
        return docIdToUrl.get(docId);
    }

    @Override
    public void indexPage(PageInfo pageInfo) throws RemoteException {
        // rejeita novos pedidos de indexacao durante o shutdown
        if (shuttingDown) {
            throw new RemoteException("Barrel is shutting down, not accepting new requests");
        }

        String url = pageInfo.getUrl();
        int docId = getOrCreateDocId(url);

        System.out.println("[" + barrelId + "] Indexing: " + url + " (docId=" + docId + ")");

        // converte URLs de outlinks para document IDs (do Downloader)
        int outlinkCount = 0;
        for (String outlinkUrl : pageInfo.getOutlinkUrls()) {
            int outlinkDocId = getOrCreateDocId(outlinkUrl);
            pageInfo.addOutlink(outlinkDocId);
            outlinkCount++;
        }

        // limpa outlinkUrls apos conversao - ja nao sao necessarios, poupa memoria
        pageInfo.getOutlinkUrls().clear();

        /*
         * System.out.println("[" + barrelId + "] [DEBUG] Converted " + outlinkCount +
         * " outlinks to docIds. " +
         * "PageInfo now has: words=" + pageInfo.getWords().size() +
         * " outlinks=" + pageInfo.getOutlinks().size());
         */

        // atualiza informacao da pagina usando document ID
        PageInfo existing = pageDatabase.get(docId);
        if (existing != null) {
            // atualiza pagina existente com novo conteudo
            existing.setTitle(pageInfo.getTitle());
            existing.setTextSnippet(pageInfo.getTextSnippet());
            existing.setWords(pageInfo.getWords());
            existing.setTermFrequencies(pageInfo.getTermFrequencies()); // copia frequencias de termos!
            existing.setIndexedTimestamp(pageInfo.getIndexedTimestamp()); // atualiza timestamp para o mais recente
            // funde inlinks e outlinks
            existing.getInlinks().addAll(pageInfo.getInlinks());
            existing.getOutlinks().addAll(pageInfo.getOutlinks());
        } else {
            pageDatabase.put(docId, pageInfo);
        }

        // debug: verifica se frequencias de termos foram recebidas
        int tfSize = (pageInfo.getTermFrequencies() != null) ? pageInfo.getTermFrequencies().size() : 0;
        if (tfSize == 0) {
            System.out.println("[" + barrelId + "] [WARNING] PageInfo has no term frequencies for: " + url);
        } else if (docId <= 3) { // log primeiras 3 paginas
            /*
             * System.out.println(
             * "[" + barrelId + "] [DEBUG] Indexed docId=" + docId + " with " + tfSize +
             * " term frequencies");
             */
        }

        // atualiza indice invertido usando document IDs
        for (String word : pageInfo.getWords()) {
            String lowerWord = word.toLowerCase();
            invertedIndex.computeIfAbsent(lowerWord, k -> ConcurrentHashMap.newKeySet()).add(docId);
        }

        // atualiza backlinks (inlinks) das paginas referenciadas
        // cria placeholder PageInfo para paginas ainda nao indexadas para rastrear
        // inlinks
        for (Integer outlinkDocId : pageInfo.getOutlinks()) {
            String outlinkUrl = getUrlForDocId(outlinkDocId);
            PageInfo outlinkedPage = pageDatabase.computeIfAbsent(outlinkDocId, k -> new PageInfo(outlinkUrl));
            outlinkedPage.addInlink(docId);
        }
    }

    @Override
    public List<SearchResult> search(Set<String> terms, int page) throws RemoteException {
        // aguarda sincronizacao completa antes de servir pesquisas
        while (isSyncing) {
            System.out.println("[" + barrelId + "] Search blocked: synchronization in progress...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RemoteException("Search interrupted during sync");
            }
        }

        // normaliza e filtra stopwords dos termos de pesquisa
        Set<String> normalizedTerms = terms.stream()
                .map(String::toLowerCase)
                .filter(term -> !stopwords.contains(term)) // remove stopwords
                .collect(Collectors.toSet());

        // se todos os termos eram stopwords, retorna resultados vazios
        if (normalizedTerms.isEmpty()) {
            System.out.println("[" + barrelId + "] Search query contained only stopwords");
            return new ArrayList<>();
        }

        // encontra Document IDs que contem todos os termos
        Set<Integer> resultDocIds = null;

        for (String term : normalizedTerms) {
            Set<Integer> docIdsForTerm = invertedIndex.get(term);

            if (docIdsForTerm == null || docIdsForTerm.isEmpty()) {
                // se algum termo nao existe, nao ha resultados
                return new ArrayList<>();
            }

            if (resultDocIds == null) {
                resultDocIds = new HashSet<>(docIdsForTerm);
            } else {
                resultDocIds.retainAll(docIdsForTerm); // intersecao
            }
        }

        if (resultDocIds == null || resultDocIds.isEmpty()) {
            return new ArrayList<>();
        }

        // total de documentos para calculo de IDF
        int totalDocs = pageDatabase.size();

        // calcula score TF-IDF para cada resultado
        List<SearchResult> results = resultDocIds.stream()
                .map(docId -> {
                    String url = getUrlForDocId(docId);
                    PageInfo pageInfo = pageDatabase.get(docId);

                    // calcula score TF-IDF para este documento
                    double tfIdfScore = calculateTfIdfScore(pageInfo, normalizedTerms, totalDocs);

                    return new SearchResult(
                            url,
                            pageInfo.getTitle() != null ? pageInfo.getTitle() : url,
                            pageInfo.getTextSnippet() != null ? pageInfo.getTextSnippet() : "",
                            pageInfo.getInlinkCount(),
                            tfIdfScore);
                })
                // ordena por score TF-IDF (descendente), depois por contagem de inlinks
                // (desempate)
                .sorted((r1, r2) -> {
                    int scoreCompare = Double.compare(r2.getTfIdfScore(), r1.getTfIdfScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    // desempate: usa contagem de inlinks
                    return Integer.compare(r2.getRelevance(), r1.getRelevance());
                })
                .collect(Collectors.toList());

        // paginacao (10 resultados por pagina)
        int startIdx = page * 10;
        int endIdx = Math.min(startIdx + 10, results.size());

        if (startIdx >= results.size()) {
            return new ArrayList<>();
        }

        // cria novo ArrayList do subList para garantir que serializacao RMI funciona
        return new ArrayList<>(results.subList(startIdx, endIdx));
    }

    /**
     * calcula score TF-IDF para um documento dados os termos de pesquisa
     * 
     * @param pageInfo  o documento
     * @param terms     termos de pesquisa
     * @param totalDocs numero total de documentos no corpus
     * @return score TF-IDF (soma do TF-IDF para cada termo)
     */
    private double calculateTfIdfScore(PageInfo pageInfo, Set<String> terms, int totalDocs) {
        double score = 0.0;

        for (String term : terms) {
            // obtem frequencia do termo do PageInfo (calculado pelo Downloader)
            int termFreq = pageInfo.getTermFrequency(term);

            if (termFreq == 0) {
                continue; // termo nao esta no documento
            }

            // TF (normalizado): frequencia / frequencia maxima no documento
            // usar frequencia bruta e mais simples e funciona bem
            double tf = termFreq;

            // IDF: log(total docs / docs contendo termo)
            Set<Integer> docsWithTerm = invertedIndex.get(term);
            int docsContainingTerm = (docsWithTerm != null) ? docsWithTerm.size() : 1;
            double idf = Math.log((double) totalDocs / docsContainingTerm);

            // TF-IDF para este termo
            score += tf * idf;
        }

        return score;
    }

    @Override
    public List<String> getInlinks(String url) throws RemoteException {
        // converte URL para document ID
        Integer docId = urlToDocId.get(url);
        if (docId == null) {
            // URL nao indexado
            return new ArrayList<>();
        }

        PageInfo pageInfo = pageDatabase.get(docId);
        if (pageInfo == null) {
            return new ArrayList<>();
        }

        // converte inlink docIds de volta para URLs para a API
        return pageInfo.getInlinks().stream()
                .map(this::getUrlForDocId)
                .collect(Collectors.toList());
    }

    /**
     * carrega stopwords de data/stopwords-en.txt e data/stopwords-pt.txt
     */
    @Override
    public BarrelStats getStats() throws RemoteException {
        // tempo de resposta agora e gerido pelo Gateway
        // inclui estado de sincronizacao e informacao de peers para estatisticas ao
        // vivo
        // usa getIndexSize() para contar apenas paginas com conteudo (nao placeholders)
        return new BarrelStats(
                barrelId,
                getIndexSize(),
                0.0,
                isSyncing,
                syncPeerBarrelId,
                syncProgress);
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    @Override
    public boolean isReady() throws RemoteException {
        // barrel esta pronto se nao estiver a sincronizar
        return !isSyncing && syncCompleted;
    }

    @Override
    public int getIndexSize() throws RemoteException {
        // conta apenas paginas com conteudo real (nao placeholders vazios para
        // backlinks)
        int count = 0;
        int totalPages = pageDatabase.size();
        for (PageInfo page : pageDatabase.values()) {
            if (page.getWords() != null && !page.getWords().isEmpty()) {
                count++;
            }
        }

        // debug: mostra log se houver diferenca significativa
        if (totalPages - count > 10) {
            // System.out.println("[" + barrelId + "] [DEBUG] getIndexSize: " + count + "
            // pages with content out of " + totalPages + " total entries (" + (totalPages -
            // count) + " placeholders)");
        }

        return count;
    }

    /**
     * grava o indice para disco usando formato JSON (simples e confiavel)
     */
    private void saveIndexToDisk() {
        if (!useJsonIndex) {
            System.out.println("[" + barrelId + "] JSON index disabled, skipping save to disk");
            return;
        }

        String filename = "data/barrel_" + barrelId + "_index.json";
        System.out.println("[" + barrelId + "] Saving index to " + filename + "...");

        try {
            // garante que diretorio data existe
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            // cria snapshot num bloco sincronizado
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

                // grava APENAS paginas com conteudo real (exclui placeholders)
                pageDatabaseSnapshot = new HashMap<>();
                int placeholderCount = 0;
                for (Map.Entry<Integer, PageInfo> entry : pageDatabase.entrySet()) {
                    PageInfo page = entry.getValue();
                    if (page.getWords() != null && !page.getWords().isEmpty()) {
                        // pagina tem conteudo - grava
                        pageDatabaseSnapshot.put(entry.getKey(), page);
                    } else {
                        // placeholder - ignora
                        placeholderCount++;
                    }
                }

                if (placeholderCount > 0) {
                    System.out.println("[" + barrelId + "] Skipping " + placeholderCount +
                            " placeholder entries (saving only " + pageDatabaseSnapshot.size()
                            + " pages with content)");
                }

                urlToDocIdSnapshot = new HashMap<>(urlToDocId);
                docIdToUrlSnapshot = new HashMap<>(docIdToUrl);
                nextDocIdSnapshot = nextDocId.get();

                // debug: verifica se objetos PageInfo tem dados incluindo frequencias de termos
                for (Map.Entry<Integer, PageInfo> entry : pageDatabaseSnapshot.entrySet()) {
                    PageInfo pi = entry.getValue();
                    if (entry.getKey() <= 3) { // mostra log das primeiras 3 paginas
                        int tfSize = (pi.getTermFrequencies() != null) ? pi.getTermFrequencies().size() : 0;
                        /*
                         * System.out.println("[" + barrelId + "] [DEBUG] Saving PageInfo docId=" +
                         * entry.getKey() +
                         * " words=" + pi.getWords().size() +
                         * " termFreqs=" + tfSize +
                         * " inlinks=" + pi.getInlinks().size() +
                         * " outlinks=" + pi.getOutlinks().size());
                         */
                    }
                }
            }

            // cria estrutura de dados JSON
            Map<String, Object> jsonData = new HashMap<>();
            jsonData.put("barrelId", barrelId);
            jsonData.put("invertedIndex", invertedIndexSnapshot);
            jsonData.put("pageDatabase", pageDatabaseSnapshot);
            jsonData.put("urlToDocId", urlToDocIdSnapshot);
            jsonData.put("docIdToUrl", docIdToUrlSnapshot);
            jsonData.put("nextDocId", nextDocIdSnapshot);

            // escreve para ficheiro usando Gson
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
     * carrega o indice do disco usando formato JSON
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
                // le JSON
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonData = gson.fromJson(reader, Map.class);

                // restaura mapeamentos de URL para docId
                @SuppressWarnings("unchecked")
                Map<String, Number> urlToDocIdData = (Map<String, Number>) jsonData.get("urlToDocId");

                urlToDocId.clear();
                if (urlToDocIdData != null) {
                    for (Map.Entry<String, Number> entry : urlToDocIdData.entrySet()) {
                        urlToDocId.put(entry.getKey(), entry.getValue().intValue());
                    }
                }

                // restaura mapeamentos de docId para URL
                @SuppressWarnings("unchecked")
                Map<String, String> docIdToUrlData = (Map<String, String>) jsonData.get("docIdToUrl");

                docIdToUrl.clear();
                if (docIdToUrlData != null) {
                    for (Map.Entry<String, String> entry : docIdToUrlData.entrySet()) {
                        // chaves JSON sao sempre strings, converte de volta para Integer
                        docIdToUrl.put(Integer.parseInt(entry.getKey()), entry.getValue());
                    }
                }

                // restaura contador nextDocId
                Number nextDocIdValue = (Number) jsonData.get("nextDocId");
                if (nextDocIdValue != null) {
                    nextDocId.set(nextDocIdValue.intValue());
                }

                // restaura indice invertido (agora com docIds Integer)
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

                // restaura base de dados de paginas (agora chaveado por docId Integer)
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> pageDatabaseData = (Map<String, Map<String, Object>>) jsonData
                        .get("pageDatabase");

                pageDatabase.clear();
                if (pageDatabaseData != null) {
                    for (Map.Entry<String, Map<String, Object>> entry : pageDatabaseData.entrySet()) {
                        // chaves JSON sao sempre strings, converte de volta para Integer
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

                        // restaura palavras (Set<String>)
                        if (pageData.get("words") != null) {
                            @SuppressWarnings("unchecked")
                            List<String> wordsData = (List<String>) pageData.get("words");
                            for (String word : wordsData) {
                                pageInfo.addWord(word);
                            }
                        }

                        // restaura inlinks (Set<Integer>)
                        if (pageData.get("inlinks") != null) {
                            @SuppressWarnings("unchecked")
                            List<Number> inlinksData = (List<Number>) pageData.get("inlinks");
                            for (Number inlinkDocId : inlinksData) {
                                pageInfo.addInlink(inlinkDocId.intValue());
                            }
                        }

                        // restaura outlinks (Set<Integer>)
                        if (pageData.get("outlinks") != null) {
                            @SuppressWarnings("unchecked")
                            List<Number> outlinksData = (List<Number>) pageData.get("outlinks");
                            for (Number outlinkDocId : outlinksData) {
                                pageInfo.addOutlink(outlinkDocId.intValue());
                            }
                        }

                        // restaura termFrequencies (Map<String, Integer>)
                        if (pageData.get("termFrequencies") != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Number> termFreqData = (Map<String, Number>) pageData.get("termFrequencies");
                            Map<String, Integer> termFrequencies = new HashMap<>();
                            for (Map.Entry<String, Number> tfEntry : termFreqData.entrySet()) {
                                termFrequencies.put(tfEntry.getKey(), tfEntry.getValue().intValue());
                            }
                            pageInfo.setTermFrequencies(termFrequencies);
                        }

                        // restaura timestamp indexado (ou define como 0 para dados antigos)
                        if (pageData.get("indexedTimestamp") != null) {
                            Number timestamp = (Number) pageData.get("indexedTimestamp");
                            pageInfo.setIndexedTimestamp(timestamp.longValue());
                        } else {
                            // dados antigos sem timestamp - define como 0 (passado remoto)
                            pageInfo.setIndexedTimestamp(0);
                        }

                        pageDatabase.put(docId, pageInfo);

                        // debug: mostra log das primeiras 3 paginas carregadas
                        if (docId <= 3) {
                            int tfSize = (pageInfo.getTermFrequencies() != null) ? pageInfo.getTermFrequencies().size()
                                    : 0;
                            /*
                             * System.out.println("[" + barrelId + "] [DEBUG] Loaded PageInfo docId=" +
                             * docId +
                             * " words=" + pageInfo.getWords().size() +
                             * " termFreqs=" + tfSize +
                             * " inlinks=" + pageInfo.getInlinks().size() +
                             * " outlinks=" + pageInfo.getOutlinks().size());
                             */
                        }
                    }
                }

                // reconstroi inlinks a partir de outlinks (ja que nao gravamos placeholders)
                // isto reconstroi a estrutura de backlinks a partir das paginas gravadas
                System.out.println("[" + barrelId + "] Reconstructing inlinks from outlinks...");
                int inlinksReconstructed = 0;

                for (Map.Entry<Integer, PageInfo> entry : pageDatabase.entrySet()) {
                    int sourceDocId = entry.getKey();
                    PageInfo sourcePage = entry.getValue();

                    // para cada outlink desta pagina
                    for (Integer targetDocId : sourcePage.getOutlinks()) {
                        // obtem ou cria a pagina alvo (pode ser placeholder se ainda nao indexada)
                        String targetUrl = docIdToUrl.get(targetDocId);
                        if (targetUrl != null) {
                            PageInfo targetPage = pageDatabase.computeIfAbsent(targetDocId,
                                    k -> new PageInfo(targetUrl));
                            targetPage.addInlink(sourceDocId);
                            inlinksReconstructed++;
                        }
                    }
                }

                System.out.println("[" + barrelId + "] Reconstructed " + inlinksReconstructed + " inlinks");

                // conta paginas com conteudo real (exclui placeholders)
                int pagesWithContent = 0;
                for (PageInfo page : pageDatabase.values()) {
                    if (page.getWords() != null && !page.getWords().isEmpty()) {
                        pagesWithContent++;
                    }
                }

                System.out.println("[" + barrelId + "] Index loaded successfully (" +
                        pagesWithContent + " pages, " +
                        invertedIndex.size() + " terms)");

            }
        } catch (Exception e) {
            System.err.println("[" + barrelId + "] Failed to load index: " + e.getMessage());
            e.printStackTrace();
            System.out.println("[" + barrelId + "] Starting with empty index");
        }
    }

    @Override
    public String getBarrelId() throws RemoteException {
        return barrelId;
    }

    @Override
    public List<PageInfo> getPageChunkBeforeTimestamp(long beforeTimestamp, int offset, int limit)
            throws RemoteException {
        // otimiza: cria lista filtrada uma vez (cached para pedidos repetidos com mesmo
        // timestamp)
        // isto acontece de forma nao bloqueante e permite que outros pedidos prossigam
        List<PageInfo> eligiblePages = new ArrayList<>();

        // snapshot rapido e nao bloqueante de paginas antes do timestamp
        for (Map.Entry<Integer, PageInfo> entry : pageDatabase.entrySet()) {
            PageInfo page = entry.getValue();
            // inclui apenas paginas com conteudo real (nao placeholders)
            if (page.getWords() != null && !page.getWords().isEmpty() &&
                    page.getIndexedTimestamp() < beforeTimestamp) {
                // repovoa outlinkUrls a partir de outlinks para transferencia RMI
                // (outlinkUrls foram limpos apos indexacao inicial para poupar memoria)
                page.getOutlinkUrls().clear();
                for (Integer outlinkDocId : page.getOutlinks()) {
                    String outlinkUrl = getUrlForDocId(outlinkDocId);
                    if (outlinkUrl != null) {
                        page.getOutlinkUrls().add(outlinkUrl);
                    }
                }
                eligiblePages.add(page);
            }
        }

        // retorna o chunk pedido sem bloquear
        int startIdx = Math.min(offset, eligiblePages.size());
        int endIdx = Math.min(offset + limit, eligiblePages.size());

        if (startIdx >= eligiblePages.size()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(eligiblePages.subList(startIdx, endIdx));
    }

    @Override
    public int getIndexSizeBeforeTimestamp(long beforeTimestamp) throws RemoteException {
        int count = 0;
        for (PageInfo page : pageDatabase.values()) {
            // conta apenas paginas com conteudo real (nao placeholders)
            if (page.getWords() != null && !page.getWords().isEmpty() &&
                    page.getIndexedTimestamp() < beforeTimestamp) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void bulkImportPages(List<PageInfo> pages) throws RemoteException {
        System.out.println("[" + barrelId + "] Bulk importing " + pages.size() + " pages...");

        for (PageInfo pageInfo : pages) {
            try {
                // importa a pagina sem acionar a gravacao
                indexPageInternal(pageInfo, false);
            } catch (Exception e) {
                System.err.println(
                        "[" + barrelId + "] Error importing page " + pageInfo.getUrl() + ": " + e.getMessage());
            }
        }

        System.out.println("[" + barrelId + "] Bulk import complete");
    }

    /**
     * metodo interno de indexacao que pode opcionalmente saltar operacoes de
     * gravacao
     */
    private void indexPageInternal(PageInfo pageInfo, boolean triggerSave) throws RemoteException {
        String url = pageInfo.getUrl();
        int docId = getOrCreateDocId(url);

        // converte URLs de outlinks para docIds
        Set<Integer> outlinkDocIds = new HashSet<>();
        for (String outlinkUrl : pageInfo.getOutlinkUrls()) {
            int outlinkDocId = getOrCreateDocId(outlinkUrl);
            outlinkDocIds.add(outlinkDocId);
        }

        // verifica se pagina ja existe
        PageInfo existing = pageDatabase.get(docId);
        if (existing != null) {
            // atualiza pagina existente
            existing.setTitle(pageInfo.getTitle());
            existing.setTextSnippet(pageInfo.getTextSnippet());
            existing.setWords(pageInfo.getWords());
            existing.setTermFrequencies(pageInfo.getTermFrequencies());
            existing.getOutlinks().clear();
            existing.getOutlinks().addAll(outlinkDocIds);
        } else {
            // pagina nova
            pageInfo.getOutlinks().clear();
            pageInfo.getOutlinks().addAll(outlinkDocIds);
            pageDatabase.put(docId, pageInfo);
        }

        // atualiza indice invertido
        for (String word : pageInfo.getWords()) {
            invertedIndex.computeIfAbsent(word, k -> ConcurrentHashMap.newKeySet()).add(docId);
        }

        // atualiza backlinks (cria placeholders se necessario, tal como indexPage
        // regular)
        for (Integer outlinkDocId : outlinkDocIds) {
            String outlinkUrl = getUrlForDocId(outlinkDocId);
            if (outlinkUrl != null) {
                PageInfo targetPage = pageDatabase.computeIfAbsent(outlinkDocId, k -> new PageInfo(outlinkUrl));
                targetPage.addInlink(docId);
            }
        }

        if (triggerSave) {
            saveIndexToDisk();
        }
    }

    @Override
    public long getMostRecentPageTimestamp() throws RemoteException {
        long mostRecent = 0;
        for (PageInfo page : pageDatabase.values()) {
            // considera apenas paginas com conteudo real (nao placeholders)
            if (page.getWords() != null && !page.getWords().isEmpty() &&
                    page.getIndexedTimestamp() > mostRecent) {
                mostRecent = page.getIndexedTimestamp();
            }
        }
        return mostRecent;
    }

    /**
     * sincroniza o indice deste barrel com um peer barrel em background.
     * 
     * trata dois cenarios:
     * 1. indice vazio - sincroniza todas as paginas de antes do inicio do barrel
     * 2. indice desatualizado - sincroniza apenas paginas mais recentes que a nossa
     * pagina mais recente
     * 
     * importante: isto corre numa thread separada e NAO bloqueia a thread principal
     * do barrel
     * - este barrel pode continuar a aceitar novas paginas dos downloaders
     * - o peer barrel pode continuar a servir pesquisas e aceitar paginas
     * - chamadas RMI ao peer sao tratadas por threads RMI separadas no peer
     * - ambos os barrels permanecem totalmente operacionais durante sync
     */
    private void synchronizeWithPeer() {
        Thread syncThread = new Thread(() -> {
            try {
                isSyncing = true;
                syncProgress = 0;
                System.out.println("[" + barrelId + "] Starting background synchronization (non-blocking)...");

                // logica simplificada: encontra peer com maior indice e sincroniza para ele
                // conta apenas paginas com conteudo real (nao placeholders)
                int ourIndexSize = 0;
                for (PageInfo page : pageDatabase.values()) {
                    if (page.getWords() != null && !page.getWords().isEmpty()) {
                        ourIndexSize++;
                    }
                }
                System.out.println("[" + barrelId + "] Our current index size: " + ourIndexSize + " pages");

                // obtem referencia ao barrel registry
                String registryUrl = common.RMIConfig.getBarrelRegistryRMIUrl();
                IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup(registryUrl);

                // obtem todos os barrels
                List<IBarrel> allBarrels = registry.getAllBarrels();

                // encontra o peer barrel com o MAIOR indice
                // criterios simples:
                // 1. nao deve ser nos proprios
                // 2. deve estar pronto (nao sincronizando)
                // 3. deve ter mais paginas que nos
                IBarrel peerBarrel = null;
                String peerId = null;
                int peerIndexSize = 0;

                System.out.println("[" + barrelId + "] Evaluating " + allBarrels.size() + " potential peer barrels...");

                for (IBarrel barrel : allBarrels) {
                    String candidateId = barrel.getBarrelId();

                    // ignora nos proprios
                    if (candidateId.equals(barrelId)) {
                        continue;
                    }

                    try {
                        // verifica se este barrel esta pronto (nao sincronizando)
                        boolean isReady = barrel.isReady();
                        if (!isReady) {
                            System.out.println("[" + barrelId + "]   - Barrel " + candidateId +
                                    " is not ready (still syncing), skipping");
                            continue;
                        }

                        // obtem o tamanho do indice deste barrel
                        int candidateIndexSize = barrel.getIndexSize();

                        System.out.println("[" + barrelId + "]   - Barrel " + candidateId +
                                " is ready with " + candidateIndexSize + " pages");

                        // verifica se este peer tem mais paginas que nos
                        if (candidateIndexSize > ourIndexSize) {
                            System.out.println("[" + barrelId + "]   - Barrel " + candidateId +
                                    " has more pages (" + candidateIndexSize + " vs " + ourIndexSize + ")");

                            // seleciona peer com maior indice
                            if (candidateIndexSize > peerIndexSize) {
                                peerBarrel = barrel;
                                peerId = candidateId;
                                peerIndexSize = candidateIndexSize;
                                System.out.println("[" + barrelId + "]   - Selected as best peer so far");
                            }
                        } else {
                            System.out.println("[" + barrelId + "]   - Barrel " + candidateId +
                                    " has same or fewer pages, skipping");
                        }

                    } catch (Exception e) {
                        System.err.println("[" + barrelId + "]   - Error checking barrel " + candidateId +
                                ": " + e.getMessage());
                        continue;
                    }
                }

                if (peerBarrel == null) {
                    // nenhum peer tem mais paginas que nos - temos o maior indice!
                    System.out
                            .println("[" + barrelId + "] No peer has more pages than us - we have the largest index!");
                    System.out.println("[" + barrelId + "] Our index: " + ourIndexSize + " pages");
                    syncCompleted = true;
                    isSyncing = false;
                    syncPeerBarrelId = null;
                    syncProgress = 100;
                    return;
                }

                syncPeerBarrelId = peerId; // guarda peer ID para estatisticas
                System.out.println("[" + barrelId + "] Selected peer barrel: " + peerId +
                        " with " + peerIndexSize + " pages (we have " + ourIndexSize + " pages)");

                // obtem contagem de paginas indexadas ANTES deste barrel ter comecado
                // (filtragem
                // baseada em timestamp)
                int peerSizeBeforeStart = peerBarrel.getIndexSizeBeforeTimestamp(barrelStartTimestamp);
                System.out.println("[" + barrelId + "] Peer has " + peerSizeBeforeStart
                        + " pages indexed before barrel start (timestamp: " + barrelStartTimestamp + ")");
                System.out.println(
                        "[" + barrelId + "] Starting timestamp-based sync (peer remains fully operational)...");

                if (peerSizeBeforeStart == 0) {
                    System.out
                            .println("[" + barrelId + "] No pages to sync, peer index is empty or all pages are newer");
                    syncCompleted = true;
                    isSyncing = false;
                    syncPeerBarrelId = null;
                    syncProgress = 0;
                    return;
                }

                // sincroniza em chunks, apenas paginas de ANTES do inicio do barrel
                // nota: cada chamada RMI e tratada por uma thread separada no peer barrel
                // o peer barrel continua a servir outros pedidos em paralelo
                int chunkSize = 100;
                int offset = 0;
                int totalSynced = 0;

                while (offset < peerSizeBeforeStart) {
                    // pede apenas paginas indexadas antes deste barrel ter comecado
                    // esta chamada RMI usa uma das threads de tratamento RMI do peer
                    // outras threads no peer podem tratar pesquisas/indexacao simultaneamente
                    List<PageInfo> chunk = peerBarrel.getPageChunkBeforeTimestamp(barrelStartTimestamp, offset,
                            chunkSize);
                    if (chunk.isEmpty()) {
                        break;
                    }

                    // importa paginas localmente (acontece neste barrel, nao afeta peer)
                    bulkImportPages(chunk);
                    totalSynced += chunk.size();
                    offset += chunk.size();

                    syncProgress = (int) ((totalSynced * 100.0) / peerSizeBeforeStart);
                    System.out.println("[" + barrelId + "] Sync progress: " + syncProgress + "% (" + totalSynced + "/"
                            + peerSizeBeforeStart + ")");

                    // pequeno delay para ser gentil com rede e recursos do peer
                    Thread.sleep(50); // reduzido de 100ms para sync mais rapido
                }

                System.out.println("[" + barrelId + "] Synchronization complete! Synced " + totalSynced
                        + " pages from before barrel start");
                System.out.println("[" + barrelId + "] Peer barrel remained fully operational during sync");
                System.out.println(
                        "[" + barrelId + "] New pages from downloaders (after start) were indexed in parallel");
                saveIndexToDisk();

                syncCompleted = true;
                isSyncing = false;
                syncPeerBarrelId = null;
                syncProgress = 100;

            } catch (Exception e) {
                System.err.println("[" + barrelId + "] Synchronization failed: " + e.getMessage());
                e.printStackTrace();
                syncCompleted = true; // marca como completo mesmo se falhou, para permitir pesquisas
                isSyncing = false;
                syncPeerBarrelId = null;
                syncProgress = 0;
            }
        });

        syncThread.setName(barrelId + "-SyncThread");
        syncThread.setDaemon(true);
        syncThread.start();

        System.out.println(
                "[" + barrelId + "] Sync thread started in background, barrel continues accepting new pages...");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java barrel.StorageBarrel <barrel-id>");
            System.exit(1);
        }

        try {
            String barrelId = args[0];

            System.out.println("===========================================");
            System.out.println("   STORAGE BARREL " + barrelId + " STARTING");
            System.out.println("===========================================");
            common.RMIConfig.printConfig("Barrel-" + barrelId);
            System.out.println("===========================================");
            System.out.println();

            StorageBarrel barrel = new StorageBarrel(barrelId);

            // aguarda que Gateway esteja disponivel antes de fazer bind
            String gatewayUrl = common.RMIConfig.getGatewayRMIUrl();
            String baseUrl = common.RMIConfig.getRMIBaseUrl();

            System.out.println("Waiting for Gateway to be available at " + gatewayUrl + "...");
            boolean gatewayAvailable = false;

            while (!gatewayAvailable) {
                try {
                    // tenta fazer lookup do Gateway para verificar se registry RMI existe
                    java.rmi.Naming.lookup(gatewayUrl);
                    gatewayAvailable = true;
                    System.out.println("Gateway detected. Proceeding with registration...");
                } catch (Exception e) {
                    System.out.println("Gateway not ready yet. Waiting 3 seconds...");
                    Thread.sleep(3000);
                }
            }

            // agora faz bind ao registry RMI (Gateway esta a correr)
            String barrelBindUrl = baseUrl + "/Barrel" + barrelId;
            java.rmi.Naming.rebind(barrelBindUrl, barrel);

            System.out.println("Storage Barrel " + barrelId + " bound to " + barrelBindUrl);

            // define o timestamp de inicio AGORA - mesmo antes de aceitar pedidos de
            // paginas
            // isto garante que apenas sincronizamos paginas indexadas ANTES do barrel ficar
            // disponivel
            barrel.barrelStartTimestamp = System.currentTimeMillis();
            System.out.println(
                    "[" + barrelId + "] Barrel ready to accept pages (timestamp: " + barrel.barrelStartTimestamp + ")");

            // regista no BarrelRegistry para suporte multicast - tenta ate ter sucesso
            // e mantem monitorizacao da saude da conexao
            final boolean[] keepRunning = { true };
            Thread registrationThread = new Thread(() -> {
                System.out.println("Starting BarrelRegistry connection manager...");
                while (keepRunning[0]) {
                    boolean registered = false;

                    // fase 1: registo inicial (tenta ate ter sucesso)
                    System.out.println("Attempting to register with BarrelRegistry...");
                    while (!registered && keepRunning[0]) {
                        try {
                            // obtem referencia fresca do registry cada vez
                            String registryUrl = common.RMIConfig.getBarrelRegistryRMIUrl();
                            IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup(registryUrl);
                            registry.registerBarrel(barrelId, barrel);
                            registered = true;
                            System.out.println("Storage Barrel " + barrelId + " registered with BarrelRegistry");

                            // aciona sempre verificacao de sync - vai determinar se sync e necessario
                            // baseado em
                            // comparacao de timestamps
                            if (!barrel.syncCompleted) {
                                System.out
                                        .println("[" + barrelId + "] Checking if sync is needed with peer barrels...");
                                barrel.synchronizeWithPeer();
                            } else {
                                System.out.println("[" + barrelId + "] Sync already completed, skipping");
                            }

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

                    // fase 2: monitorizacao de saude (faz ping ao registry periodicamente)
                    System.out.println("Entering health monitoring loop...");
                    while (registered && keepRunning[0]) {
                        try {
                            Thread.sleep(5000); // verifica a cada 5 segundos
                            // obtem referencia fresca do registry para cada ping
                            String registryUrl = common.RMIConfig.getBarrelRegistryRMIUrl();
                            IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup(registryUrl);
                            registry.getBarrelCount(); // ping para verificar se ainda esta vivo
                            // se chegamos aqui, conexao ainda esta boa
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            System.out.println("Health monitoring interrupted, exiting...");
                            return;
                        } catch (Exception e) {
                            // gateway down - conexao perdida
                            System.err.println("Lost connection to BarrelRegistry! Gateway may have shutdown.");
                            System.err.println("Error: " + e.getMessage());
                            System.err.println("Will attempt to re-register...");
                            registered = false;
                            break; // sai da monitorizacao de saude, volta para fase de registo
                        }
                    }
                    System.out.println("Exited health monitoring loop. registered=" + registered + ", keepRunning="
                            + keepRunning[0]);
                }
            });
            registrationThread.setDaemon(false);
            registrationThread.start();

            System.out.println("Storage Barrel " + barrelId + " ready");

            // adiciona shutdown hook para remover registo do registry
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // faz flush aos streams de output para evitar problemas Maven/Jansi
                    System.out.flush();
                    System.err.flush();

                    System.out.println("\n\nShutting down Barrel " + barrelId + "...");

                    // PRIMEIRO: para de aceitar novos pedidos de indexacao
                    barrel.shuttingDown = true;
                    keepRunning[0] = false; // para thread de registo

                    // da um momento para pedidos em curso completarem
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // DEPOIS: grava indice para disco com timeout
                    Thread saveThread = new Thread(() -> {
                        try {
                            barrel.saveIndexToDisk();
                        } catch (Exception e) {
                            System.err.println("[" + barrelId + "] Error saving index: " + e.getMessage());
                        }
                    });

                    saveThread.start();

                    try {
                        // aguarda ate 10 segundos para gravacao completar
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
                        String registryUrl = common.RMIConfig.getBarrelRegistryRMIUrl();
                        IBarrelRegistry registry = (IBarrelRegistry) java.rmi.Naming.lookup(registryUrl);
                        registry.unregisterBarrel(barrelId);
                        System.out.println("Unregistered from BarrelRegistry");
                    } catch (Exception e) {
                        // ignora erros durante shutdown
                    }

                    try {
                        // remove bind do registry RMI
                        String unbindUrl = common.RMIConfig.getRMIBaseUrl() + "/Barrel" + barrelId;
                        java.rmi.Naming.unbind(unbindUrl);
                        System.out.println("Unbound from RMI registry");
                    } catch (Exception e) {
                        // ignora erros durante shutdown
                    }

                    try {
                        // faz unexport do barrel
                        UnicastRemoteObject.unexportObject(barrel, true);
                        System.out.println("Barrel unexported");
                    } catch (Exception e) {
                        // ignora erros durante shutdown
                    }

                    System.out.println("Barrel " + barrelId + " shutdown complete. Goodbye!");
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
            System.err.println("Barrel exception:");
            e.printStackTrace();
        }
    }
}
