package br.com.ragro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A producer's GPS trail point during an active route. Personal location data: 7-day retention
 * (daily purge), never written to general logs.
 */
@Entity
@Table(name = "route_positions")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class RoutePosition {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "route_id", nullable = false)
  private DeliveryRoute route;

  @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(name = "accuracy_meters", precision = 8, scale = 2)
  private BigDecimal accuracyMeters;

  @Column(name = "speed_kmh", precision = 6, scale = 2)
  private BigDecimal speedKmh;

  @Column(name = "recorded_at", nullable = false)
  private OffsetDateTime recordedAt;
}
