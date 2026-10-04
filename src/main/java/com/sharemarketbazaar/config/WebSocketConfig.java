package com.sharemarketbazaar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP configuration.
 * Connect:    /ws (SockJS or raw WebSocket)
 * Subscribe:  /topic/signals, /topic/signals/{symbol}, /topic/prices/{symbol}
 * Send to:    /app/analysis/{symbol}  (reply on /user/queue/analysis)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Restrict origins to your front-end domains in production.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
