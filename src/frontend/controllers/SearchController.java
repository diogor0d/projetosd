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
                    System.out.println("[SearchController] Análise gerada: " + (analysis != null ? analysis.substring(0, Math.min(50, analysis.length())) + "..." : "null"));
                    model.addAttribute("aiAnalysis", analysis);
                } else {
                    System.out.println("[SearchController] OpenAI service NÃO está disponível");
                    model.addAttribute("aiAnalysis", "AI Analysis indisponível: API key não configurada");
                }
            }

            // indexa stories do Hacker News se solicitado
            if (indexHackerNews) {
                try {
                    int indexed = hackerNewsService.indexTopStoriesWithTerms(query, 5);
                    model.addAttribute("hnIndexed", indexed);
                    model.addAttribute("hnMessage",
                            indexed + " stories do Hacker News foram submetidas para indexação");
                } catch (Exception e) {
                    System.err.println("[SearchController] Erro ao indexar Hacker News: " + e.getMessage());
                    model.addAttribute("hnMessage", "Erro ao indexar stories do Hacker News");
                }
            }

        } catch (Exception e) {
            model.addAttribute("error", "Erro ao realizar pesquisa: " + e.getMessage());
            model.addAttribute("hasResults", false);
            System.err.println("[SearchController] Erro na pesquisa: " + e.getMessage());
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
            RedirectAttributes redirectAttributes) {

        try {
            // valida URL básico
            if (url == null || url.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "URL não pode estar vazio");
                return "redirect:/";
            }

            // adiciona esquema se não existir
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }

            gatewayService.indexURL(url);
            redirectAttributes.addFlashAttribute("success", "URL submetido para indexação: " + url);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erro ao indexar URL: " + e.getMessage());
            System.err.println("[SearchController] Erro ao indexar: " + e.getMessage());
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
            int indexed = hackerNewsService.indexTopStoriesWithTerms(query, maxUrls);
            redirectAttributes.addFlashAttribute("success",
                    indexed + " URLs do Hacker News submetidos para indexação");
            redirectAttributes.addFlashAttribute("searchQuery", query);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Erro ao indexar Hacker News: " + e.getMessage());
            System.err.println("[SearchController] Erro ao indexar HN: " + e.getMessage());
        }

        return "redirect:/search?q=" + query;
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
            model.addAttribute("error", "Erro ao obter ligações: " + e.getMessage());
            System.err.println("[SearchController] Erro ao obter links: " + e.getMessage());
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
            model.addAttribute("error", "Erro ao obter estatísticas: " + e.getMessage());
            System.err.println("[SearchController] Erro ao obter stats: " + e.getMessage());
        }

        model.addAttribute("activePage", "stats");
        return "stats";
    }
}
