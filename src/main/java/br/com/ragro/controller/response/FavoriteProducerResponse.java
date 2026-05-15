package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteProducerResponse {

    private UUID producerId;

    private String producerName;

    private String farmName;

    private String avatarUrl;

    private BigDecimal averageRating;
}