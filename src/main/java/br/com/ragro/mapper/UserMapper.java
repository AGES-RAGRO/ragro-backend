package br.com.ragro.mapper;

import br.com.ragro.controller.request.UserRequest;
import br.com.ragro.controller.response.UserResponse;
import br.com.ragro.domain.User;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserMapper {

  public static User toEntity(UserRequest request) {
    User entity = new User();
    entity.setName(request.getName());
    entity.setEmail(request.getEmail());
    entity.setPhone(request.getPhone());
    entity.setType(request.getType());
    entity.setActive(true);
    return entity;
  }

  public static UserResponse toResponse(User entity) {
    return UserResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .email(entity.getEmail())
        .phone(entity.getPhone())
        .type(entity.getType() != null ? entity.getType().name().toLowerCase(Locale.ROOT) : null)
        .active(entity.isActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
