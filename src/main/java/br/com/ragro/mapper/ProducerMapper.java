package br.com.ragro.mapper;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProducerMapper {

  public static ProducerGetResponse toGetResponse(User user, Producer producer) {
    return ProducerGetResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .phone(user.getPhone())
        .fiscalNumber(producer.getFiscalNumber())
        .fiscalNumberType(producer.getFiscalNumberType())
        .farmName(producer.getFarmName())
        .description(producer.getDescription())
        .avatarS3(producer.getAvatarS3())
        .displayPhotoS3(producer.getDisplayPhotoS3())
        .totalReviews(producer.getTotalReviews())
        .averageRating(producer.getAverageRating())
        .totalOrders(producer.getTotalOrders())
        .totalSalesAmount(producer.getTotalSalesAmount())
        .build();
  }

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

  @NonNull
  public static ProducerRegistrationResponse toRegistrationResponse(
          @NonNull User user,
          @NonNull Producer producer) {
    return ProducerRegistrationResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .type(user.getType().name().toLowerCase())
            .active(user.isActive())
            .fiscalNumber(producer.getFiscalNumber())
            .fiscalNumberType(producer.getFiscalNumberType())
            .farmName(producer.getFarmName())
            .description(producer.getDescription())
            .avatarS3(producer.getAvatarS3())
            .displayPhotoS3(producer.getDisplayPhotoS3())
            .totalReviews(producer.getTotalReviews())
            .averageRating(producer.getAverageRating())
            .totalOrders(producer.getTotalOrders())
            .totalSalesAmount(producer.getTotalSalesAmount())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
  }

  public static ProducerResponse toResponse(User entity) {
    String address = entity.getAddresses().stream()
        .filter(Address::isPrimary)
        .findFirst()
        .map(a -> "%s, %s - %s %s".formatted(a.getCity(), a.getState(), a.getStreet(), a.getNumber()))
        .orElse(null);

    return ProducerResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .email(entity.getEmail())
        .phone(entity.getPhone())
        .active(entity.isActive())
        .address(address)
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
