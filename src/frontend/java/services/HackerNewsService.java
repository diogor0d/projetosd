package frontend.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * serviço para integração com a API REST do Hacker News
 * busca top stories e filtra por termos de pesquisa
 */
@Service
public class HackerNewsService {

    private static final String HN_API_BASE = "https://hacker-news.firebaseio.com/v0";
    private static final String TOP_STORIES_ENDPOINT = HN_API_BASE + "/topstories.json";
    private static final String ITEM_ENDPOINT = HN_API_BASE + "/item/%d.json";

    private final RestTemplate restTemplate;
    private final Gson gson;

    @Autowired
    private GatewayService gatewayService;

    public HackerNewsService() {
        this.restTemplate = new RestTemplate();
        this.gson = new Gson();
    }

    /**
     * representa uma story do Hacker News
     */
    public static class HNStory {
        public String title;
        public String url;
        public int score;
        public String by;
        public long time;

        public HNStory(String title, String url, int score, String by, long time) {
            this.title = title;
            this.url = url;
            this.score = score;
            this.by = by;
            this.time = time;
        }
    }

    /**
     * obtém top stories do Hacker News que contenham os termos especificados
     * 
     * @param searchTerms termos de pesquisa para filtrar stories
     * @param maxStories  número máximo de stories a retornar
     * @return lista de stories que contêm os termos
     */
    public List<HNStory> getTopStoriesWithTerms(String searchTerms, int maxStories) {
        List<HNStory> matchingStories = new ArrayList<>();

        try {
            // busca IDs das top stories
            String topStoriesJson = restTemplate.getForObject(TOP_STORIES_ENDPOINT, String.class);
            JsonArray storyIds = gson.fromJson(topStoriesJson, JsonArray.class);

            String[] terms = searchTerms.toLowerCase().split("\\s+");
            int checked = 0;
            int maxStoriesToCheck = 200; // pool de historias para encontrar mais matches

            // itera sobre as stories até encontrar o número desejado de matches
            for (int i = 0; i < storyIds.size() && matchingStories.size() < maxStories
                    && checked < maxStoriesToCheck; i++) {
                checked++;
                int storyId = storyIds.get(i).getAsInt();

                try {
                    String itemUrl = String.format(ITEM_ENDPOINT, storyId);
                    String itemJson = restTemplate.getForObject(itemUrl, String.class);
                    JsonObject item = gson.fromJson(itemJson, JsonObject.class);

                    // verifica se é uma story (tem URL) e não apenas um comentário
                    if (item.has("url") && item.has("title")) {
                        String title = item.get("title").getAsString();
                        String url = item.get("url").getAsString();

                        // verifica se o título contém algum dos termos de pesquisa
                        boolean matches = false;
                        String titleLower = title.toLowerCase();
                        for (String term : terms) {
                            if (titleLower.contains(term)) {
                                matches = true;
                                break;
                            }
                        }

                        if (matches) {
                            int score = item.has("score") ? item.get("score").getAsInt() : 0;
                            String by = item.has("by") ? item.get("by").getAsString() : "unknown";
                            long time = item.has("time") ? item.get("time").getAsLong() : 0;

                            matchingStories.add(new HNStory(title, url, score, by, time));
                        }
                    }
                } catch (Exception e) {
                    // continua se falhar ao buscar uma story específica
                    System.err.println("[HackerNewsService] Erro ao buscar story " + storyId + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[HackerNewsService] Erro ao buscar top stories: " + e.getMessage());
        }

        return matchingStories;
    }

    /**
     * indexa URLs das top stories do Hacker News que contenham os termos
     * executa de forma assíncrona para não bloquear o utilizador
     * 
     * @param searchTerms termos para filtrar stories
     * @param maxUrls     número máximo de URLs a indexar
     * @return número de URLs indexados
     */
    @Async
    public CompletableFuture<Integer> indexTopStoriesWithTermsAsync(String searchTerms, int maxUrls) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<HNStory> stories = getTopStoriesWithTerms(searchTerms, maxUrls);

                int indexed = 0;
                for (HNStory story : stories) {
                    try {
                        System.out.println("[HackerNews] Indexing: " + story.title + " (" + story.url + ")");
                        gatewayService.indexURL(story.url);
                        indexed++;
                    } catch (Exception e) {
                        System.err.println(
                                "[HackerNewsService] Erro ao indexar URL " + story.url + ": " + e.getMessage());
                    }
                }

                System.out.println("[HackerNewsService] Submissão para indexação concluída: " + indexed + " URLs de "
                        + stories.size()
                        + " stories");
                return indexed;
            } catch (Exception e) {
                System.err.println("[HackerNewsService] Erro na indexação assíncrona: " + e.getMessage());
                return 0;
            }
        });
    }

    /**
     * indexa URLs das top stories do Hacker News que contenham os termos
     * 
     * @param searchTerms termos para filtrar stories
     * @param maxUrls     número máximo de URLs a indexar
     * @return número de URLs indexados
     */
    public int indexTopStoriesWithTerms(String searchTerms, int maxUrls) throws Exception {
        List<HNStory> stories = getTopStoriesWithTerms(searchTerms, maxUrls);

        int indexed = 0;
        for (HNStory story : stories) {
            try {
                System.out.println("[HackerNews] Indexing: " + story.title + " (" + story.url + ")");
                gatewayService.indexURL(story.url);
                indexed++;
            } catch (Exception e) {
                System.err.println("[HackerNewsService] Erro ao indexar URL " + story.url + ": " + e.getMessage());
            }
        }

        return indexed;
    }
}
