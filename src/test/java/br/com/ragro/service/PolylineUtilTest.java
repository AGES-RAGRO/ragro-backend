package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ragro.service.PolylineUtil.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolylineUtilTest {

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
    // Rota round-trip: origem(0) -> p1(1) -> p2(2, parada do cliente) -> volta(3,4 = origem).
    List<Point> route =
        List.of(
            new Point(-30.000, -51.000), // 0 origem
            new Point(-30.010, -51.010), // 1 parada de OUTRO cliente
            new Point(-30.020, -51.020), // 2 parada DESTE cliente
            new Point(-30.010, -51.005), // 3 perna de volta
            new Point(-30.000, -51.000)); // 4 origem (fim)
    String full = PolylineUtil.encode(route);

    // Produtor perto da parada 1 (a caminho); parada do cliente é a 2.
    String clipped =
        PolylineUtil.clip(full, -30.0105, -51.0105, -30.020, -51.020);
    List<Point> clippedPts = PolylineUtil.decode(clipped);

    // Só o trecho [1 → 2]: não inclui a perna de volta (vértices 3,4).
    assertThat(clippedPts).hasSize(2);
    assertThat(clippedPts.get(clippedPts.size() - 1).lat()).isCloseTo(-30.020, within());
    // Nenhum ponto da volta (lng -51.005 do vértice 3) aparece no recorte.
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
