package br.com.ragro.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Request payload to create a review for a delivered order")
public record CreateReviewRequest(
    @NotNull
        @Schema(description = "Delivered order identifier", example = "33333333-3333-3333-3333-333333333301")
        UUID orderId,
    @NotNull @Min(1) @Max(5) @Schema(description = "Review rating from 1 to 5", example = "5")
        Integer rating,
    @Schema(description = "Optional review comment", example = "Great quality and quick delivery.")
        String comment) {}
