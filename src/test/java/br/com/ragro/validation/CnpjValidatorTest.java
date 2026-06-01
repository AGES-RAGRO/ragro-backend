package br.com.ragro.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CnpjValidatorTest {

  @ParameterizedTest(name = "valid CNPJ: {0}")
  @ValueSource(strings = {"11222333000181", "45997418000153", "30457876285453", "31298737336741"})
  void isValidCnpj_shouldReturnTrue_forKnownValidCnpjs(String cnpj) {
    assertThat(CnpjValidator.isValidCnpj(cnpj)).isTrue();
  }

  @ParameterizedTest(name = "invalid CNPJ: {0}")
  @ValueSource(
      strings = {
        "12345678901234",
        "00000000000000",
        "11111111111111",
        "1234567890123",
        "123456789012345"
      })
  void isValidCnpj_shouldReturnFalse_forInvalidCnpjs(String cnpj) {
    assertThat(CnpjValidator.isValidCnpj(cnpj)).isFalse();
  }
}
