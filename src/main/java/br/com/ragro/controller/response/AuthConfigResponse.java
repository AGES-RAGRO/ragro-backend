package br.com.ragro.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class AuthConfigResponse {

  private String tokenUrl;
  private String clientId;
  private String realm;
}
