package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.Address;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.service.MinioStorageService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.domain.Producer;
import java.math.BigDecimal;

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
