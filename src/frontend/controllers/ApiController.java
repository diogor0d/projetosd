package frontend.controllers;

import frontend.services.GatewayService;
import frontend.services.StatsUpdateService;
import common.SearchResult;
import common.SystemStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * endpoint para pesquisa via API
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "1") int page) {

        try {
            List<SearchResult> results = gatewayService.search(query, page);
            return ResponseEntity.ok(Map.of(
                    "query", query,
                    "page", page,
                    "results", results));
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
