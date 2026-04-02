package br.com.ragro.controller.response;

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
public class UserResponse {

  private UUID id;
  private String name;
  private String email;
  private String phone;
  private String type;
  private boolean active;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
