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
            String prompt = "Spell checker for Portuguese search engine. Rules:\n" +
                    "- NEVER change proper nouns/names/capitalized words\n" +
                    "- Only fix obvious misspellings in common words\n" +
                    "- Prefer Portuguese over English\n" +
                    "- If correct: reply CORRECT\n" +
                    "- If wrong: reply ONLY corrected text (no arrows/explanations)\n\n" +
                    "Examples:\n" +
                    "'João Silva' → CORRECT\n" +
                    "'compter science' → computer science\n" +
                    "'coimb' → coimbra\n\n" +
                    "Query: \"" + query + "\"\n" +
                    "Response:";

            String response = openAIService.callOpenAI(prompt, 80);

            if (response != null && !response.trim().isEmpty()) {
                String suggestion = response.trim();

                // remove possível formatação incorreta da AI
                // remove setas e texto original se a AI responder mal
                suggestion = suggestion.replaceAll(".*->\\s*", ""); // Remove "xxx -> "
                suggestion = suggestion.replaceAll(".*→\\s*", ""); // Remove "xxx → "
                suggestion = suggestion.replaceAll("^['\"`]|['\"`]$", ""); // Remove aspas
                suggestion = suggestion.trim();

                // se a AI diz que está correto, retorna null
                if (suggestion.equalsIgnoreCase("CORRECT") ||
                        suggestion.equalsIgnoreCase("CORRECT.")) {
                    return null;
                }

                // só retorna se for diferente do original (case-insensitive)
                if (!suggestion.equalsIgnoreCase(query.trim())) {
                    // Validação extra: se a sugestão ainda contém o texto original, é inválida
                    String lowerQuery = query.toLowerCase();
                    String lowerSuggestion = suggestion.toLowerCase();

                    // se a sugestão começa com a query original seguida de algo, provavelmente é
                    // formato errado
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
