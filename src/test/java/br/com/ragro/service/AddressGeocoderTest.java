package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.domain.Address;
import br.com.ragro.domain.enums.GeocodeStatus;
import br.com.ragro.service.GoogleMapsService.GeocodeOutcome;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressGeocoderTest {

  @Mock private GoogleMapsService googleMapsService;

  @InjectMocks private AddressGeocoder addressGeocoder;

  private Address address;

  @BeforeEach
  void setUp() {
    address = new Address();
    address.setStreet("Rua das Flores");
    address.setNumber("100");
    address.setNeighborhood("Centro");
    address.setCity("Porto Alegre");
    address.setState("RS");
    address.setZipCode("90000000");
  }

  @Test
  void geocodeAndApply_shouldSetCoordinatesAndStatus_whenGeocodeSucceeds() {
    when(googleMapsService.geocode(any()))
        .thenReturn(
            new GeocodeOutcome(
                BigDecimal.valueOf(-30.03), BigDecimal.valueOf(-51.21), GeocodeStatus.OK));

    addressGeocoder.geocodeAndApply(address);

    assertThat(address.getLatitude()).isEqualByComparingTo("-30.03");
    assertThat(address.getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
    assertThat(address.getGeocodedAt()).isNotNull();
  }

  @Test
  void geocodeAndApply_shouldApplyApproximateCoordinates_whenAmbiguous() {
    // AMBIGUOUS still yields usable lat/lng: apply it, record status as a quality warning.
    when(googleMapsService.geocode(any()))
        .thenReturn(
            GeocodeOutcome.ambiguous(
                BigDecimal.valueOf(-30.03), BigDecimal.valueOf(-51.23)));

    addressGeocoder.geocodeAndApply(address);

    assertThat(address.getLatitude()).isEqualByComparingTo("-30.03");
    assertThat(address.getLongitude()).isEqualByComparingTo("-51.23");
    assertThat(address.getGeocodeStatus()).isEqualTo(GeocodeStatus.AMBIGUOUS);
  }

  @Test
  void geocodeAndApply_shouldNotApplyCoordinates_whenFailed() {
    when(googleMapsService.geocode(any())).thenReturn(GeocodeOutcome.failed());

    addressGeocoder.geocodeAndApply(address);

    assertThat(address.getLatitude()).isNull();
    assertThat(address.getGeocodeStatus()).isEqualTo(GeocodeStatus.FAILED);
  }

  @Test
  void ensureGeocoded_shouldNotRetry_whenPreviousAttemptFailed() {
    address.setGeocodeStatus(GeocodeStatus.FAILED);

    boolean result = addressGeocoder.ensureGeocoded(address);

    assertThat(result).isFalse();
    verify(googleMapsService, never()).geocode(any());
  }

  @Test
  void ensureGeocoded_shouldSkipCall_whenCoordinatesAlreadyPresent() {
    address.setLatitude(BigDecimal.ONE);
    address.setLongitude(BigDecimal.ONE);

    assertThat(addressGeocoder.ensureGeocoded(address)).isTrue();
    verify(googleMapsService, never()).geocode(any());
  }

  @Test
  void ensureGeocoded_shouldGeocode_whenNeverAttempted() {
    when(googleMapsService.geocode(any()))
        .thenReturn(
            new GeocodeOutcome(BigDecimal.ONE, BigDecimal.ONE, GeocodeStatus.OK));

    assertThat(addressGeocoder.ensureGeocoded(address)).isTrue();
    assertThat(address.getGeocodeStatus()).isEqualTo(GeocodeStatus.OK);
  }

  @Test
  void resetGeocode_shouldClearCoordinatesAndBookkeeping() {
    address.setLatitude(BigDecimal.ONE);
    address.setGeocodeStatus(GeocodeStatus.FAILED);

    addressGeocoder.resetGeocode(address);

    assertThat(address.getLatitude()).isNull();
    assertThat(address.getGeocodeStatus()).isNull();
    assertThat(address.getGeocodedAt()).isNull();
  }
}
