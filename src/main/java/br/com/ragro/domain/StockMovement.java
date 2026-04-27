package br.com.ragro.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import br.com.ragro.domain.enums.StockMovementReason;
import br.com.ragro.domain.enums.StockMovementType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private StockMovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private StockMovementReason reason;

    @Column(precision = 12, scale = 3, nullable = false)
    @NotNull
    @Positive
    private BigDecimal quantity;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
