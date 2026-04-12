package br.com.ragro.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfValidatorTest {

  @ParameterizedTest(name = "valid CPF: {0}")
  @ValueSource(strings = {
      "52998224725",
      "11144477735",
      "12345678909",
      "98765432100"
  })
  void isValidCpf_shouldReturnTrue_forKnownValidCpfs(String cpf) {
    assertThat(CpfValidator.isValidCpf(cpf)).isTrue();
  }

  @ParameterizedTest(name = "invalid CPF: {0}")
  @ValueSource(strings = {
      "12345678901",
      "00000000000",
      "11111111111",
      "1234567890",
      "123456789012"
  })
  void isValidCpf_shouldReturnFalse_forInvalidCpfs(String cpf) {
    assertThat(CpfValidator.isValidCpf(cpf)).isFalse();
  }

  @Test
  void isValid_shouldReturnTrue_forNull() {
    CpfValidator validator = new CpfValidator();
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  void isValid_shouldReturnTrue_forBlank() {
    CpfValidator validator = new CpfValidator();
    assertThat(validator.isValid("   ", null)).isTrue();
  }

  @Test
  void isValid_shouldStripFormatting_beforeValidating() {
    CpfValidator validator = new CpfValidator();
    assertThat(validator.isValid("529.982.247-25", null)).isTrue();
  }
}
