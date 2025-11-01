package common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Classe que representa informação sobre uma página Web indexada
 */
public class PageInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String url;
    private String title;
    private String textSnippet; // Citação de texto
    private Set<String> words; // Palavras encontradas na página
    private Set<Integer> inlinks; // Document IDs que apontam para esta página (backlinks)
    private Set<Integer> outlinks; // Document IDs para onde esta página aponta

    // Temporary storage for outlink URLs (used by Downloader, sent via RMI, then
    // converted to docIds by barrel)
    private Set<String> outlinkUrls;

    public PageInfo(String url) {
        this.url = url;
        this.words = new HashSet<>();
        this.inlinks = new HashSet<>();
        this.outlinks = new HashSet<>();
        this.outlinkUrls = new HashSet<>();
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

    // Overloaded method for Downloader to add outlinks as URLs (before conversion
    // to docIds)
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
