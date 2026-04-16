package br.com.ragro.controller;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.controller.response.ProducerResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.CustomerService;
import br.com.ragro.service.ProducerRegistrationService;
import br.com.ragro.service.ProducerService;
import br.com.ragro.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
 
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class})
class AdminControllerDeactivateProducerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @Autowired
    private ObjectMapper objectMapper;
 
    @MockBean
    private ProducerService producerService;

        @MockBean
        private CustomerService customerService;
 
    @MockBean
    private UserService userService;
 
    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

        @MockBean
        private ProducerRegistrationService producerRegistrationService;
 
    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateProducer_shouldReturn200WithUpdatedProducer_whenDeactivating() throws Exception {
        UUID producerId = UUID.randomUUID();
        ProducerResponse producerResponse = ProducerResponse.builder()
                .id(producerId)
                .name("João Farmer")
                .email("joao@example.com")
                .phone("51988888888")
                .active(false)
                .createdAt(OffsetDateTime.now().minusDays(1))
                .updatedAt(OffsetDateTime.now())
                .build();
 
        when(producerService.deactivateProducer(producerId)).thenReturn(producerResponse);
 
        MvcResult result = mockMvc
                .perform(patch("/admin/producers/{id}/deactivate", producerId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(producerId.toString()))
                .andExpect(jsonPath("$.name").value("João Farmer"))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();
 
        ProducerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProducerResponse.class);
        assertThat(response.getId()).isEqualTo(producerId);
        assertThat(response.isActive()).isFalse();
    }
 
    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateProducer_shouldReturn404_whenProducerNotFound() throws Exception {
        UUID producerId = UUID.randomUUID();
        when(producerService.deactivateProducer(producerId))
                .thenThrow(new NotFoundException("Produtor não encontrado"));
 
        mockMvc
                .perform(patch("/admin/producers/{id}/deactivate", producerId).with(csrf()))
                .andExpect(status().isNotFound());
    }
 
    @Test
    void deactivateProducer_shouldReturn403_whenUserIsNotAdmin() throws Exception {
        UUID producerId = UUID.randomUUID();
        User activeFarmer = new User();
        activeFarmer.setType(TypeUser.FARMER);
        activeFarmer.setActive(true);
        when(userRepository.findByAuthSub("some-sub")).thenReturn(java.util.Optional.of(activeFarmer));
 
        mockMvc
                .perform(
                        patch("/admin/producers/{id}/deactivate", producerId)
                                .with(
                                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .jwt()
                                                .jwt(
                                                        jwt -> jwt.claim("sub", "some-sub").claim("email",
                                                                "farmer@test.com"))
                                                .authorities(
                                                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                "ROLE_FARMER"))))
                .andExpect(status().isForbidden());
    }
}