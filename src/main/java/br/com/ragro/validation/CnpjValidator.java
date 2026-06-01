package br.com.ragro.validation;

/** Utility for CNPJ check-digit validation. Reused by {@link FiscalNumberValidator}. */
public final class CnpjValidator {

  private CnpjValidator() {}

  static boolean isValidCnpj(String digits) {
    if (digits.length() != 14) {
      return false;
    }
    if (digits.chars().distinct().count() == 1) {
      return false;
    }
    return checkDigit(digits, 12) && checkDigit(digits, 13);
  }

  private static boolean checkDigit(String digits, int position) {
    int[] weights =
        (position == 12)
            ? new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
            : new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    int sum = 0;
    for (int i = 0; i < weights.length; i++) {
      sum += Character.getNumericValue(digits.charAt(i)) * weights[i];
    }
    int remainder = sum % 11;
    int expected = remainder < 2 ? 0 : 11 - remainder;
    return expected == Character.getNumericValue(digits.charAt(position));
  }
}
