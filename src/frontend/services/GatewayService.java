package frontend.services;

import common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * serviço que atua como cliente RMI para comunicar com o Gateway
 * encapsula toda a lógica de comunicação remota
 */
@Service
public class GatewayService {

    private IGateway gateway;

    @Value("${googol.rmi.host:localhost}")
    private String rmiHost;

    @Value("${googol.rmi.port:1099}")
    private int rmiPort;

    @Value("${googol.rmi.name:Gateway}")
    private String rmiName;

    /**
     * conecta ao servidor RMI Gateway
     * é chamado automaticamente após a injeção de dependências
     */
    public void connect() throws Exception {
        String url = String.format("rmi://%s:%d/%s", rmiHost, rmiPort, rmiName);

        while (true) {
            try {
                gateway = (IGateway) Naming.lookup(url);
                System.out.println("[GatewayService] Conectado ao Gateway RMI: " + url);
                break; // Connection successful, exit loop
            } catch (Exception e) {
                System.err.println("[GatewayService] Falha ao conectar ao Gateway RMI: " + e.getMessage());
                System.out.println("[GatewayService] Tentando reconectar em 3 segundos...");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Conexão interrompida durante reconexão", ie);
                }
            }
        }
    }

    /**
     * submete um URL para indexação de forma assíncrona
     */
    @Async
    public CompletableFuture<Void> indexURLAsync(String url) {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureConnected();
                gateway.indexURL(url);
                System.out.println("[GatewayService] URL indexado com sucesso: " + url);
            } catch (Exception e) {
                System.err.println("[GatewayService] Erro ao indexar URL " + url + ": " + e.getMessage());
            }
        });
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
    public SearchResponse search(String query, int page) throws RemoteException {
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

        // Test connection validity
        try {
            gateway.getSystemStats(); // Simple call to verify connection is alive
        } catch (RemoteException e) {
            System.err.println("[GatewayService] Conexão perdida, tentando reconectar...");
            gateway = null; // Reset gateway
            try {
                connect(); // Reconnect with retry logic
            } catch (Exception ex) {
                throw new RemoteException("Falha ao reconectar ao Gateway RMI", ex);
            }
        }
    }
}
