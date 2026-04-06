package br.com.ragro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AdminController.class)
class AdminControllerProducerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ProducerService producerService;

  @MockBean private UserService userService;

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
}
