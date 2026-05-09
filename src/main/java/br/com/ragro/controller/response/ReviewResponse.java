package br.com.ragro.controller.response;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    String authorName,
    String authorAvatarUrl,
    Integer rating,
    String comment,
    UUID orderId,
    UUID farmerId,
    UUID customerId,
    OffsetDateTime createdAt
){}
