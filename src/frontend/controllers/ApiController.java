package frontend.controllers;

import frontend.services.GatewayService;
import frontend.services.StatsUpdateService;
import frontend.services.OpenAIService;
import frontend.services.SpellCheckService;
import frontend.services.HackerNewsService;
import common.SearchResult;
import common.SystemStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * controlador REST API para acesso programático
 * permite integração com outras aplicações
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private StatsUpdateService statsUpdateService;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private SpellCheckService spellCheckService;

    @Autowired
    private HackerNewsService hackerNewsService;

    /**
     * endpoint para pesquisa via API
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "1") int page) {

        try {
            common.SearchResponse searchResponse = gatewayService.search(query, page);
            return ResponseEntity.ok(Map.of(
                    "query", query,
                    "page", page,
                    "totalCount", searchResponse.getTotalCount(),
                    "totalPages", searchResponse.getTotalPages(),
                    "results", searchResponse.getResults()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * endpoint para indexação de URL via API
     */
    @PostMapping("/index")
    public ResponseEntity<?> indexURL(@RequestBody Map<String, String> body) {
        try {
            String url = body.get("url");
            if (url == null || url.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "URL é obrigatório"));
            }

            gatewayService.indexURL(url);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "URL submetido para indexação",
                    "url", url));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * endpoint para obter estatísticas via API
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            SystemStats stats = statsUpdateService.getLastStats();
            if (stats == null) {
                stats = gatewayService.getSystemStats();
            }
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * endpoint para listar stories do HackerNews filtradas por termos
     */
    @GetMapping("/hackernews/stories")
    public ResponseEntity<?> getHackerNewsStories(
            @RequestParam("query") String query,
            @RequestParam(value = "maxStories", defaultValue = "10") int maxStories) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "query é obrigatório"));
            }

            List<HackerNewsService.HNStory> stories = hackerNewsService.getTopStoriesWithTerms(query, maxStories);
            return ResponseEntity.ok(Map.of(
                    "query", query,
                    "maxStories", maxStories,
                    "count", stories.size(),
                    "stories", stories));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * endpoint para disparar indexação de stories do HackerNews de forma assíncrona
     */
    @PostMapping("/hackernews/index")
    public ResponseEntity<?> indexHackerNewsStories(@RequestBody Map<String, Object> body) {
        try {
            String query = (String) body.get("query");
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "query é obrigatório"));
            }

            int maxUrls = 10;
            Object maxUrlsValue = body.get("maxUrls");
            if (maxUrlsValue instanceof Number) {
                maxUrls = ((Number) maxUrlsValue).intValue();
            } else if (maxUrlsValue instanceof String) {
                try {
                    maxUrls = Integer.parseInt((String) maxUrlsValue);
                } catch (NumberFormatException ignored) {
                }
            }

            hackerNewsService.indexTopStoriesWithTermsAsync(query, maxUrls);
            return ResponseEntity.accepted().body(Map.of(
                    "status", "accepted",
                    "query", query,
                    "maxUrls", maxUrls,
                    "message", "Indexação em segundo plano"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * endpoint para gerar análise AI assíncrona
     */
    @PostMapping("/ai-analysis")
    public ResponseEntity<?> generateAIAnalysis(@RequestBody Map<String, Object> body) {
        try {
            String query = (String) body.get("query");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> resultsData = (List<Map<String, String>>) body.get("results");

            if (query == null || resultsData == null || resultsData.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Query e resultados são obrigatórios"));
            }

            if (!openAIService.isAvailable()) {
                return ResponseEntity.ok(Map.of(
                        "analysis", "AI Analysis indisponível: API key não configurada"));
            }

            // Converte os resultados do Map para SearchResult
            List<SearchResult> results = resultsData.stream()
                    .map(r -> new SearchResult(
                            r.get("url"),
                            r.get("title"),
                            r.get("snippet"),
                            0 // relevance não é necessário para AI analysis
                    ))
                    .toList();

            String analysis = openAIService.generateAnalysis(query, results);
            return ResponseEntity.ok(Map.of("analysis", analysis));

        } catch (Exception e) {
            System.err.println("[ApiController] Erro na análise AI: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                    "analysis", "Erro ao gerar análise: " + e.getMessage()));
        }
    }

    /**
     * endpoint direto para testar o serviço OpenRouter / OpenAI
     */
    @PostMapping("/openrouter/test")
    public ResponseEntity<?> testOpenRouter(@RequestBody Map<String, Object> body) {
        try {
            String prompt = (String) body.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "prompt é obrigatório"));
            }

            int maxTokens = 300;
            Object maxTokensValue = body.get("maxTokens");
            if (maxTokensValue instanceof Number) {
                maxTokens = ((Number) maxTokensValue).intValue();
            } else if (maxTokensValue instanceof String) {
                try {
                    maxTokens = Integer.parseInt((String) maxTokensValue);
                } catch (NumberFormatException ignored) {
                }
            }

            if (!openAIService.isAvailable()) {
                return ResponseEntity.ok(Map.of(
                        "status", "unavailable",
                        "message", "OpenRouter API key não configurada"));
            }

            String openRouterResponse = openAIService.callOpenAI(prompt, maxTokens);
            if (openRouterResponse == null) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Resposta vazia da OpenRouter API"));
            }

            return ResponseEntity.ok(Map.of(
                    "prompt", prompt,
                    "maxTokens", maxTokens,
                    "response", openRouterResponse));
        } catch (Exception e) {
            System.err.println("[ApiController] Erro no teste OpenRouter: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * endpoint para obter páginas que apontam para um URL
     */
    @GetMapping("/links-to")
    public ResponseEntity<?> getLinksTo(@RequestParam("url") String url) {
        try {
            List<String> links = gatewayService.getPagesPointingTo(url);
            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "links", links));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * endpoint assíncrono para verificação ortográfica
     */
    @GetMapping("/spell-check")
    public ResponseEntity<?> spellCheck(@RequestParam("q") String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Query não pode estar vazia"));
            }

            String suggestion = spellCheckService.getSuggestion(query);

            Map<String, Object> response = new HashMap<>();
            response.put("query", query);

            if (suggestion != null && !suggestion.equalsIgnoreCase(query)) {
                response.put("hasSuggestion", true);
                response.put("suggestion", suggestion);
            } else {
                response.put("hasSuggestion", false);
                response.put("suggestion", null);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("[ApiController] Erro no spell check: " + e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "query", query,
                    "hasSuggestion", false,
                    "suggestion", null,
                    "error", e.getMessage()));
        }
    }

    /**
     * health check
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try {
            // tenta obter stats para verificar conexão RMI
            gatewayService.getSystemStats();
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "rmiConnection", "active"));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of(
                            "status", "unhealthy",
                            "error", e.getMessage()));
        }
    }
}
