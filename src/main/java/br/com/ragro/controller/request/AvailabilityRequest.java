package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityRequest {

    @NotNull(message = "Weekday is required")
    @Min(value = 0, message = "Weekday must be between 0 and 6")
    @Max(value = 6, message = "Weekday must be between 0 and 6")
    @Schema(description = "0=Sunday, 1=Monday ... 6=Saturday", example = "1")
    private Short weekday;

    @NotNull(message = "Opens at is required")
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Opens at must use HH:mm format")
    @Schema(example = "08:00")
    private String opensAt;

    @NotNull(message = "Closes at is required")
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Closes at must use HH:mm format")
    @Schema(example = "18:00")
    private String closesAt;
}