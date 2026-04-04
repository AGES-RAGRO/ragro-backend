package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.request.UpdateUserRequest;
import br.com.ragro.controller.response.CustomerResponse;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.exception.UnauthorizedException;
import br.com.ragro.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SuppressWarnings("null")
@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class CustomerControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private CustomerService customerService;
  @MockBean private JwtDecoder jwtDecoder;

  // ─── GET /customers/me ────────────────────────────────────────────────────

  @Test
  void getMyCustomer_shouldReturn200_whenAuthenticatedAsCustomer() throws Exception {
    CustomerResponse response = buildCustomerResponse();
    when(customerService.getMyCustomer(any())).thenReturn(response);

    mockMvc
        .perform(get("/customers/me").with(asCustomer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(response.getId().toString()))
        .andExpect(jsonPath("$.name").value(response.getName()))
        .andExpect(jsonPath("$.email").value(response.getEmail()))
        .andExpect(jsonPath("$.addresses").isArray());
  }

  @Test
  void getMyCustomer_shouldReturn403_whenAuthenticatedWithoutCustomerRole() throws Exception {
    mockMvc
        .perform(get("/customers/me").with(asFarmer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getMyCustomer_shouldReturn401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/customers/me")).andExpect(status().isUnauthorized());
  }

  // ─── PUT /customers/me ────────────────────────────────────────────────────

  @Test
  void updateMyCustomer_shouldReturn200_whenRequestIsValid() throws Exception {
    CustomerResponse response = buildCustomerResponse();
    response.setName("Novo Nome");
    response.setPhone("51988887777");
    when(customerService.updateMyCustomer(any(UpdateUserRequest.class), any()))
        .thenReturn(response);

    UpdateUserRequest request = new UpdateUserRequest();
    request.setName("Novo Nome");
    request.setPhone("51988887777");

    mockMvc
        .perform(jsonPut("/customers/me", request).with(asCustomer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Novo Nome"))
        .andExpect(jsonPath("$.phone").value("51988887777"));
  }

  @Test
  void updateMyCustomer_shouldReturn400_whenNameIsBlank() throws Exception {
    UpdateUserRequest request = new UpdateUserRequest();
    request.setName("");

    mockMvc
        .perform(jsonPut("/customers/me", request).with(asCustomer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateMyCustomer_shouldReturn400_whenPhoneExceedsMaxLength() throws Exception {
    UpdateUserRequest request = new UpdateUserRequest();
    request.setName("João Silva");
    request.setPhone("1".repeat(21));

    mockMvc
        .perform(jsonPut("/customers/me", request).with(asCustomer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateMyCustomer_shouldReturn403_whenAuthenticatedWithoutCustomerRole() throws Exception {
    UpdateUserRequest request = new UpdateUserRequest();
    request.setName("João Silva");

    mockMvc
        .perform(jsonPut("/customers/me", request).with(asFarmer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateMyCustomer_shouldReturn401_whenUnauthenticated() throws Exception {
    UpdateUserRequest request = new UpdateUserRequest();
    request.setName("João Silva");

    mockMvc.perform(jsonPut("/customers/me", request)).andExpect(status().isUnauthorized());
  }

  // ─── GET /customers/{id} ─────────────────────────────────────────────────

  @Test
  void getCustomerById_shouldReturn200_whenCustomerExists() throws Exception {
    UUID id = UUID.randomUUID();
    CustomerResponse response = buildCustomerResponse();
    when(customerService.getCustomerById(eq(id), any())).thenReturn(response);

    mockMvc
        .perform(get("/customers/{id}", id).with(asCustomer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(response.getId().toString()))
        .andExpect(jsonPath("$.name").value(response.getName()));
  }

  @Test
  void getCustomerById_shouldReturn404_whenCustomerDoesNotExist() throws Exception {
    UUID id = UUID.randomUUID();
    when(customerService.getCustomerById(eq(id), any()))
        .thenThrow(new NotFoundException("Customer not found"));

    mockMvc
        .perform(get("/customers/{id}", id).with(asCustomer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Customer not found"));
  }

  @Test
  void getCustomerById_shouldReturn401_whenCallerIsNotAdmin() throws Exception {
    UUID id = UUID.randomUUID();
    when(customerService.getCustomerById(eq(id), any()))
        .thenThrow(new UnauthorizedException("Access restricted to admins"));

    mockMvc
        .perform(get("/customers/{id}", id).with(asCustomer()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Access restricted to admins"));
  }

  @Test
  void getCustomerById_shouldReturn403_whenAuthenticatedWithoutCustomerRole() throws Exception {
    mockMvc
        .perform(get("/customers/{id}", UUID.randomUUID()).with(asFarmer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getCustomerById_shouldReturn401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/customers/{id}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private static RequestPostProcessor asCustomer() {
    return Objects.requireNonNull(
        jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
  }

  private static RequestPostProcessor asFarmer() {
    return Objects.requireNonNull(
        jwt().authorities(new SimpleGrantedAuthority("ROLE_FARMER")));
  }

  private MockHttpServletRequestBuilder jsonPut(String url, Object body) throws Exception {
    return put(url)
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body)));
  }

  private CustomerResponse buildCustomerResponse() {
    return CustomerResponse.builder()
        .id(UUID.randomUUID())
        .name("Maria Silva")
        .email("maria@example.com")
        .phone("51999999999")
        .active(true)
        .createdAt(OffsetDateTime.now().minusDays(1))
        .updatedAt(OffsetDateTime.now())
        .addresses(List.of())
        .build();
  }
}
