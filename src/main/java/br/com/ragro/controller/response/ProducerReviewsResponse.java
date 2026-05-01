package br.com.ragro.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
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
@Schema(description = "Paginated reviews with aggregated stats")
public class ProducerReviewsResponse {

    @Schema(description = "Average rating of the producer", example = "4.80")
    private BigDecimal averageRating;

    @Schema(description = "Total number of reviews", example = "42")
    private Integer totalReviews;

    @Schema(description = "List of reviews for current page")
    private List<ReviewItemResponse> reviews;

    @Schema(description = "Current page number", example = "0")
    private Integer pageNumber;

    @Schema(description = "Reviews per page", example = "10")
    private Integer pageSize;

    @Schema(description = "Total number of pages", example = "5")
    private Integer totalPages;

    @Schema(description = "Total number of reviews (from DB)", example = "42")
    private Long totalElements;
}