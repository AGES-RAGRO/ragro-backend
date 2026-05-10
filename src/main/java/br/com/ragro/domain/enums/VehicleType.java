package br.com.ragro.domain.enums;

import lombok.Getter;

@Getter
public enum VehicleType {
  MOTORCYCLE("Moto"),
  CAR("Carro"),
  VAN("Van"),
  LIGHT_TRUCK("Caminhão leve");

  private final String description;

  VehicleType(String description) {
    this.description = description;
  }
}
