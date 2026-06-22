package br.com.ragro.domain;

import br.com.ragro.domain.enums.DeliveryRouteStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Rota de entrega persistida do produtor (1 ativa por vez). Substitui o fluxo efêmero antigo em
 * que cada confirmação de entrega recalculava (e pagava) a rota inteira no Google e matar o app
 * perdia a sequência. Base do rastreamento em tempo real (Fase 3).
 */
@Entity
@Table(name = "delivery_routes")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class DeliveryRoute {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "farmer_id", nullable = false)
  private Producer farmer;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DeliveryRouteStatus status = DeliveryRouteStatus.ACTIVE;

  @Column(name = "origin_latitude", nullable = false, precision = 10, scale = 7)
  private BigDecimal originLatitude;

  @Column(name = "origin_longitude", nullable = false, precision = 10, scale = 7)
  private BigDecimal originLongitude;

  @Column(name = "total_distance_km", precision = 10, scale = 2)
  private BigDecimal totalDistanceKm;

  @Column(name = "total_duration_seconds")
  private Integer totalDurationSeconds;

  /** Soma das idas-e-voltas individuais até cada parada (Route Matrix) — baseline do CO2. */
  @Column(name = "baseline_distance_km", precision = 10, scale = 2)
  private BigDecimal baselineDistanceKm;

  @Column(name = "overview_polyline", columnDefinition = "text")
  private String overviewPolyline;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sequence ASC")
  private List<RouteStop> stops = new ArrayList<>();
}
