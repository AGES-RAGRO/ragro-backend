package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerPublicProfileResponse;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.FarmerAvailability;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.ProducerProfile;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.service.MinioStorageService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProducerMapperTest {

  private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
  private final ProducerMapper producerMapper = new ProducerMapper(minioStorageService);

  {
    when(minioStorageService.composePublicUrl(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void toResponse_shouldMapAllUserFields() {
    User user = buildProducer();

    ProducerResponse response = producerMapper.toResponse(user);

    assertThat(response.getId()).isEqualTo(user.getId());
    assertThat(response.getName()).isEqualTo(user.getName());
    assertThat(response.getEmail()).isEqualTo(user.getEmail());
    assertThat(response.getPhone()).isEqualTo(user.getPhone());
    assertThat(response.isActive()).isEqualTo(user.isActive());
    assertThat(response.getCreatedAt()).isEqualTo(user.getCreatedAt());
    assertThat(response.getUpdatedAt()).isEqualTo(user.getUpdatedAt());
  }

  @Test
  void toResponse_shouldMapActiveStatus() {
    User user = buildProducer();
    user.setActive(false);

    ProducerResponse response = producerMapper.toResponse(user);

    assertThat(response.isActive()).isFalse();
  }

  @Test
  void toResponse_shouldFormatAddressFromPrimaryAddress() {
    User user = buildProducer();
    Address address = new Address();
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setStreet("Rua das Acácias");
    address.setNumber("45");
    address.setPrimary(true);
    user.setAddresses(List.of(address));

    ProducerResponse response = producerMapper.toResponse(user);

    assertThat(response.getAddress()).isEqualTo("Porto Alegre, RS - Rua das Acácias 45");
  }

  @Test
  void toResponse_shouldReturnNullAddress_whenNoAddresses() {
    User user = buildProducer();

    ProducerResponse response = producerMapper.toResponse(user);

    assertThat(response.getAddress()).isNull();
  }

  @Test
  void toResponse_shouldReturnNullAddress_whenNoPrimaryAddress() {
    User user = buildProducer();
    Address address = new Address();
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setStreet("Rua das Acácias");
    address.setNumber("45");
    address.setPrimary(false);
    user.setAddresses(List.of(address));

    ProducerResponse response = producerMapper.toResponse(user);

    assertThat(response.getAddress()).isNull();
  }

  @Test
  void toResponse_shouldMapCreatedAndUpdatedDates() {
    User user = buildProducer();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(5);
    OffsetDateTime updatedAt = OffsetDateTime.now();
    user.setCreatedAt(createdAt);
    user.setUpdatedAt(updatedAt);

    ProducerResponse response = producerMapper.toResponse(user);

    assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
  }

  private User buildProducer() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("João Farmer");
    user.setEmail("joao@example.com");
    user.setPhone("51988888888");
    user.setType(TypeUser.FARMER);
    user.setActive(true);
    user.setAuthSub("auth-sub-123");
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }

  @Test
  void toEntity_shouldMapAllFields() {
    User user = buildProducer();
    ProducerRegistrationRequest request = buildRequest();

    Producer producer = producerMapper.toEntity(user, request, "12345678901");

    assertThat(producer.getUser()).isEqualTo(user);
    assertThat(producer.getFiscalNumber()).isEqualTo("12345678901");
    assertThat(producer.getFiscalNumberType()).isEqualTo("CPF");
    assertThat(producer.getFarmName()).isEqualTo("Fazenda São João");
    assertThat(producer.getDescription()).isEqualTo("Produção orgânica");
    assertThat(producer.getAvatarS3()).isNull();
    assertThat(producer.getDisplayPhotoS3()).isNull();
  }

  @Test
  void toEntity_shouldTrimFarmName() {
    User user = buildProducer();
    ProducerRegistrationRequest request = buildRequest();
    request.setFarmName("  Fazenda São João  ");

    Producer producer = producerMapper.toEntity(user, request, "12345678901");

    assertThat(producer.getFarmName()).isEqualTo("Fazenda São João");
  }

  @Test
  void toEntity_shouldMapOptionalFields_whenProvided() {
    User user = buildProducer();
    ProducerRegistrationRequest request = buildRequest();
    request.setAvatarS3("https://s3.example.com/avatar.jpg");
    request.setDisplayPhotoS3("https://s3.example.com/display.jpg");

    Producer producer = producerMapper.toEntity(user, request, "12345678901");

    assertThat(producer.getAvatarS3()).isEqualTo("https://s3.example.com/avatar.jpg");
    assertThat(producer.getDisplayPhotoS3()).isEqualTo("https://s3.example.com/display.jpg");
  }

  @Test
  void toRegistrationResponse_shouldMapAllFields() {
    User user = buildProducer();
    Producer producer = buildProducer(user);

    ProducerRegistrationResponse response = producerMapper.toRegistrationResponse(user, producer);

    assertThat(response.getId()).isEqualTo(user.getId());
    assertThat(response.getName()).isEqualTo(user.getName());
    assertThat(response.getEmail()).isEqualTo(user.getEmail());
    assertThat(response.getPhone()).isEqualTo(user.getPhone());
    assertThat(response.getType()).isEqualTo("farmer");
    assertThat(response.isActive()).isTrue();
    assertThat(response.getFiscalNumber()).isEqualTo(producer.getFiscalNumber());
    assertThat(response.getFiscalNumberType()).isEqualTo(producer.getFiscalNumberType());
    assertThat(response.getFarmName()).isEqualTo(producer.getFarmName());
    assertThat(response.getDescription()).isEqualTo(producer.getDescription());
    assertThat(response.getTotalReviews()).isEqualTo(0);
    assertThat(response.getTotalOrders()).isEqualTo(0);
    assertThat(response.getAverageRating()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.getTotalSalesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.getCreatedAt()).isEqualTo(user.getCreatedAt());
    assertThat(response.getUpdatedAt()).isEqualTo(user.getUpdatedAt());
  }

  @Test
  void toRegistrationResponse_shouldLowercaseType() {
    User user = buildProducer();
    Producer producer = buildProducer(user);

    ProducerRegistrationResponse response = producerMapper.toRegistrationResponse(user, producer);

    assertThat(response.getType()).isEqualTo("farmer");
  }

  @Test
  void toPublicProfileResponse_shouldMapCustomerFacingFields() {
    User user = buildProducer();
    user.setPhone("51999999999");
    Producer producer = buildProducer(user);
    producer.setFarmName("Fazenda Regenerativa");
    producer.setDescription("Hortaliças sem agrotóxicos");
    producer.setAvatarS3("avatars/joao.jpg");
    producer.setDisplayPhotoS3("covers/farm.jpg");
    producer.setAverageRating(new BigDecimal("4.90"));
    producer.setTotalReviews(15);
    producer.setTotalSalesAmount(new BigDecimal("9999.99"));
    producer.setTotalOrders(120);

    ProducerProfile profile = new ProducerProfile();
    profile.setUser(user);
    profile.setStory("Dedicados à agricultura regenerativa");
    profile.setPhotoUrl("profiles/joao.jpg");
    profile.setMemberSince(LocalDate.of(2018, 1, 10));

    Address address = new Address();
    address.setId(UUID.randomUUID());
    address.setUser(user);
    address.setStreet("Rua das Flores");
    address.setNumber("123");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90010120");
    address.setPrimary(true);

    FarmerAvailability availability = new FarmerAvailability();
    availability.setFarmer(producer);
    availability.setWeekday((short) 1);
    availability.setOpensAt(LocalTime.parse("14:00"));
    availability.setClosesAt(LocalTime.parse("18:30"));

    when(minioStorageService.composePublicUrl("profiles/joao.jpg"))
        .thenReturn("https://cdn.test/profiles/joao.jpg");
    when(minioStorageService.composePublicUrl("avatars/joao.jpg"))
        .thenReturn("https://cdn.test/avatars/joao.jpg");
    when(minioStorageService.composePublicUrl("covers/farm.jpg"))
        .thenReturn("https://cdn.test/covers/farm.jpg");

    ProducerPublicProfileResponse response =
        producerMapper.toPublicProfileResponse(
            user, producer, profile, address, List.of(availability));

    assertThat(response.getId()).isEqualTo(producer.getId());
    assertThat(response.getName()).isEqualTo(user.getName());
    assertThat(response.getFarmName()).isEqualTo("Fazenda Regenerativa");
    assertThat(response.getDescription()).isEqualTo("Hortaliças sem agrotóxicos");
    assertThat(response.getStory()).isEqualTo("Dedicados à agricultura regenerativa");
    assertThat(response.getPhotoUrl()).isEqualTo("https://cdn.test/profiles/joao.jpg");
    assertThat(response.getAvatarS3()).isEqualTo("https://cdn.test/avatars/joao.jpg");
    assertThat(response.getDisplayPhotoS3()).isEqualTo("https://cdn.test/covers/farm.jpg");
    assertThat(response.getPhone()).isEqualTo("51999999999");
    assertThat(response.getAverageRating()).isEqualByComparingTo("4.90");
    assertThat(response.getTotalReviews()).isEqualTo(15);
    assertThat(response.getMemberSince()).isEqualTo(LocalDate.of(2018, 1, 10));
    assertThat(response.getAddress().getCity()).isEqualTo("Porto Alegre");
    assertThat(response.getAvailability()).hasSize(1);
    assertThat(response.getAvailability().get(0).getOpensAt()).isEqualTo("14:00");
    assertThat(response.getAvailability().get(0).getClosesAt()).isEqualTo("18:30");
  }

  private ProducerRegistrationRequest buildRequest() {
    ProducerRegistrationRequest request = new ProducerRegistrationRequest();
    request.setName("João Silva");
    request.setPhone("51988888888");
    request.setEmail("joao@example.com");
    request.setPassword("Senha@123");
    request.setFiscalNumber("12345678901");
    request.setFiscalNumberType("CPF");
    request.setFarmName("Fazenda São João");
    request.setDescription("Produção orgânica");
    return request;
  }

  private Producer buildProducer(User user) {
    Producer producer = new Producer();
    producer.setId(user.getId());
    producer.setUser(user);
    producer.setFiscalNumber("12345678901");
    producer.setFiscalNumberType("CPF");
    producer.setFarmName("Fazenda São João");
    producer.setDescription("Produção orgânica");
    producer.setTotalReviews(0);
    producer.setAverageRating(BigDecimal.ZERO);
    producer.setTotalOrders(0);
    producer.setTotalSalesAmount(BigDecimal.ZERO);
    return producer;
  }
}
