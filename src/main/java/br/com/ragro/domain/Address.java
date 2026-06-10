package br.com.ragro.domain;

import br.com.ragro.domain.enums.GeocodeStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
public class Address {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @NotBlank
  @Column(name = "street", nullable = false, length = 200)
  private String street;

  @NotBlank
  @Column(name = "number", nullable = false, length = 10)
  private String number;

  @Column(name = "complement", length = 100)
  private String complement;

  @NotBlank
  @Column(name = "neighborhood", nullable = false, length = 100)
  private String neighborhood;

  @NotBlank
  @Column(name = "city", nullable = false, length = 100)
  private String city;

  @NotBlank
  @Column(name = "state", nullable = false, length = 2)
  private String state;

  @NotBlank
  @Column(name = "zip_code", nullable = false, length = 8)
  private String zipCode;

  @Column(name = "latitude", precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(name = "longitude", precision = 10, scale = 7)
  private BigDecimal longitude;

  /**
   * Resultado da última geocodificação ({@code null} = nunca tentado). FAILED/AMBIGUOUS não são
   * re-tentados automaticamente — antes, um endereço não-geocodável era re-tentado (e cobrado) a
   * cada abertura do mapa, para sempre. Zerado quando o endereço é editado.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "geocode_status", length = 12)
  private GeocodeStatus geocodeStatus;

  @Column(name = "geocoded_at")
  private OffsetDateTime geocodedAt;

  @Column(name = "is_primary", nullable = false)
  private boolean isPrimary;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
