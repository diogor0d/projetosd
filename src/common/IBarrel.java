package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * interface RMI para o Storage Barrel
 */
public interface IBarrel extends Remote {

    /**
     * indexa informação de uma página (chamado pelos Downloaders)
     */
    void indexPage(PageInfo pageInfo) throws RemoteException;

    /**
     * Pesquisa por um conjunto de termos
     * 
     * @param terms Termos de pesquisa
     * @param page  Número da página (grupos de 10)
     * @return SearchResponse contendo resultados e contagem total
     */
    SearchResponse search(Set<String> terms, int page) throws RemoteException;

    /**
     * obtém lista de páginas que apontam para um URL específico
     */
    List<String> getInlinks(String url) throws RemoteException;

    /**
     * retorna estatísticas do barrel
     */
    BarrelStats getStats() throws RemoteException;

    /**
     * Verifica se o barrel está ativo
     */
    boolean ping() throws RemoteException;

    /**
     * verifica se o barrel está pronto para processar pesquisas
     * (não está sincronizando)
     */
    boolean isReady() throws RemoteException;

    /**
     * Obtém o tamanho do índice (número de URLs indexados)
     */
    int getIndexSize() throws RemoteException;

    /**
     * fornece o identificador unico do barrel
     */
    String getBarrelId() throws RemoteException;

    /**
     * 
     * fornece um chunk de paginas indexadas antes de uma timestamp espeficia (para
     * o feature de sincronizacao)
     * 
     * @param beforeTimestamp retornar páginas indexadas antes desta timestamp
     * @param offset          indice inicial do chunk
     * @param limit           Número máximo de páginas a retornar
     * @return Lista de objetos PageInfo indexados antes da timestamp
     */
    List<PageInfo> getPageChunkBeforeTimestamp(long beforeTimestamp, int offset, int limit) throws RemoteException;

    /**
     * Obtém a contagem de páginas indexadas antes de uma timestamp específica
     * 
     * @param beforeTimestamp Timestamp a comparar
     * @return Número de páginas indexadas antes da timestamp
     */
    int getIndexSizeBeforeTimestamp(long beforeTimestamp) throws RemoteException;

    /**
     * importacao em massa de paginas (para sincronizacao)
     * 
     * @param pages Lista de páginas a importar
     */
    void bulkImportPages(List<PageInfo> pages) throws RemoteException;

    /**
     * Obtém a timestamp da página mais recentemente indexada neste barrel
     * 
     * @return Timestamp da página mais recente, ou 0 se o índice estiver
     *         vazio
     */
    long getMostRecentPageTimestamp() throws RemoteException;
}
