package br.com.ragro.domain.enums;

/**
 * Resultado da última tentativa de geocodificação de um endereço. {@code null} na coluna significa
 * "nunca tentado" (re-tenta no próximo uso); FAILED/AMBIGUOUS não são re-tentados automaticamente
 * — o status é zerado quando o endereço é editado.
 */
public enum GeocodeStatus {
  OK,
  AMBIGUOUS,
  FAILED
}
