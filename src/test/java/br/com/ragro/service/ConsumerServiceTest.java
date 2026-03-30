package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.ragro.controller.response.ConsumerResponse;
import br.com.ragro.domain.User;
import br.com.ragro.domain.enums.TypeUser;
import br.com.ragro.exception.UnauthorizedException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ConsumerServiceTest {

    @Mock private UserService userService;

    @Mock private Jwt jwt;

    @InjectMocks private ConsumerService consumerService;

    @Test
    void getMyConsumer_shouldReturnConsumerResponse_whenUserIsCustomer() {
        User user = buildUser(TypeUser.CUSTOMER);
        when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

        ConsumerResponse response = consumerService.getMyConsumer(jwt);

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getName()).isEqualTo(user.getName());
        assertThat(response.getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getAddresses()).isEmpty();
    }

    @Test
    void getMyConsumer_shouldThrowUnauthorizedException_whenUserIsNotCustomer() {
        User user = buildUser(TypeUser.FARMER);
        when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

        assertThatThrownBy(() -> consumerService.getMyConsumer(jwt))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Acesso restrito a consumidores");
    }

    @Test
    void getMyConsumer_shouldThrowUnauthorizedException_whenUserIsAdmin() {
        User user = buildUser(TypeUser.ADMIN);
        when(userService.getAuthenticatedUser(jwt)).thenReturn(user);

        assertThatThrownBy(() -> consumerService.getMyConsumer(jwt))
                .isInstanceOf(UnauthorizedException.class);
    }

    private User buildUser(TypeUser type) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Maria Silva");
        user.setEmail("maria@example.com");
        user.setPhone("51999999999");
        user.setType(type);
        user.setActive(true);
        user.setCognitoSub("cognito-sub-123");
        user.setCreatedAt(OffsetDateTime.now().minusDays(1));
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }
}
