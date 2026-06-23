package br.com.ragro.service;

import br.com.ragro.domain.Address;
import br.com.ragro.service.GoogleMapsService.GeocodeOutcome;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single geocoding entry point for {@link Address}. Persists both result and status: FAILED/AMBIGUOUS
 * are not retried automatically.
 */
@Component
@RequiredArgsConstructor
public class AddressGeocoder {

  private final GoogleMapsService googleMapsService;

  /**
   * Applies coordinates + status to the address (caller persists within its own transaction).
   * Returns the result so callers can react (e.g. warn the user during registration).
   */
  public GeocodeOutcome geocodeAndApply(Address address) {
    String fullAddress = buildFullAddress(address);
    if (fullAddress.isBlank()) {
      return GeocodeOutcome.failed();
    }
    GeocodeOutcome outcome = googleMapsService.geocode(fullAddress);
    address.setGeocodeStatus(outcome.status());
    address.setGeocodedAt(OffsetDateTime.now());
    // Apply AMBIGUOUS (approximate) coordinates too: imprecise lat/lng beats vanishing from the map;
    // the AMBIGUOUS status stays recorded as a warning.
    if (outcome.hasCoordinates()) {
      address.setLatitude(outcome.latitude());
      address.setLongitude(outcome.longitude());
    }
    return outcome;
  }

  /**
   * Read-time self-heal: only attempts when there was no prior attempt ({@code geocodeStatus == null})
   * and no coordinates. FAILED/AMBIGUOUS stay quiet until edited (avoids paid infinite retry per GET).
   */
  public boolean ensureGeocoded(Address address) {
    if (address.getLatitude() != null && address.getLongitude() != null) {
      return true;
    }
    if (address.getGeocodeStatus() != null) {
      return false;
    }
    return geocodeAndApply(address).hasCoordinates();
  }

  /** Address edit: clears bookkeeping so the next geocoding retries. */
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
