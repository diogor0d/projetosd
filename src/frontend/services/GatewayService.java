package frontend.services;

import common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.List;

/**
 * serviço que atua como cliente RMI para comunicar com o Gateway
 * encapsula toda a lógica de comunicação remota
 */
@Service
public class GatewayService {

    private IGateway gateway;

    @Value("${googol.rmi.host:localhost}")
    private String rmiHost;

    @Value("${googol.rmi.port:7000}")
    private int rmiPort;

    @Value("${googol.rmi.name:Gateway}")
    private String rmiName;

    /**
     * conecta ao servidor RMI Gateway
     * é chamado automaticamente após a injeção de dependências
     */
    public void connect() throws Exception {
        String url = String.format("rmi://%s:%d/%s", rmiHost, rmiPort, rmiName);
        gateway = (IGateway) Naming.lookup(url);
        System.out.println("[GatewayService] Conectado ao Gateway RMI: " + url);
    }

    /**
     * submete um URL para indexação
     */
    public void indexURL(String url) throws RemoteException {
        ensureConnected();
        gateway.indexURL(url);
    }

    /**
     * realiza uma pesquisa retornando resultados paginados
     */
    public List<SearchResult> search(String query, int page) throws RemoteException {
        ensureConnected();
        return gateway.search(query, page);
    }

    /**
     * obtém páginas que apontam para um URL específico
     */
    public List<String> getPagesPointingTo(String url) throws RemoteException {
        ensureConnected();
        return gateway.getPagesPointingTo(url);
    }

    /**
     * obtém estatísticas do sistema
     */
    public SystemStats getSystemStats() throws RemoteException {
        ensureConnected();
        return gateway.getSystemStats();
    }

    /**
     * obtém o estado atual da fila de URLs
     */
    public String getURLQueueStatus() throws RemoteException {
        ensureConnected();
        return gateway.getURLQueueStatus();
    }

    /**
     * regista um listener para receber atualizações de estatísticas
     */
    public void registerStatsListener(IStatsListener listener) throws RemoteException {
        ensureConnected();
        gateway.registerStatsListener(listener);
    }

    /**
     * remove registo de um listener de estatísticas
     */
    public void unregisterStatsListener(IStatsListener listener) throws RemoteException {
        ensureConnected();
        gateway.unregisterStatsListener(listener);
    }

    /**
     * verifica se está conectado e tenta reconectar se necessário
     */
    private void ensureConnected() throws RemoteException {
        if (gateway == null) {
            try {
                connect();
            } catch (Exception e) {
                throw new RemoteException("Falha ao conectar ao Gateway RMI", e);
            }
        }
    }
}
