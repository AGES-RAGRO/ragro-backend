package br.com.ragro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.controller.request.AvailabilityRequest;
import br.com.ragro.controller.request.PaymentMethodRequest;
import br.com.ragro.controller.request.ProducerRegistrationRequest;
import br.com.ragro.controller.request.AddressRequest;
import br.com.ragro.controller.response.ProducerRegistrationResponse;
import br.com.ragro.exception.ConflictException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.CustomerService;
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

        @MockBean private CustomerService customerService;

    @MockBean private UserService userService;

    @MockBean private UserRepository userRepository;

    private PaymentMethodRequest buildPixMethod() {
        PaymentMethodRequest pm = new PaymentMethodRequest();
        pm.setType("pix");
        pm.setPixKeyType("email");
        pm.setPixKey("joao@example.com");
        return pm;
    }

    private PaymentMethodRequest buildBankMethod() {
        PaymentMethodRequest pm = new PaymentMethodRequest();
        pm.setType("bank_account");
        pm.setBankName("Banco do Brasil");
        pm.setAgency("1234");
        pm.setAccountNumber("56789-0");
        pm.setAccountType("checking");
        pm.setHolderName("João Silva");
        return pm;
    }

    private ProducerRegistrationRequest validRequest() {
        AddressRequest address = new AddressRequest();
        address.setStreet("Rua das Flores");
        address.setNumber("123");
        address.setCity("Porto Alegre");
        address.setNeighborhood("Centro");
        address.setState("RS");
        address.setZipCode("90010120");

        AvailabilityRequest availability = new AvailabilityRequest();
        availability.setWeekday((short) 1);
        availability.setOpensAt("08:00");
        availability.setClosesAt("18:00");

        ProducerRegistrationRequest request = new ProducerRegistrationRequest();
        request.setName("João Silva");
        request.setPhone("51988888888");
        request.setEmail("joao@example.com");
        request.setPassword("Senha@123");
        request.setFiscalNumber("52998224725");
        request.setFiscalNumberType("CPF");
        request.setFarmName("Fazenda São João");
        request.setDescription("Produção orgânica");
        request.setAddress(address);
        request.setPaymentMethods(List.of(buildPixMethod(), buildBankMethod()));
        request.setAvailability(List.of(availability));
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
                .fiscalNumber("52998224725")
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
    void registerProducer_shouldReturn201WithProducerData_whenRequestIsValid() throws Exception {
        UUID id = UUID.randomUUID();
        when(producerRegistrationService.register(any())).thenReturn(validResponse(id));

        MvcResult result = mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.phone").value("51988888888"))
                .andExpect(jsonPath("$.type").value("farmer"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fiscalNumber").value("52998224725"))
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
    void registerProducer_shouldReturn409_whenEmailAlreadyRegistered() throws Exception {
        when(producerRegistrationService.register(any()))
                .thenThrow(new ConflictException("E-mail already registered"));

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn409_whenFiscalNumberAlreadyRegistered() throws Exception {
        when(producerRegistrationService.register(any()))
                .thenThrow(new ConflictException("Fiscal number already registered"));

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
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
                .andExpect(status().isCreated())
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenPaymentMethodsIsEmpty() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setPaymentMethods(java.util.List.of());

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenPaymentMethodsHasOnlyOneItem() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setPaymentMethods(List.of(buildPixMethod()));

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenAvailabilityIsEmpty() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setAvailability(List.of());

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn400_whenPhoneHasNonDigitCharacters() throws Exception {
        ProducerRegistrationRequest request = validRequest();
        request.setPhone("(51) 98888-8888");

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void registerProducer_shouldReturn201_whenMultipleAvailabilityDaysAreProvided() throws Exception {
        UUID id = UUID.randomUUID();
        ProducerRegistrationRequest request = validRequest();
        
        // Criar disponibilidade para segunda a sábado (1 a 6)
        List<AvailabilityRequest> multipleDays = new java.util.ArrayList<>();
        
        // Segunda-feira: 08:00 - 18:00
        AvailabilityRequest monday = new AvailabilityRequest();
        monday.setWeekday((short) 1);
        monday.setOpensAt("08:00");
        monday.setClosesAt("18:00");
        multipleDays.add(monday);
        
        // Terça-feira: 08:00 - 18:00
        AvailabilityRequest tuesday = new AvailabilityRequest();
        tuesday.setWeekday((short) 2);
        tuesday.setOpensAt("08:00");
        tuesday.setClosesAt("18:00");
        multipleDays.add(tuesday);
        
        // Quarta-feira: 08:00 - 18:00
        AvailabilityRequest wednesday = new AvailabilityRequest();
        wednesday.setWeekday((short) 3);
        wednesday.setOpensAt("08:00");
        wednesday.setClosesAt("18:00");
        multipleDays.add(wednesday);
        
        // Quinta-feira: 08:00 - 18:00
        AvailabilityRequest thursday = new AvailabilityRequest();
        thursday.setWeekday((short) 4);
        thursday.setOpensAt("08:00");
        thursday.setClosesAt("18:00");
        multipleDays.add(thursday);
        
        // Sexta-feira: 08:00 - 18:00
        AvailabilityRequest friday = new AvailabilityRequest();
        friday.setWeekday((short) 5);
        friday.setOpensAt("08:00");
        friday.setClosesAt("18:00");
        multipleDays.add(friday);
        
        // Sábado: 08:00 - 13:00 (horário reduzido)
        AvailabilityRequest saturday = new AvailabilityRequest();
        saturday.setWeekday((short) 6);
        saturday.setOpensAt("08:00");
        saturday.setClosesAt("13:00");
        multipleDays.add(saturday);
        
        request.setAvailability(multipleDays);
        
        when(producerRegistrationService.register(any())).thenReturn(validResponse(id));

        mockMvc
                .perform(post("/admin/producers")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.active").value(true));
    }
}