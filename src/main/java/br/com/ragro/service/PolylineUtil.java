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
        if (index >= encoded.length()) {
          throw new IllegalArgumentException("Encoded polyline truncada/inválida");
        }
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

      shift = 0;
      result = 0;
      do {
        if (index >= encoded.length()) {
          throw new IllegalArgumentException("Encoded polyline truncada/inválida");
        }
        b = encoded.charAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

      points.add(new Point(lat / 1e5, lng / 1e5));
    }
    return points;
  }

  /** Codifica pontos no formato "encoded polyline" do Google (precisão 1e-5). */
  public static String encode(List<Point> points) {
    StringBuilder sb = new StringBuilder();
    long lastLat = 0;
    long lastLng = 0;
    for (Point p : points) {
      long lat = Math.round(p.lat() * 1e5);
      long lng = Math.round(p.lng() * 1e5);
      encodeValue(lat - lastLat, sb);
      encodeValue(lng - lastLng, sb);
      lastLat = lat;
      lastLng = lng;
    }
    return sb.toString();
  }

  private static void encodeValue(long value, StringBuilder sb) {
    long v = value < 0 ? ~(value << 1) : (value << 1);
    while (v >= 0x20) {
      sb.append((char) ((0x20 | (int) (v & 0x1f)) + 63));
      v >>= 5;
    }
    sb.append((char) ((int) v + 63));
  }

  /**
   * Recorta a polyline ao sub-trecho {@code [from → to]} ao longo do traçado, devolvendo só esse
   * pedaço re-encodado. {@code to} é localizado primeiro (vértice mais próximo); {@code from} é
   * buscado APENAS no intervalo anterior a {@code to} — assim, numa rota round-trip, nunca devolve
   * a perna de volta nem paradas posteriores a {@code to} (privacidade: o cliente vê só o caminho
   * até a SUA entrega). Retorna a polyline original quando não há o que recortar.
   */
  public static String clip(
      String encoded, double fromLat, double fromLng, double toLat, double toLng) {
    List<Point> points;
    try {
      points = decode(encoded);
    } catch (IllegalArgumentException e) {
      // Polyline malformada não pode derrubar o recorte (500); devolve a original.
      return encoded;
    }
    if (points.size() < 2) {
      return encoded;
    }
    int toIdx = nearestIndex(points, toLat, toLng, 0, points.size() - 1);
    int fromIdx = nearestIndex(points, fromLat, fromLng, 0, toIdx);
    int lo = Math.min(fromIdx, toIdx);
    int hi = Math.max(fromIdx, toIdx);
    if (hi - lo < 1) {
      return encoded;
    }
    return encode(points.subList(lo, hi + 1));
  }

  private static int nearestIndex(List<Point> points, double lat, double lng, int start, int end) {
    int best = start;
    double bestDist = Double.MAX_VALUE;
    for (int i = start; i <= end; i++) {
      double d = distanceMeters(lat, lng, points.get(i).lat(), points.get(i).lng());
      if (d < bestDist) {
        bestDist = d;
        best = i;
      }
    }
    return best;
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
