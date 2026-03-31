package br.com.ragro.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "Street is required")
    private String street;

    @NotBlank(message = "Number is required")
    private String number;

    private String complement;

    private String neighborhood;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "State must contain 2 letters")
    private String state;

    @NotBlank(message = "Zip code is required")
    @Pattern(regexp = "^\\d{8}$", message = "Zip code must contain 8 digits")
    private String zipCode;

    private BigDecimal latitude;

    private BigDecimal longitude;
}
