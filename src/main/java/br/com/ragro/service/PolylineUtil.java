package br.com.ragro.service;

import java.util.ArrayList;
import java.util.List;

/** Decodificação da "encoded polyline" do Google e geometria básica (haversine). */
public final class PolylineUtil {

  private PolylineUtil() {}

  public record Point(double lat, double lng) {}

  /** Algoritmo padrão do Google (precisão 1e-5). */
  public static List<Point> decode(String encoded) {
    List<Point> points = new ArrayList<>();
    if (encoded == null || encoded.isEmpty()) {
      return points;
    }
    int index = 0;
    int lat = 0;
    int lng = 0;
    while (index < encoded.length()) {
      int shift = 0;
      int result = 0;
      int b;
      do {
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

      shift = 0;
      result = 0;
      do {
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

      points.add(new Point(lat / 1e5, lng / 1e5));
    }
    return points;
  }

  /** Distância em metros entre dois pontos (haversine). */
  public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double rLat1 = Math.toRadians(lat1);
    double rLat2 = Math.toRadians(lat2);
    double dLat = rLat2 - rLat1;
    double dLng = Math.toRadians(lng2 - lng1);
    double h =
        Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(rLat1) * Math.cos(rLat2) * Math.pow(Math.sin(dLng / 2), 2);
    return 2 * 6_371_000.0 * Math.asin(Math.sqrt(h));
  }
}
