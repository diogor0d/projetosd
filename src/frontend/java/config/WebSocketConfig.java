package frontend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * configuração do WebSocket para push de estatísticas em tempo real
 * usa STOMP sobre WebSocket para comunicação bidirecional
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // habilita um broker simples em memória para enviar mensagens aos clientes
        config.enableSimpleBroker("/topic");
        // prefixo para mensagens destinadas ao servidor
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // endpoint websocket que os clientes usarão para conectar
        registry.addEndpoint("/ws-stats")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // fallback para ambientes sem suporte a websocket
    }
}
