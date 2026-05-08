package br.com.ragro.domain.enums;

import lombok.Getter;

@Getter
public enum FuelType {
  GASOLINE("Gasolina", 2.31),
  ETHANOL("Etanol", 1.50),
  DIESEL("Diesel", 2.68),
  ELECTRIC("Elétrico", 0.0);

  private final String description;
  private final double emissionFactor;

  FuelType(String description, double emissionFactor) {
    this.description = description;
    this.emissionFactor = emissionFactor;
  }
}
