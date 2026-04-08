package br.com.ragro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.ProducerRegistrationService;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AdminController.class)
class AdminControllerProducerRegistrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private ProducerRegistrationService producerRegistrationService;

    @MockBean private ProducerService producerService;

    @MockBean private UserService userService;

    @MockBean private UserRepository userRepository;

    private ProducerRegistrationRequest validRequest() {
        ProducerRegistrationRequest request = new ProducerRegistrationRequest();
        request.setName("João Silva");
        request.setPhone("51988888888");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setFiscalNumber("12345678901");
        request.setFiscalNumberType("CPF");
        request.setFarmName("Fazenda São João");
        request.setDescription("Produção orgânica");
        request.setAvatarS3(null);
        request.setDisplayPhotoS3(null);
        return request;
    }

    private ProducerRegistrationResponse validResponse(UUID id) {
        return ProducerRegistrationResponse.builder()
                .id(id)
                .name("João Silva")
                .email("joao@example.com")
                .phone("51988888888")
                .type("farmer")
                .active(true)
                .fiscalNumber("12345678901")
                .fiscalNumberType("CPF")
                .farmName("Fazenda São João")
                .description("Produção orgânica")
                .totalReviews(0)
                .averageRating(BigDecimal.ZERO)
                .totalOrders(0)
                .totalSalesAmount(BigDecimal.ZERO)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn200WithProducerData_whenRequestIsValid() throws Exception {
        UUID id = UUID.randomUUID();
        when(producerRegistrationService.register(any())).thenReturn(validResponse(id));

        MvcResult result = mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.phone").value("51988888888"))
                .andExpect(jsonPath("$.type").value("farmer"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fiscalNumber").value("12345678901"))
                .andExpect(jsonPath("$.fiscalNumberType").value("CPF"))
                .andExpect(jsonPath("$.farmName").value("Fazenda São João"))
                .andExpect(jsonPath("$.totalReviews").value(0))
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andReturn();

        ProducerRegistrationResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProducerRegistrationResponse.class);
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getEmail()).isEqualTo("joao@example.com");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenNameIsBlank() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setName("");

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenEmailIsInvalid() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setEmail("email-invalido");

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenPasswordDoesNotMeetRequirements() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setPassword("fraca");

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenFiscalNumberIsInvalid() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setFiscalNumber("123");

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenFiscalNumberTypeIsInvalid() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setFiscalNumberType("RG");

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenEmailAlreadyRegistered() throws Exception {
        when(producerRegistrationService.register(any()))
                .thenThrow(new BusinessException("E-mail already registered"));

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenFiscalNumberAlreadyRegistered() throws Exception {
        when(producerRegistrationService.register(any()))
                .thenThrow(new BusinessException("Fiscal number already registered"));

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturnTimestamps_whenProducerIsRegistered() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusSeconds(1);
        OffsetDateTime updatedAt = OffsetDateTime.now();

        ProducerRegistrationResponse response = validResponse(id);
        response.setCreatedAt(createdAt);
        response.setUpdatedAt(updatedAt);

        when(producerRegistrationService.register(any())).thenReturn(response);

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenFarmNameIsBlank() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setFarmName("");

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}