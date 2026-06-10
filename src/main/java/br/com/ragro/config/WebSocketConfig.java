package br.com.ragro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal de rastreamento em tempo real (Fase 3): STOMP sobre WebSocket com broker SIMPLES em
 * memória — decisão da etapa: sem broker externo (1 task ECS); na etapa de mensageria, trocar
 * {@code enableSimpleBroker} por {@code enableStompBrokerRelay} pluga um broker externo sem mexer
 * nos controllers. Flag {@code ragro.tracking.enabled} desliga o canal inteiro (rollback).
 *
 * <p>Handshake: o upgrade HTTP de {@code /ws} passa pela security chain normal (Bearer token
 * obrigatório — o app envia o header no connect); o Principal do handshake é associado à sessão
 * STOMP e a autorização fina (assinar tópico da rota, enviar posição) acontece no {@link
 * TrackingChannelInterceptor}.
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "ragro.tracking.enabled", havingValue = "true")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final TrackingChannelInterceptor trackingChannelInterceptor;
  private final String allowedOriginPatterns;

  public WebSocketConfig(
      TrackingChannelInterceptor trackingChannelInterceptor,
      @Value("${cors.allowed-origin-patterns}") String allowedOriginPatterns) {
    this.trackingChannelInterceptor = trackingChannelInterceptor;
    this.allowedOriginPatterns = allowedOriginPatterns;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        // Apps nativos não enviam Origin; navegadores ficam restritos ao padrão do CORS.
        .setAllowedOriginPatterns(allowedOriginPatterns.split(","));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(
      org.springframework.messaging.simp.config.ChannelRegistration registration) {
    registration.interceptors(trackingChannelInterceptor);
  }
}
