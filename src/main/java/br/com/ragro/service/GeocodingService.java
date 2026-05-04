package br.com.ragro.service;

import br.com.ragro.domain.Address;
import br.com.ragro.domain.Coordinates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeocodingService {

  private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
  private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
  private static final String USER_AGENT = "ragro-app/1.0";

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  public GeocodingService(ObjectMapper objectMapper) {
    this.restTemplate = new RestTemplate();
    this.objectMapper = objectMapper;
  }

  public Optional<Coordinates> geocode(Address address) {
    try {
      String street = encode(address.getNumber() + " " + address.getStreet());
      String city = encode(address.getCity());
      String state = encode(address.getState());
      String url = NOMINATIM_URL
          + "?street=" + street
          + "&city=" + city
          + "&state=" + state
          + "&country=Brazil&format=json&limit=1";

      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", USER_AGENT);
      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(URI.create(url), HttpMethod.GET, entity, String.class);

      if (response.getBody() == null || response.getBody().isBlank()) {
        return Optional.empty();
      }

      JsonNode nodes = objectMapper.readTree(response.getBody());
      if (!nodes.isArray() || nodes.isEmpty()) {
        return Optional.empty();
      }

      JsonNode first = nodes.get(0);
      double lat = first.get("lat").asDouble();
      double lon = first.get("lon").asDouble();
      return Optional.of(new Coordinates(lat, lon));

    } catch (Exception e) {
      log.warn("Geocoding failed for address id={}: {}", address.getId(), e.getMessage());
      return Optional.empty();
    }
  }

  public Optional<Coordinates> geocodeSnapshot(
      String number, String street, String city, String state) {
    try {
      String streetParam = encode(number + " " + street);
      String cityParam = encode(city);
      String stateParam = encode(state);
      String url = NOMINATIM_URL
          + "?street=" + streetParam
          + "&city=" + cityParam
          + "&state=" + stateParam
          + "&country=Brazil&format=json&limit=1";

      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", USER_AGENT);
      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(URI.create(url), HttpMethod.GET, entity, String.class);

      if (response.getBody() == null || response.getBody().isBlank()) {
        return Optional.empty();
      }

      JsonNode nodes = objectMapper.readTree(response.getBody());
      if (!nodes.isArray() || nodes.isEmpty()) {
        return Optional.empty();
      }

      JsonNode first = nodes.get(0);
      return Optional.of(new Coordinates(first.get("lat").asDouble(), first.get("lon").asDouble()));

    } catch (Exception e) {
      log.warn("Geocoding snapshot failed for {}, {}: {}", street, city, e.getMessage());
      return Optional.empty();
    }
  }

  public void geocodeAndPersist(Address address,
      br.com.ragro.repository.AddressRepository addressRepository) {
    geocode(address).ifPresent(coords -> {
      address.setLatitude(BigDecimal.valueOf(coords.latitude()));
      address.setLongitude(BigDecimal.valueOf(coords.longitude()));
      addressRepository.save(address);
    });
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
