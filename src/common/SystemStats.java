package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estatísticas gerais do sistema
 */
public class SystemStats implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<String> topSearches; // Top 10 pesquisas mais comuns
    private List<BarrelStats> barrelStats;
    private int totalIndexedPages;
    private int urlQueueSize;

    public SystemStats() {
        this.topSearches = new ArrayList<>();
        this.barrelStats = new ArrayList<>();
    }

    public List<String> getTopSearches() {
        return topSearches;
    }

    public void setTopSearches(List<String> topSearches) {
        this.topSearches = topSearches;
    }

    public List<BarrelStats> getBarrelStats() {
        return barrelStats;
    }

    public void setBarrelStats(List<BarrelStats> barrelStats) {
        this.barrelStats = barrelStats;
    }

    public int getTotalIndexedPages() {
        return totalIndexedPages;
    }

    public void setTotalIndexedPages(int totalIndexedPages) {
        this.totalIndexedPages = totalIndexedPages;
    }

    public int getUrlQueueSize() {
        return urlQueueSize;
    }

    public void setUrlQueueSize(int urlQueueSize) {
        this.urlQueueSize = urlQueueSize;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SYSTEM STATISTICS ===\n");
        sb.append("Total Indexed Pages: ").append(totalIndexedPages).append("\n");
        sb.append("Active Barrels: ").append(barrelStats.size()).append("\n");
        sb.append("URL Queue Size: ").append(urlQueueSize).append("\n\n");

        sb.append("Top 10 Searches:\n");
        for (int i = 0; i < topSearches.size(); i++) {
            sb.append((i + 1)).append(". ").append(topSearches.get(i)).append("\n");
        }

        sb.append("\nStorage Barrels:\n");
        for (BarrelStats bs : barrelStats) {
            sb.append(bs.toString()).append("\n");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        SystemStats that = (SystemStats) obj;

        if (totalIndexedPages != that.totalIndexedPages)
            return false;
        if (urlQueueSize != that.urlQueueSize)
            return false;
        if (!topSearches.equals(that.topSearches))
            return false;
        return barrelStats.equals(that.barrelStats);
    }

    @Override
    public int hashCode() {
        int result = topSearches.hashCode();
        result = 31 * result + barrelStats.hashCode();
        result = 31 * result + totalIndexedPages;
        result = 31 * result + urlQueueSize;
        return result;
    }
}
