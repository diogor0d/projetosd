package common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * classe que representa informação sobre uma página Web indexada
 */
public class PageInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String url;
    private String title;
    private String textSnippet; // texto curto da página
    private Set<String> words; // coleçao de palavras encontradas na página
    private Map<String, Integer> termFrequencies; // TF: word -> frequency count (calculada pelo downloader)
    private Set<Integer> inlinks; // Document IDs que apontam para esta página (backlinks)
    private Set<Integer> outlinks; // Document IDs para onde esta página aponta
    private long indexedTimestamp; // Timestamp de quando a página foi indexada

    // Armazenamento temporário para URLs de outlinks (usado pelo Downloader,
    // enviado via RMI, então
    // convertido para docIds pelo barrel)
    private Set<String> outlinkUrls;

    public PageInfo(String url) {
        this.url = url;
        this.words = new HashSet<>();
        this.termFrequencies = new HashMap<>();
        this.inlinks = new HashSet<>();
        this.outlinks = new HashSet<>();
        this.outlinkUrls = new HashSet<>();
        this.indexedTimestamp = System.currentTimeMillis(); // definir a timestamp na criacao
    }

    // Getters e Setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTextSnippet() {
        return textSnippet;
    }

    public void setTextSnippet(String textSnippet) {
        this.textSnippet = textSnippet;
    }

    public Set<String> getWords() {
        return words;
    }

    public void setWords(Set<String> words) {
        this.words = words;
    }

    public void addWord(String word) {
        this.words.add(word);
    }

    public Map<String, Integer> getTermFrequencies() {
        return termFrequencies;
    }

    public void setTermFrequencies(Map<String, Integer> termFrequencies) {
        this.termFrequencies = termFrequencies;
    }

    public int getTermFrequency(String word) {
        return termFrequencies.getOrDefault(word, 0);
    }

    public Set<Integer> getInlinks() {
        return inlinks;
    }

    public void addInlink(Integer docId) {
        this.inlinks.add(docId);
    }

    public int getInlinkCount() {
        return inlinks.size();
    }

    public Set<Integer> getOutlinks() {
        return outlinks;
    }

    public void addOutlink(Integer docId) {
        this.outlinks.add(docId);
    }

    // Overloaded method para o Downloader adicionar outlinks como URLs (antes da
    // conversão para docIds)
    public void addOutlink(String outlinkUrl) {
        if (this.outlinkUrls == null) {
            this.outlinkUrls = new HashSet<>();
        }
        this.outlinkUrls.add(outlinkUrl);
    }

    public Set<String> getOutlinkUrls() {
        if (this.outlinkUrls == null) {
            this.outlinkUrls = new HashSet<>();
        }
        return outlinkUrls;
    }

    public long getIndexedTimestamp() {
        return indexedTimestamp;
    }

    public void setIndexedTimestamp(long indexedTimestamp) {
        this.indexedTimestamp = indexedTimestamp;
    }

    @Override
    public String toString() {
        return "PageInfo{" +
                "url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", inlinks=" + inlinks.size() +
                ", outlinks=" + outlinks.size() +
                ", words=" + words.size() +
                '}';
    }
}
