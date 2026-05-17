package br.com.ragro.domain;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "co2_savings")
@Getter
@Setter
public class Co2Saving {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "distance_optimized", nullable = false)
  private Double distanceOptimized;

  @Column(name = "distance_non_optimized", nullable = false)
  private Double distanceNonOptimized;

  @Column(name = "co2_saved", nullable = false)
  private Double co2Saved;

  @Enumerated(EnumType.STRING)
  @Column(name = "vehicle_type", nullable = false)
  private VehicleType vehicleType;

  @Enumerated(EnumType.STRING)
  @Column(name = "fuel_type", nullable = false)
  private FuelType fuelType;

  @Column(name = "average_consumption", nullable = false)
  private Double averageConsumption;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
