package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.ActiveUserFilter;
import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.CustomerRegistrationRequest;
import br.com.ragro.controller.response.CustomerRegistrationResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.CustomerRegistrationService;
import br.com.ragro.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class, ActiveUserFilter.class})
@TestPropertySource(properties = {
    "keycloak.public-url=http://localhost:8180",
    "keycloak.realm=ragro"
})
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private CustomerRegistrationService customerRegistrationService;
  @MockBean private UserService userService;
  @MockBean private UserRepository userRepository;

  // ─── POST /auth/register/customer ────────────────────────────────────────

  @Test
  void registerCustomer_shouldReturn201_whenRequestIsValid() throws Exception {
    UUID id = UUID.randomUUID();
    CustomerRegistrationResponse response = CustomerRegistrationResponse.builder()
        .id(id)
        .name("Maria Silva")
        .email("maria@example.com")
        .phone("51987654321")
        .type("customer")
        .active(true)
        .fiscalNumber("52998224725")
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    when(customerRegistrationService.register(any())).thenReturn(response);

    mockMvc.perform(post("/auth/register/customer")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRegistrationRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.email").value("maria@example.com"))
        .andExpect(jsonPath("$.type").value("customer"));
  }

  @Test
  void registerCustomer_shouldReturn400_whenEmailAlreadyRegistered() throws Exception {
    when(customerRegistrationService.register(any()))
        .thenThrow(new BusinessException("E-mail already registered"));

    mockMvc.perform(post("/auth/register/customer")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validRegistrationRequest())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("E-mail already registered"));
  }

  @Test
  void registerCustomer_shouldReturn400_whenRequestBodyIsInvalid() throws Exception {
    CustomerRegistrationRequest invalid = new CustomerRegistrationRequest();

    mockMvc.perform(post("/auth/register/customer")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerCustomer_shouldReturn400_whenCpfIsArithmeticallyInvalid() throws Exception {
    CustomerRegistrationRequest request = validRegistrationRequest();
    request.setFiscalNumber("12345678901");

    mockMvc.perform(post("/auth/register/customer")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // ─── GET /auth/config ─────────────────────────────────────────────────────

  @Test
  void getConfig_shouldReturn200_withKeycloakTokenUrl() throws Exception {
    mockMvc.perform(get("/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenUrl").value(
            "http://localhost:8180/realms/ragro/protocol/openid-connect/token"))
        .andExpect(jsonPath("$.clientId").value("ragro-app"))
        .andExpect(jsonPath("$.realm").value("ragro"));
  }

  @Test
  void getConfig_shouldReturn200_withoutAuthentication() throws Exception {
    mockMvc.perform(get("/auth/config"))
        .andExpect(status().isOk());
  }

  // ─── GET /auth/session ────────────────────────────────────────────────────

  @Test
  void getSession_shouldReturn200_withUserData_whenJwtIsValid() throws Exception {
    String sub = "keycloak-sub-abc";
    User user = buildUser(sub, TypeUser.CUSTOMER);
    when(userRepository.findByAuthSub(sub)).thenReturn(Optional.of(user));
    when(userService.getAuthenticatedUser(any())).thenReturn(user);

    mockMvc.perform(get("/auth/session")
            .with(SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("sub", sub).claim("email", "user@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test User"))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.type").value("customer"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void getSession_shouldReturn401_whenNoJwtProvided() throws Exception {
    mockMvc.perform(get("/auth/session"))
        .andExpect(status().isUnauthorized());
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private CustomerRegistrationRequest validRegistrationRequest() {
    AddressRequest address = new AddressRequest();
    address.setStreet("Rua das Flores");
    address.setNumber("123");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90010120");

    CustomerRegistrationRequest request = new CustomerRegistrationRequest();
    request.setName("Maria Silva");
    request.setEmail("maria@example.com");
    request.setPhone("51987654321");
    request.setPassword("Senha@123");
    request.setFiscalNumber("52998224725");
    request.setAddress(address);
    return request;
  }

  private User buildUser(String authSub, TypeUser type) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setName("Test User");
    user.setEmail("user@example.com");
    user.setPhone("51999999999");
    user.setType(type);
    user.setActive(true);
    user.setAuthSub(authSub);
    return user;
  }
}
