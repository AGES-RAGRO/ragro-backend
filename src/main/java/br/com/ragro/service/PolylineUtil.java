package br.com.ragro.service;

import java.util.ArrayList;
import java.util.List;

/** Decoding of Google's "encoded polyline" and basic geometry (haversine). */
public final class PolylineUtil {

  private PolylineUtil() {}

  public record Point(double lat, double lng) {}

  /** Standard Google algorithm (1e-5 precision). */
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

  /** Encodes points as a Google encoded polyline (1e-5 precision). */
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
   * Clips the polyline to the {@code [from → to]} sub-segment, re-encoded. {@code to} is located first
   * (nearest vertex); {@code from} is searched only before {@code to} — so a round-trip route never
   * returns the return leg or later stops (privacy: customer sees only the path to their delivery).
   * Returns the original polyline when there is nothing to clip.
   */
  public static String clip(
      String encoded, double fromLat, double fromLng, double toLat, double toLng) {
    List<Point> points;
    try {
      points = decode(encoded);
    } catch (IllegalArgumentException e) {
      // Malformed polyline must not 500 the clip; return the original.
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

  /** Distance in meters between two points (haversine). */
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
