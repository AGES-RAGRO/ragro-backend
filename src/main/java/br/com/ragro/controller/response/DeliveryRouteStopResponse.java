package br.com.ragro.controller.response;

public record DeliveryRouteStopResponse(
    int stopOrder,
    String orderId,
    String formattedAddress,
    double latitude,
    double longitude) {}
