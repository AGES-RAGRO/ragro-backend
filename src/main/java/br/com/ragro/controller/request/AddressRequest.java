package br.com.ragro.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "Street is required")
    @Size(max = 200)
    private String street;

    @NotBlank(message = "Number is required")
    @Size(max = 10)
    private String number;

    @Size(max = 100)
    private String complement;

    @Size(max = 100)
    private String neighborhood;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "State is required")
    @Size(min = 2, max = 2, message = "State must have exactly 2 characters")
    private String state;

    @NotBlank(message = "Zip code is required")
    @Pattern(regexp = "\\d{8}", message = "Zip code must have exactly 8 digits")
    private String zipCode;

    private BigDecimal latitude;
    private BigDecimal longitude;
}
