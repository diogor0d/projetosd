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
     * Obtém o tamanho do índice (número de URLs indexados)
     */
    int getIndexSize() throws RemoteException;
}
