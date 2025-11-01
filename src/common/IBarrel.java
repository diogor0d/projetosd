package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * Interface RMI para o Storage Barrel
 */
public interface IBarrel extends Remote {

    /**
     * Indexa informação de uma página (chamado pelos Downloaders)
     */
    void indexPage(PageInfo pageInfo) throws RemoteException;

    /**
     * Pesquisa por um conjunto de termos
     * 
     * @param terms Termos de pesquisa
     * @param page  Número da página (grupos de 10)
     * @return Lista de resultados ordenados por relevância
     */
    List<SearchResult> search(Set<String> terms, int page) throws RemoteException;

    /**
     * Obtém lista de páginas que apontam para um URL específico
     */
    List<String> getInlinks(String url) throws RemoteException;

    /**
     * Retorna estatísticas do barrel
     */
    BarrelStats getStats() throws RemoteException;

    /**
     * Verifica se o barrel está ativo
     */
    boolean ping() throws RemoteException;

    /**
     * Verifica se o barrel está pronto para processar pesquisas
     * (não está sincronizando)
     */
    boolean isReady() throws RemoteException;

    /**
     * Obtém o tamanho do índice (número de URLs indexados)
     */
    int getIndexSize() throws RemoteException;

    /**
     * Gets the barrel's unique identifier
     */
    String getBarrelId() throws RemoteException;

    /**
     * Gets a chunk of pages indexed before a specific timestamp (for
     * synchronization)
     * 
     * @param beforeTimestamp Only return pages indexed before this timestamp
     *                        (millis since epoch)
     * @param offset          Starting index
     * @param limit           Maximum number of pages to return
     * @return List of PageInfo objects indexed before the timestamp
     */
    List<PageInfo> getPageChunkBeforeTimestamp(long beforeTimestamp, int offset, int limit) throws RemoteException;

    /**
     * Gets the count of pages indexed before a specific timestamp
     * 
     * @param beforeTimestamp Timestamp to compare against
     * @return Number of pages indexed before the timestamp
     */
    int getIndexSizeBeforeTimestamp(long beforeTimestamp) throws RemoteException;

    /**
     * Bulk imports pages from another barrel (for synchronization)
     * 
     * @param pages List of pages to import
     */
    void bulkImportPages(List<PageInfo> pages) throws RemoteException;
}
