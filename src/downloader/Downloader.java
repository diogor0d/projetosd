package downloader;

import common.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Downloader - Web Crawler que obtém e processa páginas
 * Múltiplos downloaders trabalham em paralelo
 */
public class Downloader implements Runnable {

    private String downloaderId;
    private BlockingQueue<String> urlQueue;
    private Set<String> visitedUrls;
    private List<IBarrel> barrels;
    private boolean running;

    private static final int MAX_DEPTH = 3; // Profundidade máxima de crawling
    private static final int TIMEOUT = 5000; // Timeout para conexões (ms)

    public Downloader(String downloaderId, BlockingQueue<String> urlQueue) {
        this.downloaderId = downloaderId;
        this.urlQueue = urlQueue;
        this.visitedUrls = Collections.synchronizedSet(new HashSet<>());
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
                // Obtém próximo URL da fila
                String url = urlQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);

                if (url != null && !visitedUrls.contains(url)) {
                    processURL(url);
                }

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

            // Marca como visitado
            visitedUrls.add(urlStr);

            // Obtém e analisa a página
            Document doc = Jsoup.connect(urlStr)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (compatible; GoogolBot/1.0)")
                    .get();

            PageInfo pageInfo = extractPageInfo(urlStr, doc);

            // Envia para todos os barrels (multicast)
            for (IBarrel barrel : barrels) {
                try {
                    barrel.indexPage(pageInfo);
                } catch (Exception e) {
                    System.err.println("[" + downloaderId + "] Failed to index on barrel: " + e.getMessage());
                }
            }

            // Adiciona links encontrados à fila
            for (String link : pageInfo.getOutlinks()) {
                if (!visitedUrls.contains(link)) {
                    urlQueue.offer(link);
                }
            }

        } catch (Exception e) {
            System.err.println("[" + downloaderId + "] Failed to process " + urlStr + ": " + e.getMessage());
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
        // Exemplo de uso
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        try {
            // Conecta aos barrels
            IBarrel barrel1 = (IBarrel) java.rmi.Naming.lookup("rmi://localhost/Barrel1");

            Downloader downloader = new Downloader("Downloader-1", queue);
            downloader.addBarrel(barrel1);

            // Adiciona URL inicial
            queue.offer("https://www.uc.pt");

            Thread thread = new Thread(downloader);
            thread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
