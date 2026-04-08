package br.com.ragro.mapper;

import br.com.ragro.controller.response.ProducerGetResponse;
import br.com.ragro.domain.Producer;
import br.com.ragro.domain.User;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProducerMapper {

    public static ProducerGetResponse toResponse(User user, Producer producer) {
        return ProducerGetResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fiscalNumber(producer.getFiscalNumber())
                .fiscalNumberType(producer.getFiscalNumberType())
                .farmName(producer.getFarmName())
                .description(producer.getDescription())
                .avatarS3(producer.getAvatarS3())
                .displayPhotoS3(producer.getDisplayPhotoS3())
                .totalReviews(producer.getTotalReviews())
                .averageRating(producer.getAverageRating())
                .totalOrders(producer.getTotalOrders())
                .totalSalesAmount(producer.getTotalSalesAmount())
                .build();
    }
}
