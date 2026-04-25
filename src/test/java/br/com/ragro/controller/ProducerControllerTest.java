package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerPublicProfileResponse;
import br.com.ragro.controller.response.ProductResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.ForbiddenException;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.ProducerRepository;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.ProductService;
import br.com.ragro.service.StockMovementService;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProducerController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class ProducerControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private ProducerService producerService;
  @MockBean private ProductService productService;
  @MockBean private UserRepository userRepository;
  @MockBean private ProducerRepository producerRepository;
  @MockBean private StockMovementService stockMovementService;

  // ─── GET /{id} ──────────────────────────────────────────────────────────────

  @Test
  void shouldReturn200_whenProducerExists() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-active";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerGetResponse response =
        ProducerGetResponse.builder()
            .id(producerId)
            .name("Test Farmer")
            .email("farmer@test.com")
            .phone("51999999999")
            .fiscalNumber("12345678901234")
            .fiscalNumberType("CNPJ")
            .farmName("Fazenda Teste")
            .totalReviews(0)
            .averageRating(BigDecimal.ZERO)
            .totalOrders(0)
            .totalSalesAmount(BigDecimal.ZERO)
            .build();

    when(producerService.getProducerProfileById(eq(producerId), any())).thenReturn(response);

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Farmer"))
        .andExpect(jsonPath("$.farmName").value("Fazenda Teste"));
  }

  @Test
  void shouldReturn404_whenProducerNotFound() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-active";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    when(producerService.getProducerProfileById(eq(producerId), any()))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn401_whenUserIsInactive() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-inactive";
    User inactiveUser = buildUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveUser));

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Conta desativada ou usuário não encontrado"));
  }

  @Test
  void shouldReturn401_whenUserNotFoundInDatabase() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-unknown";
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Conta desativada ou usuário não encontrado"));
  }

  @Test
  void shouldReturn401_whenNoTokenProvided() throws Exception {
    UUID producerId = UUID.randomUUID();
    mockMvc.perform(get("/producers/" + producerId)).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturn403_whenTokenHasWrongRole() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "some-sub";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    mockMvc
        .perform(
            get("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  // ─── Ownership check on GET /{id} ─────────────────────────────────────────

  @Test
  void getProducer_shouldReturn403_whenFarmerTriesToReadAnotherFarmersProfile() throws Exception {
    UUID farmerAId = UUID.randomUUID();
    UUID farmerBId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer-a";
    User farmerA = buildUser(sub, true);
    farmerA.setId(farmerAId);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(farmerA));

    when(producerService.getProducerProfileById(eq(farmerBId), any()))
        .thenThrow(new ForbiddenException("Você não tem permissão para visualizar este perfil"));

    mockMvc
        .perform(
            get("/producers/" + farmerBId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmera@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void getProducer_shouldReturn200_whenFarmerReadsOwnProfile() throws Exception {
    UUID farmerAId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer-a";
    User farmerA = buildUser(sub, true);
    farmerA.setId(farmerAId);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(farmerA));

    ProducerGetResponse response =
        ProducerGetResponse.builder()
            .id(farmerAId)
            .name("Farmer A")
            .email("farmera@test.com")
            .phone("51999999999")
            .fiscalNumber("12345678901")
            .fiscalNumberType("CPF")
            .farmName("Fazenda A")
            .totalReviews(0)
            .averageRating(BigDecimal.ZERO)
            .totalOrders(0)
            .totalSalesAmount(BigDecimal.ZERO)
            .build();

    when(producerService.getProducerProfileById(eq(farmerAId), any())).thenReturn(response);

    mockMvc
        .perform(
            get("/producers/" + farmerAId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmera@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Farmer A"));
  }

  // ─── PUT /{id} ──────────────────────────────────────────────────────────────

  @Test
  void putProducer_shouldReturn200_whenFarmerUpdatesOwnProfile() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("João Atualizado");
    request.setFarmName("Fazenda Nova");

    ProducerGetResponse response =
        ProducerGetResponse.builder()
            .id(producerId)
            .name("João Atualizado")
            .email("farmer@test.com")
            .phone("51999999999")
            .farmName("Fazenda Nova")
            .fiscalNumber("12345678901")
            .fiscalNumberType("CPF")
            .totalReviews(0)
            .averageRating(BigDecimal.ZERO)
            .totalOrders(0)
            .totalSalesAmount(BigDecimal.ZERO)
            .build();

    when(producerService.updateProducerProfile(eq(producerId), any(), any())).thenReturn(response);

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("João Atualizado"))
        .andExpect(jsonPath("$.farmName").value("Fazenda Nova"));
  }

  @Test
  void putProducer_shouldReturn403_whenFarmerTriesToUpdateAnotherProfile() throws Exception {
    UUID otherProducerId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Hacker");

    when(producerService.updateProducerProfile(eq(otherProducerId), any(), any()))
        .thenThrow(new ForbiddenException("Você não tem permissão para alterar este perfil"));

    mockMvc
        .perform(
            put("/producers/" + otherProducerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void putProducer_shouldReturn401_whenNoTokenProvided() throws Exception {
    UUID producerId = UUID.randomUUID();
    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Test");

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void putProducer_shouldReturn403_whenRoleIsNotFarmer() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "some-customer-sub";
    User activeUser = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Test");

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "cust@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void putProducer_shouldReturn401_whenUserIsInactive() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-inactive";
    User inactiveUser = buildUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveUser));

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setName("Test");

    mockMvc
        .perform(
            put("/producers/" + producerId)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Conta desativada ou usuário não encontrado"));
  }

  // ─── GET /{id}/profile ──────────────────────────────────────────────────────

  @Test
  void getPublicProducerProfile_shouldReturn200_whenCustomerRequestsActiveProducer()
      throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-customer";
    User activeCustomer = buildCustomerUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeCustomer));

    ProducerPublicProfileResponse response =
        ProducerPublicProfileResponse.builder()
            .id(producerId)
            .name("João Nascimento")
            .farmName("Mato Grosso, Brasil")
            .description("Agricultura regenerativa")
            .story("Dedicados à agricultura regenerativa")
            .photoUrl("https://cdn.test/profile.jpg")
            .avatarS3("https://cdn.test/avatar.jpg")
            .displayPhotoS3("https://cdn.test/cover.jpg")
            .phone("51999999999")
            .averageRating(new BigDecimal("4.90"))
            .totalReviews(15)
            .memberSince(LocalDate.of(2018, 1, 10))
            .build();

    when(producerService.getPublicProfileById(producerId)).thenReturn(response);

    mockMvc
        .perform(
            get("/producers/" + producerId + "/profile")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("João Nascimento"))
        .andExpect(jsonPath("$.farmName").value("Mato Grosso, Brasil"))
        .andExpect(jsonPath("$.averageRating").value(4.9))
        .andExpect(jsonPath("$.totalReviews").value(15))
        .andExpect(jsonPath("$.fiscalNumber").doesNotExist())
        .andExpect(jsonPath("$.totalSalesAmount").doesNotExist())
        .andExpect(jsonPath("$.paymentMethods").doesNotExist());
  }

  @Test
  void getPublicProducerProfile_shouldReturn404_whenProducerNotFound() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-customer";
    User activeCustomer = buildCustomerUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeCustomer));
    when(producerService.getPublicProfileById(producerId))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(
            get("/producers/" + producerId + "/profile")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Produtor não encontrado"));
  }

  @Test
  void getPublicProducerProfile_shouldReturn401_whenNoTokenProvided() throws Exception {
    UUID producerId = UUID.randomUUID();
    mockMvc
        .perform(get("/producers/" + producerId + "/profile"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getPublicProducerProfile_shouldReturn403_whenRoleIsFarmer() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer";
    User activeFarmer = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeFarmer));

    mockMvc
        .perform(
            get("/producers/" + producerId + "/profile")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden());
  }

  // ─── GET /{id}/products ──────────────────────────────────────────────────────

  @Test
  void getProducerProducts_shouldReturn200WithProducts_whenCustomerRequestsActiveProducts()
      throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-customer";
    User activeCustomer = buildCustomerUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeCustomer));

    List<ProductResponse> products =
        List.of(
            ProductResponse.builder()
                .id(UUID.randomUUID())
                .farmerId(producerId)
                .name("Organic strawberries")
                .price(new BigDecimal("18.90"))
                .unityType("kg")
                .stockQuantity(new BigDecimal("35.500"))
                .active(true)
                .build());
    when(productService.getActiveProductsByProducerId(producerId)).thenReturn(products);

    mockMvc
        .perform(
            get("/producers/" + producerId + "/products")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Organic strawberries"))
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  void getProducerProducts_shouldReturn200WithEmptyList_whenProducerHasNoActiveProducts()
      throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-customer";
    User activeCustomer = buildCustomerUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeCustomer));
    when(productService.getActiveProductsByProducerId(producerId)).thenReturn(List.of());

    mockMvc
        .perform(
            get("/producers/" + producerId + "/products")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void getProducerProducts_shouldReturn404_whenProducerNotFound() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-customer";
    User activeCustomer = buildCustomerUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeCustomer));
    when(productService.getActiveProductsByProducerId(producerId))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(
            get("/producers/" + producerId + "/products")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Produtor não encontrado"));
  }

  @Test
  void getProducerProducts_shouldReturn401_whenNoTokenProvided() throws Exception {
    UUID producerId = UUID.randomUUID();
    mockMvc
        .perform(get("/producers/" + producerId + "/products"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getProducerProducts_shouldReturn403_whenRoleIsFarmer() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-farmer";
    User activeFarmer = buildUser(sub, true);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(activeFarmer));

    mockMvc
        .perform(
            get("/producers/" + producerId + "/products")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "farmer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void getProducerProducts_shouldReturn401_whenUserIsInactive() throws Exception {
    UUID producerId = UUID.randomUUID();
    String sub = "keycloak-sub-inactive";
    User inactiveCustomer = buildCustomerUser(sub, false);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(inactiveCustomer));

    mockMvc
        .perform(
            get("/producers/" + producerId + "/products")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.claim("sub", sub).claim("email", "customer@test.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Conta desativada ou usuário não encontrado"));
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private User buildUser(String authSub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test Farmer");
    user.setEmail("farmer@test.com");
    user.setPhone("51999999999");
    user.setType(TypeUser.FARMER);
    user.setActive(active);
    user.setAuthSub(authSub);
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }

  private User buildCustomerUser(String authSub, boolean active) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test Customer");
    user.setEmail("customer@test.com");
    user.setPhone("51988888888");
    user.setType(TypeUser.CUSTOMER);
    user.setActive(active);
    user.setAuthSub(authSub);
    user.setCreatedAt(OffsetDateTime.now().minusDays(1));
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }
}
