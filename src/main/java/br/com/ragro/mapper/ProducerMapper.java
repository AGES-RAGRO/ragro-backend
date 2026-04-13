package br.com.ragro.mapper;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.MarketplaceProducerResponse;
import br.com.ragro.controller.response.PaymentMethodResponse;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.ProducerProfile;
import br.com.ragro.domain.User;
import java.util.List;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProducerMapper {

  public static ProducerGetResponse toGetResponse(
      User user,
      Producer producer,
      ProducerProfile profile,
      Address primaryAddress,
      List<PaymentMethod> paymentMethods) {

    List<PaymentMethodResponse> pmResponses =
        paymentMethods == null
            ? List.of()
            : paymentMethods.stream().map(ProducerMapper::toPaymentMethodResponse).toList();

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
        .story(profile != null ? profile.getStory() : null)
        .photoUrl(profile != null ? profile.getPhotoUrl() : null)
        .memberSince(profile != null ? profile.getMemberSince() : null)
        .active(user.isActive())
        .address(primaryAddress != null ? AddressMapper.toResponse(primaryAddress) : null)
        .paymentMethods(pmResponses)
        .build();
  }

  public static PaymentMethodResponse toPaymentMethodResponse(PaymentMethod pm) {
    return PaymentMethodResponse.builder()
        .id(pm.getId())
        .type(pm.getType())
        .pixKeyType(pm.getPixKeyType())
        .pixKey(pm.getPixKey())
        .bankCode(pm.getBankCode())
        .bankName(pm.getBankName())
        .agency(pm.getAgency())
        .accountNumber(pm.getAccountNumber())
        .accountType(pm.getAccountType())
        .holderName(pm.getHolderName())
        .build();
  }

  @NonNull
  public static Producer toEntity(
      @NonNull User user,
      @NonNull ProducerRegistrationRequest request,
      @NonNull String normalizedFiscalNumber) {
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
      @NonNull User user, @NonNull Producer producer) {
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
    String address =
        entity.getAddresses().stream()
            .filter(Address::isPrimary)
            .findFirst()
            .map(
                a ->
                    "%s, %s - %s %s"
                        .formatted(a.getCity(), a.getState(), a.getStreet(), a.getNumber()))
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

  public static MarketplaceProducerResponse toMarketplaceResponse(Producer producer) {
    return MarketplaceProducerResponse.builder()
        .id(producer.getId())
        .ownerName(producer.getUser().getName())
        .farmName(producer.getFarmName())
        .description(producer.getDescription())
        .avatarS3(producer.getAvatarS3())
        .averageRating(producer.getAverageRating())
        .build();
  }
}
