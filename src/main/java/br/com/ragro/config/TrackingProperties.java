package br.com.ragro.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Configuração do rastreamento em tempo real (Fase 3). */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ragro.tracking")
public class TrackingProperties {

  /** Liga/desliga o canal WebSocket/STOMP inteiro (rollback instantâneo). */
  private boolean enabled = true;

  /** Janela de retenção da trilha GPS (decisão de produto: 7 dias, base LGPD). */
  @Min(1)
  private int retentionDays = 7;

  /** Pings com precisão pior que isto são descartados (ruído de GPS). */
  @Min(1)
  private double maxAccuracyMeters = 50;

  /** Salto implicando velocidade acima disto é descartado (teleporte de GPS). */
  @Min(1)
  private double maxSpeedKmh = 150;

  /** Intervalo mínimo entre pings aceitos por rota (rate limit de ingestão). */
  @Min(100)
  private long minIntervalMs = 2000;

  /** Distância da polyline a partir da qual o produtor é considerado fora da rota. */
  @Min(10)
  private double deviationMeters = 200;
}
