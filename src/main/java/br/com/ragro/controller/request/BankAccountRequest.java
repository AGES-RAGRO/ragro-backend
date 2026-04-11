package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankAccountRequest {

    @NotBlank(message = "Bank name is required")
    @Schema(example = "Banco do Brasil")
    private String bankName;

    @Schema(example = "001")
    private String bankCode;

    @NotBlank(message = "Agency is required")
    @Schema(example = "0001")
    private String agency;

    @NotBlank(message = "Account number is required")
    @Schema(example = "12345-6")
    private String accountNumber;

    @NotBlank(message = "Holder name is required")
    @Schema(example = "João Silva")
    private String holderName;

    @NotBlank(message = "Fiscal number is required")
    @Schema(example = "12345678901")
    private String fiscalNumber;
}