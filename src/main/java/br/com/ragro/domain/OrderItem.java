package br.com.ragro.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "product_name_snapshot", nullable = false, length = 150)
  private String productNameSnapshot;

  @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPriceSnapshot;

  @Column(name = "unity_type_snapshot", nullable = false, length = 20)
  private String unityTypeSnapshot;

  @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
  private BigDecimal quantity;

  @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
  private BigDecimal subtotal;
}
