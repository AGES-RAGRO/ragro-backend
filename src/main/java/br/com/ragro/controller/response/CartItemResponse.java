package br.com.ragro.controller.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private BigDecimal priceSnapshot;
    private BigDecimal quantity;
    private BigDecimal subtotal;
    private String imageS3;
}