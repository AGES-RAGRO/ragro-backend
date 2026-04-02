package br.com.ragro.controller.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class ErrorResponse {

  private LocalDateTime timestamp;
  private int status;
  private String error;
  private String path;
}
