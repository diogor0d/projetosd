package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * listener remoto para receber atualizações de SystemStats do Gateway.
 */
public interface IStatsListener extends Remote {
    /**
     * Chamado pelo Gateway para enviar um snapshot atualizado de SystemStats.
     */
    void updateStats(SystemStats stats) throws RemoteException;
}
