package br.com.ragro.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that fiscalNumber is arithmetically correct based on fiscalNumberType (CPF or CNPJ).
 * Applied at class level to access both fields.
 */
@Documented
@Constraint(validatedBy = FiscalNumberValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFiscalNumber {

  String message() default "Invalid fiscal number for the given type";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
