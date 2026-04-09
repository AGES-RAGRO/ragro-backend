package br.com.ragro.mapper;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.User;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProducerMapper {

  public static ProducerResponse toResponse(User entity) {
    return ProducerResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .email(entity.getEmail())
        .phone(entity.getPhone())
        .active(entity.isActive())
        .rating(entity.getProducerProfile() != null ? entity.getProducerProfile().getRating() : BigDecimal.ZERO)
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
