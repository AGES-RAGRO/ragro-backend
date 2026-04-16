package br.com.ragro.mapper;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.AvailabilityResponse;
import br.com.ragro.controller.response.MarketplaceProducerResponse;
import br.com.ragro.controller.response.PaymentMethodResponse;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.FarmerAvailability;
import br.com.ragro.domain.PaymentMethod;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.ProducerProfile;
import br.com.ragro.domain.User;
import br.com.ragro.service.MinioStorageService;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProducerMapper {

  private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

  private final MinioStorageService minioStorageService;

  public ProducerGetResponse toGetResponse(
      User user,
      Producer producer,
      ProducerProfile profile,
      Address primaryAddress,
      List<PaymentMethod> paymentMethods,
      List<FarmerAvailability> availability) {

    return ProducerGetResponse.builder()
        .id(user.getId())
        .name(user.getName())
        .email(user.getEmail())
        .phone(user.getPhone())
        .fiscalNumber(producer.getFiscalNumber())
        .fiscalNumberType(producer.getFiscalNumberType())
        .farmName(producer.getFarmName())
        .description(producer.getDescription())
        .avatarS3(minioStorageService.composePublicUrl(producer.getAvatarS3()))
        .displayPhotoS3(minioStorageService.composePublicUrl(producer.getDisplayPhotoS3()))
        .totalReviews(producer.getTotalReviews())
        .averageRating(producer.getAverageRating())
        .totalOrders(producer.getTotalOrders())
        .totalSalesAmount(producer.getTotalSalesAmount())
        .story(profile == null ? null : profile.getStory())
        .photoUrl(profile == null ? null : minioStorageService.composePublicUrl(profile.getPhotoUrl()))
        .memberSince(profile == null ? null : profile.getMemberSince())
        .active(user.isActive())
        .address(primaryAddress == null ? null : AddressMapper.toResponse(primaryAddress))
        .paymentMethods(mapList(paymentMethods, this::toPaymentMethodResponse))
        .availability(mapList(availability, this::toAvailabilityResponse))
        .build();
  }

  private static <T, R> List<R> mapList(List<T> source, Function<T, R> mapper) {
    return source == null ? List.of() : source.stream().map(mapper).toList();
  }

  public AvailabilityResponse toAvailabilityResponse(FarmerAvailability slot) {
    return AvailabilityResponse.builder()
        .weekday(slot.getWeekday() == null ? null : slot.getWeekday().intValue())
        .opensAt(slot.getOpensAt() == null ? null : slot.getOpensAt().format(HHMM))
        .closesAt(slot.getClosesAt() == null ? null : slot.getClosesAt().format(HHMM))
        .build();
  }

  public PaymentMethodResponse toPaymentMethodResponse(PaymentMethod pm) {
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
  public Producer toEntity(
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
  public ProducerRegistrationResponse toRegistrationResponse(
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
        .avatarS3(minioStorageService.composePublicUrl(producer.getAvatarS3()))
        .displayPhotoS3(minioStorageService.composePublicUrl(producer.getDisplayPhotoS3()))
        .totalReviews(producer.getTotalReviews())
        .averageRating(producer.getAverageRating())
        .totalOrders(producer.getTotalOrders())
        .totalSalesAmount(producer.getTotalSalesAmount())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  public ProducerResponse toResponse(User entity) {
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

  public MarketplaceProducerResponse toMarketplaceResponse(Producer producer) {
    return MarketplaceProducerResponse.builder()
        .id(producer.getId())
        .ownerName(producer.getUser().getName())
        .farmName(producer.getFarmName())
        .description(producer.getDescription())
        .avatarS3(minioStorageService.composePublicUrl(producer.getAvatarS3()))
        .displayPhotoS3(minioStorageService.composePublicUrl(producer.getDisplayPhotoS3()))
        .averageRating(producer.getAverageRating())
        .build();
  }
}
