package br.com.ragro.controller.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record CreateReviewRequest(
    @NotNull
    @Min(1)
    @Max(5)
    Integer rating,

    String comment) {
  
    
}
