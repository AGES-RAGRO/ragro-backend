package br.com.ragro.domain;

import br.com.ragro.domain.enums.OrderStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class OrderStatusHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderStatus status;

  @CreationTimestamp
  @Column(name = "changed_at", nullable = false, updatable = false)
  private OffsetDateTime changedAt;
}
