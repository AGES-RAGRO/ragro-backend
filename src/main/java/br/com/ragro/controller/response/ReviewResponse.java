package br.com.ragro.controller.response;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    Integer rating,
    String comment,
    UUID orderId,
    UUID farmerId,
    UUID customerId,
    OffsetDateTime createdAt
){}
