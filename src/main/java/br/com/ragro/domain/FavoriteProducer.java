package br.com.ragro.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

@Entity
@Table(name = "favorite_producers")
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class FavoriteProducer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @NotNull
    @Column(name = "customer_id")
    private UUID customerId;

    @NotNull
    @Column(name = "producer_id")
    private UUID producerId;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producer_id", insertable = false, updatable = false)
    private Producer producer;

    public UUID getId() {
        return id;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public void setProducerId(UUID producerId) {
        this.producerId = producerId;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getProducerId() {
        return producerId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Producer getProducer() {
        return producer;
    }
}
