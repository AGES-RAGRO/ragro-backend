package br.com.ragro.mapper;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProducerMapper {

  @NonNull
  public static Producer toEntity(@NonNull User user, @NonNull ProducerRegistrationRequest request, @NonNull String normalizedFiscalNumber) {
    Producer producer = new Producer();
    producer.setUser(user);
    producer.setFiscalNumber(normalizedFiscalNumber);
    producer.setFiscalNumberType(request.getFiscalNumberType());
    producer.setFarmName(request.getFarmName().trim());
    producer.setDescription(request.getDescription());
    producer.setAvatarS3(request.getAvatarS3());
    producer.setDisplayPhotoS3(request.getDisplayPhotoS3());
    return producer;
  }

  public static ProducerResponse toResponse(User entity) {
    return ProducerResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .email(entity.getEmail())
        .phone(entity.getPhone())
        .active(entity.isActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
