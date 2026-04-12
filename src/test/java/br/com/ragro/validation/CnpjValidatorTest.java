package br.com.ragro.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CnpjValidatorTest {

  @ParameterizedTest(name = "valid CNPJ: {0}")
  @ValueSource(strings = {
      "11222333000181",
      "45997418000153",
      "30457876285453",
      "31298737336741"
  })
  void isValidCnpj_shouldReturnTrue_forKnownValidCnpjs(String cnpj) {
    assertThat(CnpjValidator.isValidCnpj(cnpj)).isTrue();
  }

  @ParameterizedTest(name = "invalid CNPJ: {0}")
  @ValueSource(strings = {
      "12345678901234",
      "00000000000000",
      "11111111111111",
      "1234567890123",
      "123456789012345"
  })
  void isValidCnpj_shouldReturnFalse_forInvalidCnpjs(String cnpj) {
    assertThat(CnpjValidator.isValidCnpj(cnpj)).isFalse();
  }

  @Test
  void isValid_shouldReturnTrue_forNull() {
    CnpjValidator validator = new CnpjValidator();
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  void isValid_shouldReturnTrue_forBlank() {
    CnpjValidator validator = new CnpjValidator();
    assertThat(validator.isValid("   ", null)).isTrue();
  }

  @Test
  void isValid_shouldStripFormatting_beforeValidating() {
    CnpjValidator validator = new CnpjValidator();
    assertThat(validator.isValid("11.222.333/0001-81", null)).isTrue();
  }
}
