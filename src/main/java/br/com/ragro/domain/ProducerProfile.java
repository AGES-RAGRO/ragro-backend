package br.com.ragro.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "producer_profiles")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class ProducerProfile {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private User user;

    @Column(columnDefinition = "TEXT")
    private String story;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "member_since")
    private LocalDate memberSince;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}