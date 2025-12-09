package frontend.services;

import common.IStatsListener;
import common.SystemStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * serviço que recebe atualizações de estatísticas do Gateway via RMI
 * e distribui para clientes web via WebSocket
 */
@Service
public class StatsUpdateService extends UnicastRemoteObject implements IStatsListener {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private GatewayService gatewayService;

    private SystemStats lastStats;

    public StatsUpdateService() throws RemoteException {
        super();
    }

    /**
     * regista este serviço como listener no Gateway após inicialização
     */
    @PostConstruct
    public void init() {
        try {
            System.out.println("[StatsUpdateService] Iniciando conexão com Gateway...");
            gatewayService.connect();
            System.out.println("[StatsUpdateService] Conexão com Gateway estabelecida");

            System.out.println("[StatsUpdateService] Registando como listener de estatísticas...");
            gatewayService.registerStatsListener(this);
            System.out.println("[StatsUpdateService] ✅ Registado como listener de estatísticas");

            // obtém estatísticas iniciais
            try {
                System.out.println("[StatsUpdateService] Obtendo estatísticas iniciais...");
                SystemStats stats = gatewayService.getSystemStats();
                if (stats != null) {
                    System.out.println("[StatsUpdateService] Estatísticas iniciais recebidas, atualizando...");
                    updateStats(stats);
                }
            } catch (Exception e) {
                System.err.println("[StatsUpdateService] Erro ao obter estatísticas iniciais: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("[StatsUpdateService] ❌ Erro ao registar listener: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * remove registo do listener ao destruir o bean
     */
    @PreDestroy
    public void cleanup() {
        try {
            gatewayService.unregisterStatsListener(this);
            System.out.println("[StatsUpdateService] Removido registo de listener");
        } catch (Exception e) {
            System.err.println("[StatsUpdateService] Erro ao remover registo: " + e.getMessage());
        }
    }

    /**
     * callback invocado pelo Gateway quando as estatísticas mudam
     * envia as estatísticas para todos os clientes WebSocket conectados
     */
    @Override
    public void updateStats(SystemStats stats) throws RemoteException {
        this.lastStats = stats;

        // System.out.println("[StatsUpdateService] Recebidas novas estatísticas:");
        // System.out.println(" - Total páginas: " + stats.getTotalIndexedPages());
        // System.out.println(" - URLs na fila: " + stats.getUrlQueueSize());
        // System.out.println(" - Barrels ativos: " + stats.getBarrelStats().size());

        // envia para todos os clientes subscritos ao tópico /topic/stats
        messagingTemplate.convertAndSend("/topic/stats", stats);

        // System.out.println("[StatsUpdateService] Estatísticas enviadas para clientes
        // WebSocket");
    }

    /**
     * obtém as últimas estatísticas conhecidas
     */
    public SystemStats getLastStats() {
        return lastStats;
    }
}
