package frontend.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import common.SearchResult;

/**
 * serviço para integração com API do OpenRouter.ai
 * gera análises contextualizadas dos resultados de pesquisa usando modelos LLM
 */
@Service
public class OpenAIService {

    // openrouter.ai é compatível com a API OpenAI mas oferece múltiplos modelos
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";

    @Value("${openrouter.api.key:}")
    private String apiKey;

    @Value("${openrouter.model:meta-llama/llama-3.1-8b-instruct:free}")
    private String model;

    @Value("${openrouter.site.url:http://localhost:8081}")
    private String siteUrl;

    @Value("${openrouter.site.name:Googol Search Engine}")
    private String siteName;

    private final RestTemplate restTemplate;
    private final Gson gson;

    public OpenAIService() {
        this.restTemplate = new RestTemplate();
        this.gson = new Gson();
    }

    /**
     * carrega a chave da API de um ficheiro se não estiver configurada
     */
    private String getApiKey() {
        if (apiKey == null || apiKey.isEmpty()) {
            // 1) check environment variable
            String envKey = System.getenv("OPENROUTER_API_KEY");
            if (envKey != null && !envKey.isEmpty()) {
                apiKey = envKey.trim();
                return apiKey;
            }

            // 2) procurar pelo ficheiro em varias localizacoes aceitaveis
            try {
                java.util.List<Path> candidates = java.util.List.of(
                        Path.of("config", "key.openrouterai.txt"),
                        Path.of("config", "key.openrouter.txt"),
                        Path.of("key.openrouterai.txt"),
                        Path.of("key.openrouter.txt"));

                for (Path keyFile : candidates) {
                    if (Files.exists(keyFile)) {
                        apiKey = Files.readString(keyFile).trim();
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("[OpenAIService] Erro ao ler ficheiro de chave: " + e.getMessage());
            }
        }
        return apiKey;
    }

    /**
     * gera uma análise contextualizada usando a API do OpenRouter.ai
     * 
     * @param searchTerms termos da pesquisa
     * @param results     resultados da pesquisa
     * @return análise textual gerada pela IA
     */
    public String generateAnalysis(String searchTerms, List<SearchResult> results) {
        String key = getApiKey();
        if (key == null || key.isEmpty()) {
            return "Analysis unavailable: OpenRouter API key not configured. " +
                    "Set the key in application.properties (openrouter.api.key) or in key.openrouterai.txt file";
        }

        try {
            // constrói o prompt com base nos resultados
            StringBuilder prompt = new StringBuilder();
            prompt.append("Analyze the following search results for the terms '")
                    .append(searchTerms)
                    .append("':\n\n");

            int count = Math.min(5, results.size()); // usa apenas os primeiros 5 resultados
            for (int i = 0; i < count; i++) {
                SearchResult result = results.get(i);
                prompt.append(i + 1).append(". ")
                        .append(result.getTitle())
                        .append("\n   ")
                        .append(result.getSnippet())
                        .append("\n\n");
            }

            prompt.append(
                    "You are search engine summary provider part of an experimental search engine (for academic purposes). Provide a brief analysis (2-3 paragraphs) about what these results reveal about the searched topic, identifying main patterns and insights. You have to keep in mind that these results are returned from a page index that may not be big enough to provide useful results.");

            // constrói o pedido JSON para a API OpenRouter
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);

            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt.toString());
            messages.add(message);

            requestBody.add("messages", messages);
            requestBody.addProperty("max_tokens", 500);
            requestBody.addProperty("temperature", 0.7);

            // configura headers com autenticação e metadata do OpenRouter
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);
            // headers específicos do OpenRouter para tracking e rate limits
            headers.set("HTTP-Referer", siteUrl);
            headers.set("X-Title", siteName);

            HttpEntity<String> request = new HttpEntity<>(gson.toJson(requestBody), headers);

            System.out.println("[OpenAIService] Enviando pedido para OpenRouter.ai com modelo: " + model);

            // faz o pedido à API
            String response = restTemplate.postForObject(OPENROUTER_API_URL, request, String.class);

            // extrai a resposta do JSON
            JsonObject responseJson = gson.fromJson(response, JsonObject.class);
            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject messageObj = firstChoice.getAsJsonObject("message");
                return messageObj.get("content").getAsString().trim();
            }

            return "Error processing OpenRouter API response.";

        } catch (Exception e) {
            System.err.println("[OpenAIService] Error generating analysis: " + e.getMessage());
            e.printStackTrace();
            return "Error generating analysis: " + e.getMessage();
        }
    }

    /**
     * verifica se o serviço está configurado e disponível
     */
    public boolean isAvailable() {
        String key = getApiKey();
        return key != null && !key.isEmpty();
    }

    /**
     * método genérico para chamar a API do OpenRouter
     *
     * @param prompt    o prompt para enviar à IA
     * @param maxTokens número máximo de tokens na resposta
     * @return resposta da IA
     */
    public String callOpenAI(String prompt, int maxTokens) {
        String key = getApiKey();
        if (key == null || key.isEmpty()) {
            return null;
        }

        try {
            // constrói o pedido JSON para a API OpenRouter
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);

            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);

            requestBody.add("messages", messages);
            requestBody.addProperty("max_tokens", maxTokens);
            requestBody.addProperty("temperature", 0.3); // Lower temperature for more deterministic responses

            // configura headers com autenticação e metadata do OpenRouter
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);
            headers.set("HTTP-Referer", siteUrl);
            headers.set("X-Title", siteName);

            HttpEntity<String> request = new HttpEntity<>(gson.toJson(requestBody), headers);

            // faz o pedido à API
            String response = restTemplate.postForObject(OPENROUTER_API_URL, request, String.class);

            // extrai a resposta do JSON
            JsonObject responseJson = gson.fromJson(response, JsonObject.class);
            JsonArray choices = responseJson.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject messageObj = firstChoice.getAsJsonObject("message");
                return messageObj.get("content").getAsString().trim();
            }

            return null;

        } catch (Exception e) {
            System.err.println("[OpenAIService] Error calling API: " + e.getMessage());
            return null;
        }
    }
}
