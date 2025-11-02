package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Servico para registar os barrels ativos no sistema
 * backbone do sistema de indexação distribuída
 */
public interface IBarrelRegistry extends Remote {

    /**
     * registar um barrel no sistema
     * 
     * @param barrelId identificador do barril
     * @param barrel   referencia da interface rmi do barril
     */
    void registerBarrel(String barrelId, IBarrel barrel) throws RemoteException;

    /**
     * unregister de barril do sistema
     * 
     * @param barrelId identificador do barril a remover
     */
    void unregisterBarrel(String barrelId) throws RemoteException;

    /**
     * get da lista de barrels ativos. atualiza a lista removendo barrels mortos
     * 
     * @return lista de barrels ativos
     */
    List<IBarrel> getAllBarrels() throws RemoteException;

    /**
     * get da contagem de barris ativos
     * 
     * @return numero de barris registados
     */
    int getBarrelCount() throws RemoteException;

    /**
     * verificar se um barrel especifico está registado
     * 
     * @param barrelId identificador do barrel
     * @return true se o barrel está registado
     */
    boolean isBarrelRegistered(String barrelId) throws RemoteException;
}
