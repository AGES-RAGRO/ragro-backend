package br.com.ragro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FavoriteProducerId implements Serializable {

  @Column(name = "customer_id", nullable = false, columnDefinition = "uuid")
  private UUID customerId;

  @Column(name = "farmer_id", nullable = false, columnDefinition = "uuid")
  private UUID producerId;
}
