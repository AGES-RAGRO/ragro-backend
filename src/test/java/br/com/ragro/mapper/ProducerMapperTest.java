package br.com.ragro.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProducerMapperTest {

  @Test
  void toResponse_shouldMapAllUserFields() {
    User user = buildProducer();

    ProducerResponse response = ProducerMapper.toResponse(user);

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

    ProducerResponse response = ProducerMapper.toResponse(user);

    assertThat(response.isActive()).isFalse();
  }

  @Test
  void toResponse_shouldMapCreatedAndUpdatedDates() {
    User user = buildProducer();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(5);
    OffsetDateTime updatedAt = OffsetDateTime.now();
    user.setCreatedAt(createdAt);
    user.setUpdatedAt(updatedAt);

    ProducerResponse response = ProducerMapper.toResponse(user);

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
}
