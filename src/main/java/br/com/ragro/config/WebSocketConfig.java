package br.com.ragro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time tracking channel (Phase 3): STOMP over WebSocket with an in-memory SIMPLE broker (1 ECS
 * task; swap {@code enableSimpleBroker} for {@code enableStompBrokerRelay} to use an external broker).
 * The {@code ragro.tracking.enabled} flag disables the whole channel (rollback).
 *
 * <p>The {@code /ws} upgrade goes through the security chain (Bearer token required); the handshake
 * Principal binds to the STOMP session and fine-grained authz happens in
 * {@link TrackingChannelInterceptor}.
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
        // Native apps don't send Origin; browsers stay restricted to the CORS pattern.
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
