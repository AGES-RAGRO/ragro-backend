package br.com.ragro.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<ValidCpf, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return true;
    }
    String digits = value.replaceAll("\\D", "");
    return isValidCpf(digits);
  }

  static boolean isValidCpf(String digits) {
    if (digits.length() != 11) {
      return false;
    }
    if (digits.chars().distinct().count() == 1) {
      return false;
    }
    return checkDigit(digits, 9) && checkDigit(digits, 10);
  }

  private static boolean checkDigit(String digits, int position) {
    int sum = 0;
    int weight = position + 1;
    for (int i = 0; i < position; i++) {
      sum += Character.getNumericValue(digits.charAt(i)) * weight--;
    }
    int remainder = (sum * 10) % 11;
    int expected = remainder == 10 ? 0 : remainder;
    return expected == Character.getNumericValue(digits.charAt(position));
  }
}
