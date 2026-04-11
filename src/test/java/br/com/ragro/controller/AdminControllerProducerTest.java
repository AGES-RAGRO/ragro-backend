package br.com.ragro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.controller.request.ProducerUpdateRequest;
import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ProducerRegistrationService;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AdminController.class)
class AdminControllerProducerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ProducerService producerService;

  @MockBean private UserService userService;

  @MockBean private UserRepository userRepository;

    @MockBean private ProducerRegistrationService producerRegistrationService;

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProducers_shouldReturn200WithPageOfProducers() throws Exception {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    List<ProducerResponse> producers =
        List.of(
            ProducerResponse.builder()
                .id(id1)
                .name("Eduardo Fazendeiro")
                .email("eduardo@example.com")
                .phone("51988888888")
                .active(true)
                .address("Porto Alegre, RS - Rua das Acácias 45")
                .createdAt(OffsetDateTime.now().minusDays(2))
                .updatedAt(OffsetDateTime.now())
                .build(),
            ProducerResponse.builder()
                .id(id2)
                .name("Maria Farmer")
                .email("maria@example.com")
                .phone("51977777777")
                .active(false)
                .address(null)
                .createdAt(OffsetDateTime.now().minusDays(1))
                .updatedAt(OffsetDateTime.now())
                .build());

    Page<ProducerResponse> page = new PageImpl<>(producers, PageRequest.of(0, 10), 2);
    when(producerService.getAllProducers(PageRequest.of(0, 10))).thenReturn(page);

    mockMvc
        .perform(get("/admin/producers").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].id").value(id1.toString()))
        .andExpect(jsonPath("$.content[0].name").value("Eduardo Fazendeiro"))
        .andExpect(jsonPath("$.content[1].id").value(id2.toString()))
        .andExpect(jsonPath("$.content[1].name").value("Maria Farmer"))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProducers_shouldReturnInactiveProducers() throws Exception {
    UUID inactiveId = UUID.randomUUID();
    ProducerResponse inactive =
        ProducerResponse.builder()
            .id(inactiveId)
            .name("Produtor Inativo")
            .email("inativo@example.com")
            .active(false)
            .build();

    Page<ProducerResponse> page = new PageImpl<>(List.of(inactive), PageRequest.of(0, 10), 1);
    when(producerService.getAllProducers(PageRequest.of(0, 10))).thenReturn(page);

    mockMvc
        .perform(get("/admin/producers").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].active").value(false))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProducers_shouldReturn200WithEmptyPage_whenNoProducersExist() throws Exception {
    Page<ProducerResponse> empty = Page.empty(PageRequest.of(0, 10));
    when(producerService.getAllProducers(PageRequest.of(0, 10))).thenReturn(empty);

    mockMvc
        .perform(get("/admin/producers").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProducer_shouldReturn200WithProducerDetails_whenProducerExists() throws Exception {
    UUID producerId = UUID.randomUUID();
    ProducerResponse producerResponse =
        ProducerResponse.builder()
            .id(producerId)
            .name("João Farmer")
            .email("joao@example.com")
            .phone("51988888888")
            .active(true)
            .address("Porto Alegre, RS - Rua das Acácias 45")
            .createdAt(OffsetDateTime.now().minusDays(1))
            .updatedAt(OffsetDateTime.now())
            .build();

    when(producerService.getProducerById(producerId)).thenReturn(producerResponse);

    MvcResult result =
        mockMvc
            .perform(get("/admin/producers/{id}", producerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(producerId.toString()))
            .andExpect(jsonPath("$.name").value("João Farmer"))
            .andExpect(jsonPath("$.email").value("joao@example.com"))
            .andExpect(jsonPath("$.phone").value("51988888888"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.address").value("Porto Alegre, RS - Rua das Acácias 45"))
            .andReturn();

    ProducerResponse response =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), ProducerResponse.class);
    assertThat(response.getId()).isEqualTo(producerId);
    assertThat(response.getEmail()).isEqualTo("joao@example.com");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void getProducer_shouldReturn404_whenProducerNotFound() throws Exception {
    UUID producerId = UUID.randomUUID();
    when(producerService.getProducerById(producerId))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(get("/admin/producers/{id}", producerId))
        .andExpect(status().isNotFound());
  }

  @WithMockUser(roles = "ADMIN")
  @Test
  void getProducer_shouldReturn404_whenUserIsNotFarmer() throws Exception {
    UUID customerId = UUID.randomUUID();
    when(producerService.getProducerById(customerId))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(get("/admin/producers/{id}", customerId))
        .andExpect(status().isNotFound());
  }

  @WithMockUser(roles = "ADMIN")
  @Test
  void getProducer_shouldReturn404_whenUserIsAdmin() throws Exception {
    UUID adminId = UUID.randomUUID();
    when(producerService.getProducerById(adminId))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(get("/admin/producers/{id}", adminId))
        .andExpect(status().isNotFound());
  }

  @WithMockUser(roles = "ADMIN")
  @Test
  void getProducer_shouldReturnProducerWithTimestamps() throws Exception {
    UUID producerId = UUID.randomUUID();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(5);
    OffsetDateTime updatedAt = OffsetDateTime.now();

    ProducerResponse producerResponse =
        ProducerResponse.builder()
            .id(producerId)
            .name("Maria Farmer")
            .email("maria@example.com")
            .phone("51987654321")
            .active(true)
            .address("Canoas, RS - Rua dos Jasmins 120")
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    when(producerService.getProducerById(producerId)).thenReturn(producerResponse);

    mockMvc
        .perform(get("/admin/producers/{id}", producerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(producerId.toString()))
        .andExpect(jsonPath("$.name").value("Maria Farmer"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty());
  }

  // ─── PUT /admin/producers/{id} ───────────────────────────────────────────────

  @WithMockUser(roles = "ADMIN")
  @Test
  void putProducer_shouldReturn200_whenAdminUpdatesProducer() throws Exception {
    UUID producerId = UUID.randomUUID();

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setFarmName("Fazenda Atualizada");
    request.setPhone("51911111111");

    ProducerGetResponse response =
        ProducerGetResponse.builder()
            .id(producerId)
            .name("João Farmer")
            .email("joao@example.com")
            .phone("51911111111")
            .farmName("Fazenda Atualizada")
            .fiscalNumber("12345678901")
            .fiscalNumberType("CPF")
            .totalReviews(0)
            .averageRating(BigDecimal.ZERO)
            .totalOrders(0)
            .totalSalesAmount(BigDecimal.ZERO)
            .paymentMethods(List.of())
            .build();

    when(producerService.updateProducerProfile(eq(producerId), any(), any())).thenReturn(response);

    mockMvc
        .perform(
            put("/admin/producers/{id}", producerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.farmName").value("Fazenda Atualizada"))
        .andExpect(jsonPath("$.phone").value("51911111111"));
  }

  @WithMockUser(roles = "ADMIN")
  @Test
  void putProducer_shouldReturn404_whenProducerNotFound() throws Exception {
    UUID producerId = UUID.randomUUID();

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setFarmName("Fazenda X");

    when(producerService.updateProducerProfile(eq(producerId), any(), any()))
        .thenThrow(new NotFoundException("Produtor não encontrado"));

    mockMvc
        .perform(
            put("/admin/producers/{id}", producerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @WithMockUser(roles = "FARMER")
  @Test
  void putProducer_shouldReturn403_whenCalledByNonAdmin() throws Exception {
    UUID producerId = UUID.randomUUID();

    ProducerUpdateRequest request = new ProducerUpdateRequest();
    request.setFarmName("Fazenda X");

    mockMvc
        .perform(
            put("/admin/producers/{id}", producerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }
}
