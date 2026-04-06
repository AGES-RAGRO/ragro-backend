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

    
}
