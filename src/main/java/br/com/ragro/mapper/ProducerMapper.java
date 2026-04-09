package br.com.ragro.mapper;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.User;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProducerMapper {

  public static ProducerResponse toResponse(User entity) {
    var builder = ProducerResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .email(entity.getEmail())
        .phone(entity.getPhone())
        .active(entity.isActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt());

    if (entity.getProducerProfile() != null) {
      builder.story(entity.getProducerProfile().getStory());
      builder.photoUrl(entity.getProducerProfile().getPhotoUrl());
      builder.memberSince(entity.getProducerProfile().getMemberSince());
    }

    if (entity.getAddresses() != null && !entity.getAddresses().isEmpty()) {
      builder.addresses(entity.getAddresses().stream()
          .map(AddressMapper::toResponse)
          .toList());
    }

    return builder.build();
  }
}
