package common;

import java.io.Serializable;
import java.util.List;

/**
 * Response object for search queries containing both results and metadata
 */
public class SearchResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<SearchResult> results;
    private int totalCount;
    private int page;
    private int pageSize;
    private long searchTimeMs;

    public SearchResponse() {
        this.pageSize = 10;
    }

    public SearchResponse(List<SearchResult> results, int totalCount, int page) {
        this.results = results;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = 10;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> results) {
        this.results = results;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getSearchTimeMs() {
        return searchTimeMs;
    }

    public void setSearchTimeMs(long searchTimeMs) {
        this.searchTimeMs = searchTimeMs;
    }

    public int getTotalPages() {
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    public boolean hasResults() {
        return results != null && !results.isEmpty();
    }

    public boolean hasMorePages() {
        return page < getTotalPages() - 1;
    }

    @Override
    public String toString() {
        return "SearchResponse{" +
                "totalCount=" + totalCount +
                ", page=" + page +
                ", pageSize=" + pageSize +
                ", resultsOnPage=" + (results != null ? results.size() : 0) +
                ", totalPages=" + getTotalPages() +
                '}';
    }
}
