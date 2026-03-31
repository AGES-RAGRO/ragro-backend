package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockCognitoServiceTest {

    private MockCognitoService cognitoService;

    @BeforeEach
    void setUp() {
        cognitoService = new MockCognitoService();
    }

    @Test
    void registerUser_shouldReturnNonNullSub() {
        String sub = cognitoService.registerUser("Maria Silva", "maria@example.com");

        assertThat(sub).isNotNull().isNotBlank();
    }

    @Test
    void registerUser_shouldReturnValidUuidFormat() {
        String sub = cognitoService.registerUser("João Costa", "joao@example.com");

        assertThatNoException().isThrownBy(() -> java.util.UUID.fromString(sub));
    }

    @Test
    void registerUser_shouldReturnDistinctSubsForDifferentCalls() {
        String sub1 = cognitoService.registerUser("Maria Silva", "maria@example.com");
        String sub2 = cognitoService.registerUser("João Costa", "joao@example.com");

        assertThat(sub1).isNotEqualTo(sub2);
    }

    @Test
    void addToConsumerGroup_shouldNotThrow() {
        assertThatNoException()
                .isThrownBy(() -> cognitoService.addToConsumerGroup("some-cognito-sub"));
    }
}
