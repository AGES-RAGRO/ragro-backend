package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ragro.service.PolylineUtil.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolylineUtilTest {

  // Single char with the continuation bit (>= 0x20) set, so the decoder expects another byte but
  // the string ends -> truncated/invalid encoding. ('~' = 126; 126 - 63 = 0x3F, continuation set.)
  private static final String TRUNCATED = "~";

  @Test
  void decode_shouldThrowIllegalArgument_whenEncodedIsTruncated() {
    // The do-while loops bounds-check `index >= length` and throw instead of running off the
    // end of the string (StringIndexOutOfBounds / silent corruption).
    assertThatThrownBy(() -> PolylineUtil.decode(TRUNCATED))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decode_shouldThrowIllegalArgument_whenLngHalfIsTruncated() {
    // Valid lat followed by a truncated lng half: ensures BOTH do-while loops are guarded.
    String latOk = PolylineUtil.encode(List.of(new Point(-30.0, -51.0)));
    // Complete lat+lng pair plus a dangling continuation char (new value, no end).
    String dangling = latOk + "~";
    assertThatThrownBy(() -> PolylineUtil.decode(dangling))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clip_shouldReturnInputUnchanged_whenEncodedIsTruncated() {
    // clip wraps decode in try/catch and returns the original input on a malformed polyline
    // instead of bubbling a 500.
    assertThat(PolylineUtil.clip(TRUNCATED, -30.0, -51.0, -30.1, -51.1)).isEqualTo(TRUNCATED);
  }

  @Test
  void encodeDecode_shouldRoundTrip() {
    List<Point> original =
        List.of(
            new Point(-30.0000, -51.0000),
            new Point(-30.0100, -51.0100),
            new Point(-30.0200, -51.0050));

    List<Point> roundTripped = PolylineUtil.decode(PolylineUtil.encode(original));

    assertThat(roundTripped).hasSize(3);
    for (int i = 0; i < original.size(); i++) {
      assertThat(roundTripped.get(i).lat()).isCloseTo(original.get(i).lat(), within());
      assertThat(roundTripped.get(i).lng()).isCloseTo(original.get(i).lng(), within());
    }
  }

  @Test
  void clip_shouldReturnOnlySegmentFromProducerToStop_notFullRoundTrip() {
    // Round-trip route: origin(0) -> p1(1) -> p2(2, this customer's stop) -> return(3,4 = origin).
    List<Point> route =
        List.of(
            new Point(-30.000, -51.000), // 0 origin
            new Point(-30.010, -51.010), // 1 ANOTHER customer's stop
            new Point(-30.020, -51.020), // 2 THIS customer's stop
            new Point(-30.010, -51.005), // 3 return leg
            new Point(-30.000, -51.000)); // 4 origin (end)
    String full = PolylineUtil.encode(route);

    // Producer near stop 1 (en route); the customer's stop is 2.
    String clipped =
        PolylineUtil.clip(full, -30.0105, -51.0105, -30.020, -51.020);
    List<Point> clippedPts = PolylineUtil.decode(clipped);

    // Only segment [1 -> 2]: excludes the return leg (vertices 3,4).
    assertThat(clippedPts).hasSize(2);
    assertThat(clippedPts.get(clippedPts.size() - 1).lat()).isCloseTo(-30.020, within());
    // No return point (lng -51.005 of vertex 3) appears in the clip.
    assertThat(clippedPts).noneMatch(p -> Math.abs(p.lng() - (-51.005)) < 1e-4);
  }

  @Test
  void clip_shouldReturnOriginal_whenTooFewPoints() {
    String single = PolylineUtil.encode(List.of(new Point(-30.0, -51.0)));
    assertThat(PolylineUtil.clip(single, -30.0, -51.0, -30.1, -51.1)).isEqualTo(single);
  }

  private static org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(1e-4);
  }
}
