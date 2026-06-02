package br.com.ragro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "favorites")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class FavoriteProducer {

  @EmbeddedId private FavoriteProducerId id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("producerId")
  @JoinColumn(name = "farmer_id", nullable = false)
  private Producer producer;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public FavoriteProducer() {}

  public FavoriteProducer(FavoriteProducerId id, Producer producer) {
    this.id = id;
    this.producer = producer;
  }
}
