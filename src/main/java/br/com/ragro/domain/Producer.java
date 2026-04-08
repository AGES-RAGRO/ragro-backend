package br.com.ragro.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "farmers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class Producer {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Column(name = "fiscal_number", nullable = false, length = 14)
    private String fiscalNumber;

    @Column(name = "fiscal_number_type", nullable = false, length = 5)
    private String fiscalNumberType;

    @Column(name = "farm_name", nullable = false, length = 150)
    private String farmName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "avatar_s3", columnDefinition = "text")
    private String avatarS3;

    @Column(name = "display_photo_s3", columnDefinition = "text")
    private String displayPhotoS3;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews = 0;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders = 0;

    @Column(name = "total_sales_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalSalesAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
