package br.com.ragro.domain;

import br.com.ragro.domain.enums.FuelType;
import br.com.ragro.domain.enums.VehicleType;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "vehicle_preferences")
@Getter
@Setter
public class VehiclePreference {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

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

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
