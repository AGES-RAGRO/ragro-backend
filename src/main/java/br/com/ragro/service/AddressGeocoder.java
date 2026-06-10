package br.com.ragro.service;

import br.com.ragro.domain.Address;
import br.com.ragro.service.GoogleMapsService.GeocodeOutcome;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ponto único de geocodificação de {@link Address} (antes o bloco "monta endereço → geocode → set
 * lat/lng" estava triplicado nos cadastros e no self-heal do mapa). Persiste o resultado E o
 * status: FAILED/AMBIGUOUS não são re-tentados automaticamente.
 */
@Component
@RequiredArgsConstructor
public class AddressGeocoder {

  private final GoogleMapsService googleMapsService;

  /**
   * Geocodifica e aplica coordenadas+status no endereço (não salva — o chamador persiste na sua
   * transação). Retorna o resultado para quem precisa reagir (ex.: avisar usuário no cadastro).
   */
  public GeocodeOutcome geocodeAndApply(Address address) {
    String fullAddress = buildFullAddress(address);
    if (fullAddress.isBlank()) {
      return GeocodeOutcome.failed();
    }
    GeocodeOutcome outcome = googleMapsService.geocode(fullAddress);
    address.setGeocodeStatus(outcome.status());
    address.setGeocodedAt(OffsetDateTime.now());
    if (outcome.isOk()) {
      address.setLatitude(outcome.latitude());
      address.setLongitude(outcome.longitude());
    }
    return outcome;
  }

  /**
   * Self-heal de leitura: só tenta quando nunca houve tentativa ({@code geocodeStatus == null}) e
   * não há coordenadas — endereços FAILED/AMBIGUOUS ficam quietos até serem editados (o retry
   * infinito a cada GET /producers/locations era pago e inútil).
   */
  public boolean ensureGeocoded(Address address) {
    if (address.getLatitude() != null && address.getLongitude() != null) {
      return true;
    }
    if (address.getGeocodeStatus() != null) {
      return false;
    }
    return geocodeAndApply(address).isOk();
  }

  /** Edição de endereço: zera o bookkeeping para a próxima geocodificação re-tentar. */
  public void resetGeocode(Address address) {
    address.setLatitude(null);
    address.setLongitude(null);
    address.setGeocodeStatus(null);
    address.setGeocodedAt(null);
  }

  private String buildFullAddress(Address address) {
    if (address.getStreet() == null || address.getCity() == null) {
      return "";
    }
    return String.format(
        "%s, %s - %s, %s - %s, %s",
        address.getStreet(),
        address.getNumber(),
        address.getNeighborhood(),
        address.getCity(),
        address.getState(),
        address.getZipCode());
  }
}
