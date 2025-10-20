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
}
