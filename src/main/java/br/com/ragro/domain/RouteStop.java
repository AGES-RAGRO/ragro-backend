package br.com.ragro.domain;

import br.com.ragro.domain.enums.RouteStopStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Parada ordenada de uma {@link DeliveryRoute}, 1:1 com o pedido entregue nela. */
@Entity
@Table(name = "route_stops")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class RouteStop {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "route_id", nullable = false)
  private DeliveryRoute route;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  /** Posição na rota otimizada (0-based, ordem de visita). */
  @Column(name = "sequence", nullable = false)
  private int sequence;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RouteStopStatus status = RouteStopStatus.PENDING;

  @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(name = "address_text", columnDefinition = "text")
  private String addressText;

  /** Distância/duração da leg que CHEGA nesta parada (da parada anterior ou da origem). */
  @Column(name = "leg_distance_km", precision = 10, scale = 2)
  private BigDecimal legDistanceKm;

  @Column(name = "leg_duration_seconds")
  private Integer legDurationSeconds;

  /** ETA absoluto estimado na criação da rota (com trânsito do momento). */
  @Column(name = "eta")
  private OffsetDateTime eta;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;
}
