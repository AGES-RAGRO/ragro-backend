package br.com.ragro.controller.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class SessionResponse {

  private UUID id;
  private String name;
  private String email;
  private String type;
  private boolean active;
}
