package br.com.ragro.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateReviewRequest(
    @NotNull
    UUID orderId,
    @NotNull
    @Min(1)
    @Max(5)
    Integer rating,

    String comment) {}
