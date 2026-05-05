package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Individual review item")
public class ReviewItemResponse {

    @Schema(description = "Review ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Customer name", example = "Maria Silva")
    private String customerName;

    @Schema(description = "Rating score", example = "5")
    private Short rating;

    @Schema(description = "Review comment")
    private String comment;

    @Schema(description = "Review creation date", example = "2026-04-25T10:30:00Z")
    private OffsetDateTime createdAt;
}