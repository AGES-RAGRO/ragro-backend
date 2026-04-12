package br.com.ragro.validation;

import br.com.ragro.controller.request.ProducerRegistrationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FiscalNumberValidator
    implements ConstraintValidator<ValidFiscalNumber, ProducerRegistrationRequest> {

  @Override
  public boolean isValid(ProducerRegistrationRequest request, ConstraintValidatorContext context) {
    if (request == null) {
      return true;
    }
    String fiscalNumber = request.getFiscalNumber();
    String fiscalNumberType = request.getFiscalNumberType();

    if (fiscalNumber == null || fiscalNumberType == null) {
      return true;
    }

    String digits = fiscalNumber.replaceAll("\\D", "");

    boolean valid = switch (fiscalNumberType.toUpperCase()) {
      case "CPF" -> CpfValidator.isValidCpf(digits);
      case "CNPJ" -> CnpjValidator.isValidCnpj(digits);
      default -> true;
    };

    if (!valid) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate(
          "fiscalNumber: Invalid " + fiscalNumberType.toUpperCase())
          .addPropertyNode("fiscalNumber")
          .addConstraintViolation();
    }
    return valid;
  }
}
