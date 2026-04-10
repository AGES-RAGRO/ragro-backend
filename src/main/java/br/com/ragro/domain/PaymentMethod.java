package br.com.ragro.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id", nullable = false)
    private Producer farmer;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "bank_code", columnDefinition = "bpchar(3)")
    private String bankCode;   

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "agency", length = 10)
    private String agency;

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "holder_name", length = 120)
    private String holderName;

    @Column(name = "fiscal_number", length = 14)
    private String fiscalNumber;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}