package br.com.ragro.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductCategory {
  FRUTAS("Frutas"),
  VERDURAS("Verduras"),
  LEGUMES("Legumes"),
  LATICINIOS("Laticínios"),
  OVOS("Ovos"),
  GRAOS_E_CEREAIS("Grãos e Cereais"),
  CARNES("Carnes"),
  MEL_E_DERIVADOS("Mel e Derivados"),
  PROCESSADOS_ARTESANAIS("Processados Artesanais"),
  PLANTAS_E_MUDAS("Plantas e Mudas");

  private final String label;
}
