package br.com.ragro.domain;

import java.time.OffsetDateTime;
import jakarta.persistence.*;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


@Entity
@Table(name = "customer")
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "customers")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class Customer {

@Id
@GeneratedValue(strategy = GenerationType.UUID)
@Column(columnDefinition = "uuid")
private UUID id;

@Column(nullable = false, length = 120)
private String name;

@Column(nullable = false, unique = true, length = 254)
private String email;

@Column(length = 20)
private String phone;

@Column(name = "active", nullable = false)
private boolean active;

@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@UpdateTimestamp
@Column(name = "updated_at")
private OffsetDateTime updatedAt;

    
  @Id
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "id")
  private User user;

  @Column(name = "fiscal_number", nullable = false, unique = true, length = 11)
  private String fiscalNumber;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
