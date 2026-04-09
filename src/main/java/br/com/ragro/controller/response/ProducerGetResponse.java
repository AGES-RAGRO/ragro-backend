package br.com.ragro.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProducerGetResponse {

    private UUID id;

    // dados do User
    private String name;
    private String email;
    private String phone;

    // dados do Producer (farmers)
    private String fiscalNumber;
    private String fiscalNumberType;
    private String farmName;
    private String description;
    private String avatarS3;
    private String displayPhotoS3;
    private Integer totalReviews;
    private BigDecimal averageRating;
    private Integer totalOrders;
    private BigDecimal totalSalesAmount;
}
