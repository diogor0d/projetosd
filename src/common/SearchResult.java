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
    private int relevance; // Número de inlinks

    public SearchResult(String url, String title, String snippet, int relevance) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
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

    @Override
    public String toString() {
        return "Title: " + title + "\n" +
                "URL: " + url + "\n" +
                "Snippet: " + snippet + "\n" +
                "Relevance: " + relevance + " inlinks\n";
    }
}
