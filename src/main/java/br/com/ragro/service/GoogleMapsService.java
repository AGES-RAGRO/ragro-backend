package br.com.ragro.service;

import br.com.ragro.domain.enums.GeocodeStatus;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.OverDailyLimitException;
import com.google.maps.errors.OverQueryLimitException;
import com.google.maps.model.ComponentFilter;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import com.google.maps.model.LocationType;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Address geocoding (Geocoding API, official Java client). Route calculation lives in the new Routes
 * API ({@link GoogleRoutesService}).
 */
@Service
@Slf4j
public class GoogleMapsService {

  /**
   * Validated result of a geocoding call. {@code FAILED} never carries coordinates; {@code AMBIGUOUS}
   * (partial_match/approximate) still carries usable lat/lng — enough to route, the status is just a
   * quality warning (e.g. registration asks for confirmation).
   */
  public record GeocodeOutcome(BigDecimal latitude, BigDecimal longitude, GeocodeStatus status) {
    public static GeocodeOutcome failed() {
      return new GeocodeOutcome(null, null, GeocodeStatus.FAILED);
    }

    public static GeocodeOutcome ambiguous(BigDecimal latitude, BigDecimal longitude) {
      return new GeocodeOutcome(latitude, longitude, GeocodeStatus.AMBIGUOUS);
    }

    public boolean isOk() {
      return status == GeocodeStatus.OK;
    }

    /** Whether there is a usable coordinate (OK or approximate AMBIGUOUS). */
    public boolean hasCoordinates() {
      return latitude != null && longitude != null;
    }
  }

  private final GeoApiContext context;
  private final MeterRegistry meterRegistry;

  public GoogleMapsService(
      @Value("${google.maps.api-key}") String apiKey, MeterRegistry meterRegistry) {
    this.context = new GeoApiContext.Builder().apiKey(apiKey).build();
    this.meterRegistry = meterRegistry;
  }

  /**
   * Geocodes a Brazilian address with quality validation: restricts to BR, flags {@code partial_match}
   * and APPROXIMATE precision as AMBIGUOUS. Never throws: failures become FAILED and the caller decides
   * (registration warns the user; the map filters out the producer).
   */
  public GeocodeOutcome geocode(String address) {
    meterRegistry.counter("ragro.google.calls", "api", "geocoding").increment();
    try {
      // components(country=BR) is a HARD filter (region("br") was only a bias and could return
      // coords outside Brazil for ambiguous addresses).
      GeocodingResult[] results =
          GeocodingApi.geocode(context, address).components(ComponentFilter.country("BR")).await();
      if (results == null || results.length == 0) {
        return GeocodeOutcome.failed();
      }
      GeocodingResult best = results[0];
      if (best.geometry == null || best.geometry.location == null) {
        return GeocodeOutcome.failed();
      }
      LatLng location = best.geometry.location;
      BigDecimal lat = BigDecimal.valueOf(location.lat);
      BigDecimal lng = BigDecimal.valueOf(location.lng);
      // partial_match or approximate precision (e.g. street without exact number): coordinate is
      // usable for routing; mark AMBIGUOUS as a quality warning WITHOUT discarding the lat/lng.
      if (best.partialMatch || best.geometry.locationType == LocationType.APPROXIMATE) {
        return GeocodeOutcome.ambiguous(lat, lng);
      }
      return new GeocodeOutcome(lat, lng, GeocodeStatus.OK);
    } catch (OverQueryLimitException | OverDailyLimitException e) {
      log.warn("Geocoding quota exceeded: {}", e.getMessage());
      return GeocodeOutcome.failed();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return GeocodeOutcome.failed();
    } catch (Exception e) {
      // Don't log the address (street/number/zip = PII); the stacktrace suffices.
      log.error("Failed to geocode address", e);
      return GeocodeOutcome.failed();
    }
  }

  /** Without shutdown, GeoApiContext leaks orphan threads on each redeploy. */
  @PreDestroy
  public void shutdown() {
    context.shutdown();
  }
}
