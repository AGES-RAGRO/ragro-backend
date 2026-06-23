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
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "vehicle_preferences")
@Getter
@Setter
public class VehiclePreference implements Persistable<UUID> {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

  @Transient
  private boolean isNew;

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

  @Override
  public UUID getId() {
    return userId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public void markAsNew() {
    this.isNew = true;
  }
}
