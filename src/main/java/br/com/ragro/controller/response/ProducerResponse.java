package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProducerResponse {

  private UUID id;
  private String name;
  private String email;
  private String phone;
  private boolean active;
  private BigDecimal rating;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
