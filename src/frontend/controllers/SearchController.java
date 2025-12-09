package frontend.controllers;

import frontend.services.GatewayService;
import frontend.services.HackerNewsService;
import frontend.services.OpenAIService;
import frontend.services.StatsUpdateService;
import common.SearchResult;
import common.SystemStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * controlador principal para as páginas do motor de pesquisa
 * implementa padrão MVC com Thymeleaf
 */
@Controller
public class SearchController {

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private HackerNewsService hackerNewsService;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private StatsUpdateService statsUpdateService;

    /**
     * página inicial
     */
    @GetMapping("/")
    public String home(Model model) {
        try {
            SystemStats stats = statsUpdateService.getLastStats();
            if (stats != null) {
                model.addAttribute("stats", stats);
            }
        } catch (Exception e) {
            System.err.println("[SearchController] Erro ao obter estatísticas: " + e.getMessage());
        }
        model.addAttribute("activePage", "home");
        return "index";
    }

    /**
     * processa pesquisa e apresenta resultados
     */
    @GetMapping("/search")
    public String search(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "aiAnalysis", defaultValue = "false") boolean aiAnalysis,
            @RequestParam(value = "indexHackerNews", defaultValue = "false") boolean indexHackerNews,
            Model model) {

        try {
            // realiza pesquisa via RMI
            List<SearchResult> results = gatewayService.search(query, page);

            model.addAttribute("query", query);
            model.addAttribute("results", results);
            model.addAttribute("page", page);
            model.addAttribute("hasResults", results != null && !results.isEmpty());

            // gera análise com IA se solicitado e há resultados
            if (aiAnalysis && results != null && !results.isEmpty()) {
                System.out.println("[SearchController] AI Analysis solicitado para query: " + query);
                if (openAIService.isAvailable()) {
                    System.out.println("[SearchController] OpenAI service está disponível");
                    String analysis = openAIService.generateAnalysis(query, results);
                    System.out.println("[SearchController] Análise gerada: "
                            + (analysis != null ? analysis.substring(0, Math.min(50, analysis.length())) + "..."
                                    : "null"));
                    model.addAttribute("aiAnalysis", analysis);
                } else {
                    System.out.println("[SearchController] OpenAI service NOT available");
                    model.addAttribute("aiAnalysis", "AI Analysis unavailable: API key not configured");
                }
            }

            // indexa stories do Hacker News se solicitado
            if (indexHackerNews) {
                try {
                    int indexed = hackerNewsService.indexTopStoriesWithTerms(query, 5);
                    model.addAttribute("hnIndexed", indexed);
                    model.addAttribute("hnMessage",
                            indexed + " Hacker News stories submitted for indexing");
                } catch (Exception e) {
                    System.err.println("[SearchController] Error indexing Hacker News: " + e.getMessage());
                    model.addAttribute("hnMessage", "Error indexing Hacker News stories");
                }
            }

        } catch (Exception e) {
            model.addAttribute("error", "Error performing search: " + e.getMessage());
            model.addAttribute("hasResults", false);
            System.err.println("[SearchController] Search error: " + e.getMessage());
        }

        model.addAttribute("activePage", "search");
        return "search";
    }

    /**
     * indexa um novo URL submetido pelo utilizador
     */
    @PostMapping("/index")
    public String indexURL(
            @RequestParam("url") String url,
            @RequestParam(value = "returnQuery", required = false) String returnQuery,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            RedirectAttributes redirectAttributes) {

        try {
            // valida URL básico
            if (url == null || url.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "URL cannot be empty");
                if (returnQuery != null && !returnQuery.trim().isEmpty()) {
                    return "redirect:/search?q=" + returnQuery;
                } else if (returnUrl != null && !returnUrl.trim().isEmpty()) {
                    return "redirect:/links-to?url=" + returnUrl;
                }
                return "redirect:/";
            }

            // adiciona esquema se não existir
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }

            // indexa de forma assíncrona
            gatewayService.indexURLAsync(url);
            redirectAttributes.addFlashAttribute("success",
                    "URL submitted for indexing: " + url + ". Processing in the background.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error indexing URL: " + e.getMessage());
            System.err.println("[SearchController] Indexing error: " + e.getMessage());
        }

        // Redirect back to search page if returnQuery is provided
        if (returnQuery != null && !returnQuery.trim().isEmpty()) {
            return "redirect:/search?q=" + returnQuery;
        }
        // Redirect back to links page if returnUrl is provided
        else if (returnUrl != null && !returnUrl.trim().isEmpty()) {
            return "redirect:/links-to?url=" + returnUrl;
        }
        return "redirect:/";
    }

    /**
     * indexa URLs do Hacker News relacionados com a pesquisa
     */
    @PostMapping("/index-hackernews")
    public String indexHackerNews(
            @RequestParam("query") String query,
            @RequestParam(value = "maxUrls", defaultValue = "10") int maxUrls,
            RedirectAttributes redirectAttributes) {

        try {
            // inicia indexação assíncrona
            hackerNewsService.indexTopStoriesWithTermsAsync(query, maxUrls);

            // retorna imediatamente com mensagem de sucesso
            redirectAttributes.addFlashAttribute("success",
                    "📰 Indexing HackerNews stories containing '" + query + "'. Processing in the background.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error starting Hacker News indexing: " + e.getMessage());
            System.err.println("[SearchController] HN indexing error: " + e.getMessage());
        }

        return "redirect:/";
    }

    /**
     * mostra páginas que apontam para um URL específico
     */
    @GetMapping("/links-to")
    public String linksTo(
            @RequestParam("url") String url,
            Model model) {

        try {
            List<String> links = gatewayService.getPagesPointingTo(url);
            model.addAttribute("targetUrl", url);
            model.addAttribute("links", links);
            model.addAttribute("hasLinks", links != null && !links.isEmpty());

        } catch (Exception e) {
            model.addAttribute("error", "Error retrieving links: " + e.getMessage());
            System.err.println("[SearchController] Error getting links: " + e.getMessage());
        }

        model.addAttribute("activePage", "links");
        return "links";
    }

    /**
     * página de estatísticas do sistema
     */
    @GetMapping("/stats")
    public String stats(Model model) {
        try {
            SystemStats stats = gatewayService.getSystemStats();
            model.addAttribute("stats", stats);

            // informação adicional da fila
            String queueStatus = gatewayService.getURLQueueStatus();
            model.addAttribute("queueStatus", queueStatus);

        } catch (Exception e) {
            model.addAttribute("error", "Error retrieving statistics: " + e.getMessage());
            System.err.println("[SearchController] Error getting stats: " + e.getMessage());
        }

        model.addAttribute("activePage", "stats");
        return "stats";
    }
}
