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

    @Value("${openrouter.site.url:http://localhost:8080}")
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
            try {
                // tenta ler de key.openrouterai.txt
                Path keyFile = Path.of("key.openrouterai.txt");
                if (Files.exists(keyFile)) {
                    apiKey = Files.readString(keyFile).trim();
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
            return "Análise indisponível: chave da API OpenRouter não configurada. " +
                    "Configure a chave em application.properties (openrouter.api.key) ou no ficheiro key.openrouterai.txt";
        }

        try {
            // constrói o prompt com base nos resultados
            StringBuilder prompt = new StringBuilder();
            prompt.append("Analisa os seguintes resultados de pesquisa para os termos '")
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

            prompt.append("Fornece uma análise breve (2-3 parágrafos) sobre o que estes resultados revelam ")
                    .append("sobre o tema pesquisado, identificando padrões e insights principais.");

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

            return "Erro ao processar resposta da API OpenRouter.";

        } catch (Exception e) {
            System.err.println("[OpenAIService] Erro ao gerar análise: " + e.getMessage());
            e.printStackTrace();
            return "Erro ao gerar análise: " + e.getMessage();
        }
    }

    /**
     * verifica se o serviço está configurado e disponível
     */
    public boolean isAvailable() {
        String key = getApiKey();
        return key != null && !key.isEmpty();
    }
}
