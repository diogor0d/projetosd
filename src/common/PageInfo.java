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
    private Set<String> inlinks; // URLs que apontam para esta página (backlinks)
    private Set<String> outlinks; // URLs para onde esta página aponta

    public PageInfo(String url) {
        this.url = url;
        this.words = new HashSet<>();
        this.inlinks = new HashSet<>();
        this.outlinks = new HashSet<>();
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

    public Set<String> getInlinks() {
        return inlinks;
    }

    public void addInlink(String url) {
        this.inlinks.add(url);
    }

    public int getInlinkCount() {
        return inlinks.size();
    }

    public Set<String> getOutlinks() {
        return outlinks;
    }

    public void addOutlink(String url) {
        this.outlinks.add(url);
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
