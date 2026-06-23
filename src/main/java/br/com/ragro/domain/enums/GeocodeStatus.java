package br.com.ragro.domain.enums;

/**
 * Last geocoding result for an address. {@code null} = never attempted (retried on next use);
 * FAILED/AMBIGUOUS are not auto-retried — cleared when the address is edited.
 */
public enum GeocodeStatus {
  OK,
  AMBIGUOUS,
  FAILED
}
