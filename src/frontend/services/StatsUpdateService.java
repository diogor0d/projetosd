package frontend.services;

import common.IStatsListener;
import common.SystemStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
            gatewayService.connect();
            gatewayService.registerStatsListener(this);
            System.out.println("[StatsUpdateService] Registado como listener de estatísticas");

            // força primeira atualização
            pollStats();
        } catch (Exception e) {
            System.err.println("[StatsUpdateService] Erro ao registar listener: " + e.getMessage());
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

        // envia para todos os clientes subscritos ao tópico /topic/stats
        messagingTemplate.convertAndSend("/topic/stats", stats);

        System.out.println("[StatsUpdateService] Estatísticas atualizadas e enviadas aos clientes");
    }

    /**
     * polling periódico como fallback caso o push não funcione
     * executa a cada 5 segundos
     */
    @Scheduled(fixedRate = 5000)
    public void pollStats() {
        try {
            SystemStats stats = gatewayService.getSystemStats();
            if (stats != null && !stats.equals(lastStats)) {
                updateStats(stats);
            }
        } catch (Exception e) {
            // ignora erros de polling silenciosamente
        }
    }

    /**
     * obtém as últimas estatísticas conhecidas
     */
    public SystemStats getLastStats() {
        return lastStats;
    }
}
