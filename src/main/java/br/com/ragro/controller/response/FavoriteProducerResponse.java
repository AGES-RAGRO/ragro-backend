package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FavoriteProducerResponse {

  private UUID producerId;

  private String producerName;

  private String farmName;

  private String avatarUrl;

  private String coverUrl;

  private BigDecimal averageRating;
}
