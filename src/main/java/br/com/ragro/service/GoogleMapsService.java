package br.com.ragro.service;

import br.com.ragro.domain.enums.GeocodeStatus;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.OverDailyLimitException;
import com.google.maps.errors.OverQueryLimitException;
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
 * Geocodificação de endereços (Geocoding API, client Java oficial). O cálculo de rota migrou para
 * a Routes API nova em {@link GoogleRoutesService}; o {@code optimizeRoute} legado (Directions
 * API, rota efêmera) foi removido junto com {@code POST /routes/optimize}.
 */
@Service
@Slf4j
public class GoogleMapsService {

  /** Resultado validado de uma geocodificação; lat/lng presentes apenas quando status = OK. */
  public record GeocodeOutcome(BigDecimal latitude, BigDecimal longitude, GeocodeStatus status) {
    public static GeocodeOutcome failed() {
      return new GeocodeOutcome(null, null, GeocodeStatus.FAILED);
    }

    public static GeocodeOutcome ambiguous() {
      return new GeocodeOutcome(null, null, GeocodeStatus.AMBIGUOUS);
    }

    public boolean isOk() {
      return status == GeocodeStatus.OK;
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
   * Geocodifica um endereço brasileiro com validação de qualidade: restringe à região BR, rejeita
   * {@code partial_match} e precisão APPROXIMATE como AMBIGUOUS (antes, "Rua das Flores" podia
   * geocodar em outra cidade e o pin aparecia errado silenciosamente). Nunca lança: falhas viram
   * FAILED e o chamador decide (cadastro avisa o usuário; mapa filtra o produtor).
   */
  public GeocodeOutcome geocode(String address) {
    meterRegistry.counter("ragro.google.calls", "api", "geocoding").increment();
    try {
      GeocodingResult[] results = GeocodingApi.geocode(context, address).region("br").await();
      if (results == null || results.length == 0) {
        return GeocodeOutcome.failed();
      }
      GeocodingResult best = results[0];
      if (best.partialMatch || best.geometry == null || best.geometry.location == null) {
        return GeocodeOutcome.ambiguous();
      }
      if (best.geometry.locationType == LocationType.APPROXIMATE) {
        return GeocodeOutcome.ambiguous();
      }
      LatLng location = best.geometry.location;
      return new GeocodeOutcome(
          BigDecimal.valueOf(location.lat), BigDecimal.valueOf(location.lng), GeocodeStatus.OK);
    } catch (OverQueryLimitException | OverDailyLimitException e) {
      log.warn("Geocoding quota exceeded: {}", e.getMessage());
      return GeocodeOutcome.failed();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return GeocodeOutcome.failed();
    } catch (Exception e) {
      log.error("Failed to geocode address: {}", address, e);
      return GeocodeOutcome.failed();
    }
  }

  /** Sem o shutdown, o GeoApiContext deixa threads órfãs a cada redeploy. */
  @PreDestroy
  public void shutdown() {
    context.shutdown();
  }
}
