package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface RMI para a Gateway
 */
public interface IGateway extends Remote {

    /**
     * Submete um URL para indexação
     */
    void indexURL(String url) throws RemoteException;

    /**
     * Realiza uma pesquisa
     * 
     * @param query String com os termos de pesquisa
     * @param page  Número da página de resultados (grupos de 10)
     */
    List<SearchResult> search(String query, int page) throws RemoteException;

    /**
     * Obtém páginas que apontam para um URL específico
     */
    List<String> getPagesPointingTo(String url) throws RemoteException;

    /**
     * Obtém estatísticas gerais do sistema
     */
    SystemStats getSystemStats() throws RemoteException;

    /**
     * Obtém próximo URL da fila (para Downloaders)
     * 
     * @param timeout Tempo de espera em segundos
     * @return URL para processar, ou null se timeout
     */
    String pollURL(long timeout) throws RemoteException;

    /**
     * Obtém próximo URL da fila, bloqueando até estar disponível (para Downloaders)
     * Este método bloqueia indefinidamente até que um URL esteja disponível
     * 
     * @return URL para processar (nunca null)
     */
    String getNextURL() throws RemoteException;

    /**
     * Verifica se um URL já foi visitado
     * 
     * @param url URL a verificar
     * @return true se já foi visitado
     */
    boolean isURLVisited(String url) throws RemoteException;

    /**
     * Marca um URL como visitado
     * 
     * @param url URL a marcar
     * @return true se foi marcado agora, false se já estava visitado
     */
    boolean markURLAsVisited(String url) throws RemoteException;

    /**
     * Atomically check if URL is unvisited and mark it as visited if so.
     * This prevents race conditions where multiple downloaders try to process the
     * same URL.
     * 
     * @param url URL to check and mark
     * @return true if URL was NOT visited (and is now marked), false if already
     *         visited
     */
    boolean tryMarkAsVisited(String url) throws RemoteException;

    /**
     * Adiciona um URL descoberto à fila (com prioridade normal)
     * 
     * @param url          URL descoberto
     * @param highPriority true para alta prioridade (user-submitted), false para
     *                     normal (crawled)
     */
    void addDiscoveredURL(String url, boolean highPriority) throws RemoteException;

    /**
     * Obtém o conteúdo atual da fila de URLs (para debug)
     * 
     * @return String representando o estado da fila
     */
    String getURLQueueStatus() throws RemoteException;

    /**
     * Register a remote listener to receive real-time SystemStats updates pushed
     * by the Gateway.
     *
     * @param listener remote listener implementing IStatsListener
     */
    void registerStatsListener(IStatsListener listener) throws RemoteException;

    /**
     * Unregister a previously registered stats listener.
     *
     * @param listener remote listener to remove
     */
    void unregisterStatsListener(IStatsListener listener) throws RemoteException;
}
