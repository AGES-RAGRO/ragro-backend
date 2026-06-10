package br.com.ragro.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.controller.AuthController;
import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.request.CustomerRegistrationRequest;
import br.com.ragro.controller.response.CustomerRegistrationResponse;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.CustomerRegistrationService;
import br.com.ragro.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AuthController.class)
@Import({
  SecurityConfig.class,
  KeycloakRolesConverter.class,
  CorsConfig.class,
  ActiveUserFilter.class,
  RateLimitConfig.class,
  RateLimitFilterTest.RateLimitTestConfig.class
})
@TestPropertySource(
    properties = {
      "keycloak.public-url=http://localhost:8180",
      "keycloak.realm=ragro",
      "ragro.rate-limit.enabled=true",
      "ragro.rate-limit.register-per-minute=3",
      "ragro.rate-limit.forgot-per-minute=3"
    })
class RateLimitFilterTest {

  @TestConfiguration
  @EnableConfigurationProperties(RateLimitProperties.class)
  static class RateLimitTestConfig {}

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private CustomerRegistrationService customerRegistrationService;
  @MockBean private UserService userService;
  @MockBean private UserRepository userRepository;

  // Blinda contra contaminação do SecurityContextHolder (thread-local) por uma classe de teste
  // anterior: sem isto, um JWT residual fazia o ActiveUserFilter responder 401 nos registros.
  @org.junit.jupiter.api.BeforeEach
  void clearSecurityContext() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Test
  void register_shouldReturn429WithRetryAfter_whenIpExceedsLimit() throws Exception {
    when(customerRegistrationService.register(any()))
        .thenReturn(CustomerRegistrationResponse.builder().id(UUID.randomUUID()).build());

    for (int i = 0; i < 3; i++) {
      mockMvc.perform(registerRequest("10.0.0.1")).andExpect(status().isCreated());
    }

    mockMvc
        .perform(registerRequest("10.0.0.1"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.path").value("/auth/register/customer"));
  }

  @Test
  void register_shouldNotLimitDifferentIp_whenAnotherIpExceededLimit() throws Exception {
    when(customerRegistrationService.register(any()))
        .thenReturn(CustomerRegistrationResponse.builder().id(UUID.randomUUID()).build());

    for (int i = 0; i < 4; i++) {
      mockMvc.perform(registerRequest("10.0.0.2"));
    }

    mockMvc.perform(registerRequest("10.0.0.3")).andExpect(status().isCreated());
  }

  @Test
  void forgot_shouldHaveIndependentBucket_whenRegisterBucketIsExhausted() throws Exception {
    when(customerRegistrationService.register(any()))
        .thenReturn(CustomerRegistrationResponse.builder().id(UUID.randomUUID()).build());

    for (int i = 0; i < 4; i++) {
      mockMvc.perform(registerRequest("10.0.0.4"));
    }

    mockMvc
        .perform(
            post("/auth/password/forgot")
                .with(csrf())
                .with(
                    request -> {
                      request.setRemoteAddr("10.0.0.4");
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"maria@example.com\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void register_shouldUseLastForwardedAddress_whenBehindProxy() throws Exception {
    when(customerRegistrationService.register(any()))
        .thenReturn(CustomerRegistrationResponse.builder().id(UUID.randomUUID()).build());

    // O último hop é o único appendado pelo proxy confiável (ALB); a esquerda é forjável.
    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(registerRequest("172.16.0.1").header("X-Forwarded-For", "200.1.2.3"))
          .andExpect(status().isCreated());
    }

    // Spoofing à esquerda do header NÃO escapa do limite: o último valor continua o mesmo.
    mockMvc
        .perform(
            registerRequest("172.16.0.9").header("X-Forwarded-For", "6.6.6.6, 7.7.7.7, 200.1.2.3"))
        .andExpect(status().isTooManyRequests());

    // Cliente real diferente (último valor diferente): passa.
    mockMvc
        .perform(registerRequest("172.16.0.1").header("X-Forwarded-For", "200.9.9.9"))
        .andExpect(status().isCreated());
  }

  private MockHttpServletRequestBuilder registerRequest(String remoteAddr) throws Exception {
    return post("/auth/register/customer")
        .with(csrf())
        .with(
            request -> {
              request.setRemoteAddr(remoteAddr);
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(validRegistrationRequest()));
  }

  private CustomerRegistrationRequest validRegistrationRequest() {
    AddressRequest address = new AddressRequest();
    address.setStreet("Rua das Flores");
    address.setNumber("123");
    address.setComplement("Apto 1");
    address.setNeighborhood("Centro");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90000000");

    CustomerRegistrationRequest request = new CustomerRegistrationRequest();
    request.setName("Maria Silva");
    request.setEmail("maria@example.com");
    request.setPassword("Senha@123");
    request.setPhone("51987654321");
    request.setFiscalNumber("52998224725");
    request.setAddress(address);
    return request;
  }
}
