package frontend.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Serviço de verificação ortográfica usando AI (OpenAI)
 * Processamento assíncrono via API REST endpoint
 */
@Service
public class SpellCheckService {

    @Autowired
    private OpenAIService openAIService;

    /**
     * Verifica se uma consulta tem erros ortográficos e retorna sugestão usando AI
     */
    public String getSuggestion(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        // Usa AI para verificar ortografia
        if (openAIService.isAvailable()) {
            try {
                String aiSuggestion = getAISuggestion(query);
                if (aiSuggestion != null) {
                    System.out.println("[SpellCheck] AI suggestion: '" + query + "' -> '" + aiSuggestion + "'");
                    return aiSuggestion;
                }
            } catch (Exception e) {
                System.err.println("[SpellCheck] AI spell check failed: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Obtém sugestão usando AI (OpenAI)
     */
    private String getAISuggestion(String query) {
        try {
            String prompt = "You are a spelling checker for a Portuguese search engine.\n\n" +
                    "CRITICAL RULES:\n" +
                    "1. NEVER change proper nouns, names, surnames, or capitalized words\n" +
                    "2. If you see names like 'João', 'Maria', 'Silva', 'Rosmaninho', etc. - DO NOT CHANGE THEM\n" +
                    "3. Only fix obvious misspellings in common words\n" +
                    "4. Prefer Portuguese spellings over English when ambiguous\n" +
                    "5. If the query looks correct, respond ONLY with: CORRECT\n" +
                    "6. If you find errors, respond ONLY with the corrected text - NO arrows, NO explanations, NO original text\n\n" +
                    "WRONG response format:\n" +
                    "- 'gui rosmaninho' -> 'gui rosemaninho' ❌\n" +
                    "- gui rosmaninho -> gui rosemaninho ❌\n\n" +
                    "CORRECT response format:\n" +
                    "- CORRECT (if no errors)\n" +
                    "- computer science (if correcting 'compter science')\n\n" +
                    "Examples:\n" +
                    "Query: 'João Silva' → Response: CORRECT\n" +
                    "Query: 'gui rosmaninho' → Response: CORRECT\n" +
                    "Query: 'Maria Santos' → Response: CORRECT\n" +
                    "Query: 'compter science' → Response: computer science\n" +
                    "Query: 'coimb' → Response: coimbra\n" +
                    "Query: 'tecnologia' → Response: CORRECT\n\n" +
                    "Query: \"" + query + "\"\n" +
                    "Response:";

            String response = openAIService.callOpenAI(prompt, 80);

            if (response != null && !response.trim().isEmpty()) {
                String suggestion = response.trim();

                // Remove possível formatação incorreta da AI
                // Remove setas e texto original se a AI responder mal
                suggestion = suggestion.replaceAll(".*->\\s*", ""); // Remove "xxx -> "
                suggestion = suggestion.replaceAll(".*→\\s*", "");  // Remove "xxx → "
                suggestion = suggestion.replaceAll("^['\"`]|['\"`]$", ""); // Remove aspas
                suggestion = suggestion.trim();

                // Se a AI diz que está correto, retorna null
                if (suggestion.equalsIgnoreCase("CORRECT") ||
                    suggestion.equalsIgnoreCase("CORRECT.")) {
                    return null;
                }

                // Só retorna se for diferente do original (case-insensitive)
                if (!suggestion.equalsIgnoreCase(query.trim())) {
                    // Validação extra: se a sugestão ainda contém o texto original, é inválida
                    String lowerQuery = query.toLowerCase();
                    String lowerSuggestion = suggestion.toLowerCase();

                    // Se a sugestão começa com a query original seguida de algo, provavelmente é formato errado
                    if (lowerSuggestion.startsWith(lowerQuery + " ") ||
                        lowerSuggestion.contains("→") ||
                        lowerSuggestion.contains("->")) {
                        System.err.println("[SpellCheck] AI returned invalid format, ignoring: " + suggestion);
                        return null;
                    }

                    return suggestion;
                }
            }
        } catch (Exception e) {
            System.err.println("[SpellCheck] Error calling AI: " + e.getMessage());
        }

        return null;
    }
}

