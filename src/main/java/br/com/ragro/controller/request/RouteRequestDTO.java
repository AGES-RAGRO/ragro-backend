package br.com.ragro.controller.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RouteRequestDTO {

  @NotBlank(message = "A origem é obrigatória")
  private String origin;

  @NotBlank(message = "O destino é obrigatório")
  private String destination;

  private List<String> waypoints;
}
