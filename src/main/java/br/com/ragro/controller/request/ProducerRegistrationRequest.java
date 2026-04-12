package br.com.ragro.controller.request;

import br.com.ragro.validation.ValidFiscalNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ValidFiscalNumber
@Schema(description = "Request payload for producer registration")
public class ProducerRegistrationRequest {

    @NotBlank(message = "Name is required")
    @Schema(description = "Full name of the producer", example = "João Silva", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Phone is required")
    @Size(max = 20, message = "Phone must contain at most 20 characters")
    @Schema(description = "Phone number", example = "(51) 98765-4321", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Schema(description = "Email address", example = "joao@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 50, message = "Password must contain between 8 and 50 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
    @Schema(description = "Password", example = "Senha@123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "Fiscal number is required")
    @Pattern(regexp = "\\d{11}|\\d{14}", message = "Fiscal number must contain 11 or 14 digits")
    @Schema(description = "CPF or CNPJ (digits only)", example = "12345678901", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fiscalNumber;

    @NotBlank(message = "Fiscal number type is required")
    @Pattern(regexp = "CPF|CNPJ", message = "Fiscal number type must be CPF or CNPJ")
    @Schema(description = "Type of fiscal number", example = "CPF", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fiscalNumberType;

    @NotBlank(message = "Farm name is required")
    @Size(max = 150, message = "Farm name must contain at most 150 characters")
    @Schema(description = "Name of the farm", example = "Fazenda São João", requiredMode = Schema.RequiredMode.REQUIRED)
    private String farmName;

    @Valid
    @NotNull(message = "Address is required")
    @Schema(description = "Address of the producer", requiredMode = Schema.RequiredMode.REQUIRED)
    private AddressRequest address;

    @Valid
    @Schema(description = "Bank account details")
    private BankAccountRequest bankAccount;

    @Valid
    @Schema(description = "Payment method data (pix or bank_account). Prefer this over bankAccount.")
    private PaymentMethodRequest paymentMethod;

    @Valid
    @Schema(description = "Availability schedule")
    private List<AvailabilityRequest> availability;

    @Schema(description = "Description of the farm")
    private String description;

    @Schema(description = "Avatar S3 URL")
    private String avatarS3;

    @Schema(description = "Display photo S3 URL")
    private String displayPhotoS3;
}
