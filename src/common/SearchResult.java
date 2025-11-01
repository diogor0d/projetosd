package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe que representa o resultado de uma pesquisa
 */
public class SearchResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String url;
    private String title;
    private String snippet;
    private int relevance; // Número de inlinks (used as tie-breaker)
    private double tfIdfScore; // TF-IDF relevance score

    public SearchResult(String url, String title, String snippet, int relevance, double tfIdfScore) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
        this.tfIdfScore = tfIdfScore;
    }

    // Backward compatibility constructor (for any code still using old constructor)
    public SearchResult(String url, String title, String snippet, int relevance) {
        this(url, title, snippet, relevance, 0.0);
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    public int getRelevance() {
        return relevance;
    }

    public double getTfIdfScore() {
        return tfIdfScore;
    }

    @Override
    public String toString() {
        return "Title: " + title + "\n" +
                "URL: " + url + "\n" +
                "Snippet: " + snippet + "\n" +
                "Score: " + String.format("%.4f", tfIdfScore) + " (TF-IDF)\n" +
                "Relevance: " + relevance + " inlinks\n";
    }
}
