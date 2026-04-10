package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvailabilityRequest {

    @NotNull(message = "Weekday is required")
    @Schema(description = "0=Sunday, 1=Monday ... 6=Saturday", example = "1")
    private Short weekday;

    @NotNull(message = "Opens at is required")
    @Schema(example = "08:00")
    private String opensAt;

    @NotNull(message = "Closes at is required")
    @Schema(example = "18:00")
    private String closesAt;
}