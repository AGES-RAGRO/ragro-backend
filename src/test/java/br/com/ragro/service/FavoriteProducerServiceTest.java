package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.FavoriteProducerResponse;
import br.com.ragro.domain.FavoriteProducer;
import br.com.ragro.domain.FavoriteProducerId;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ConflictException;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.FavoriteProducerRepository;
import br.com.ragro.repository.ProducerRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class FavoriteProducerServiceTest {

  @Mock private FavoriteProducerRepository favoriteProducerRepository;
  @Mock private ProducerRepository producerRepository;
  @Mock private UserService userService;

  private FavoriteProducerService favoriteProducerService;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    favoriteProducerService =
        new FavoriteProducerService(favoriteProducerRepository, producerRepository, userService);
    jwt = Jwt.withTokenValue("token").header("alg", "RS256").claim("sub", "sub").build();
  }

  @Test
  void favoriteProducer_shouldPersistFavorite_whenProducerIsActiveAndNotFavorited() {
    User customer = buildUser(TypeUser.CUSTOMER);
    Producer producer = buildProducer(true);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);
    when(producerRepository.findById(producer.getId())).thenReturn(Optional.of(producer));
    when(favoriteProducerRepository.existsByIdCustomerIdAndIdProducerId(
            customer.getId(), producer.getId()))
        .thenReturn(false);

    favoriteProducerService.favoriteProducer(producer.getId(), jwt);

    ArgumentCaptor<FavoriteProducer> captor = ArgumentCaptor.forClass(FavoriteProducer.class);
    verify(favoriteProducerRepository).save(captor.capture());

    FavoriteProducer saved = captor.getValue();
    assertThat(saved.getId().getCustomerId()).isEqualTo(customer.getId());
    assertThat(saved.getId().getProducerId()).isEqualTo(producer.getId());
    assertThat(saved.getProducer()).isEqualTo(producer);
  }

  @Test
  void favoriteProducer_shouldThrowConflict_whenAlreadyFavorited() {
    User customer = buildUser(TypeUser.CUSTOMER);
    Producer producer = buildProducer(true);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);
    when(producerRepository.findById(producer.getId())).thenReturn(Optional.of(producer));
    when(favoriteProducerRepository.existsByIdCustomerIdAndIdProducerId(
            customer.getId(), producer.getId()))
        .thenReturn(true);

    assertThatThrownBy(() -> favoriteProducerService.favoriteProducer(producer.getId(), jwt))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Produtor já favoritado");

    verify(favoriteProducerRepository, never()).save(any(FavoriteProducer.class));
  }

  @Test
  void favoriteProducer_shouldThrowNotFound_whenProducerDoesNotExist() {
    User customer = buildUser(TypeUser.CUSTOMER);
    UUID producerId = UUID.randomUUID();

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);
    when(producerRepository.findById(producerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> favoriteProducerService.favoriteProducer(producerId, jwt))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");
  }

  @Test
  void favoriteProducer_shouldThrowNotFound_whenProducerIsInactive() {
    User customer = buildUser(TypeUser.CUSTOMER);
    Producer producer = buildProducer(false);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);
    when(producerRepository.findById(producer.getId())).thenReturn(Optional.of(producer));

    assertThatThrownBy(() -> favoriteProducerService.favoriteProducer(producer.getId(), jwt))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Produtor não encontrado");

    verify(favoriteProducerRepository, never())
        .existsByIdCustomerIdAndIdProducerId(any(UUID.class), any(UUID.class));
  }

  @Test
  void unfavoriteProducer_shouldDeleteFavoriteForAuthenticatedCustomer() {
    User customer = buildUser(TypeUser.CUSTOMER);
    UUID producerId = UUID.randomUUID();

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);

    favoriteProducerService.unfavoriteProducer(producerId, jwt);

    verify(favoriteProducerRepository)
        .deleteByIdCustomerIdAndIdProducerId(customer.getId(), producerId);
  }

  @Test
  void getFavorites_shouldReturnFavoriteProducerResponses() {
    User customer = buildUser(TypeUser.CUSTOMER);
    Producer producer = buildProducer(true);
    FavoriteProducer favorite =
        new FavoriteProducer(new FavoriteProducerId(customer.getId(), producer.getId()), producer);

    when(userService.getAuthenticatedUser(jwt)).thenReturn(customer);
    when(favoriteProducerRepository.findByIdCustomerIdOrderByCreatedAtDesc(customer.getId()))
        .thenReturn(List.of(favorite));

    List<FavoriteProducerResponse> response = favoriteProducerService.getFavorites(jwt);

    assertThat(response).hasSize(1);
    assertThat(response.getFirst().getProducerId()).isEqualTo(producer.getId());
    assertThat(response.getFirst().getProducerName()).isEqualTo(producer.getUser().getName());
    assertThat(response.getFirst().getFarmName()).isEqualTo(producer.getFarmName());
    assertThat(response.getFirst().getAverageRating()).isEqualByComparingTo("4.70");
  }

  @Test
  void favoriteProducer_shouldThrowForbidden_whenAuthenticatedUserIsNotCustomer() {
    User farmer = buildUser(TypeUser.FARMER);
    UUID producerId = UUID.randomUUID();

    when(userService.getAuthenticatedUser(jwt)).thenReturn(farmer);

    assertThatThrownBy(() -> favoriteProducerService.favoriteProducer(producerId, jwt))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Apenas customers podem favoritar produtores");

    verify(producerRepository, never()).findById(any(UUID.class));
  }

  private User buildUser(TypeUser type) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName(type == TypeUser.CUSTOMER ? "Maria Customer" : "João Farmer");
    user.setEmail(user.getName().toLowerCase().replace(" ", "") + "@example.com");
    user.setType(type);
    user.setActive(true);
    user.setAuthSub("auth-" + user.getId());
    return user;
  }

  private Producer buildProducer(boolean active) {
    User producerUser = buildUser(TypeUser.FARMER);
    producerUser.setActive(active);

    Producer producer = new Producer();
    producer.setId(producerUser.getId());
    producer.setUser(producerUser);
    producer.setFiscalNumber("12345678901");
    producer.setFiscalNumberType("CPF");
    producer.setFarmName("Fazenda Boa Terra");
    producer.setAvatarS3("avatars/farmer.jpg");
    producer.setAverageRating(new BigDecimal("4.70"));
    producer.setTotalReviews(10);
    return producer;
  }
}
