package jp.hakamap.project.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DomainValueTest {
  @Test
  void textUsesUnicodeCodePointsAndTrimsUnicodeWhitespace() {
    AreaName name = new AreaName("\u3000第一組 ");
    assertThat(name.value()).isEqualTo("第一組");

    String fiftyEmoji = "😀".repeat(50);
    assertThat(new GraveName(fiftyEmoji).value()).isEqualTo(fiftyEmoji);
    assertThatThrownBy(() -> new GraveName(fiftyEmoji + "😀"))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void multilineTextNormalizesLineEndingsAndRejectsOtherControls() {
    assertThat(new GraveNotes("一行目\r\n二行目\r三行目").value()).isEqualTo("一行目\n二行目\n三行目");
    assertThatThrownBy(() -> new GraveNotes("不正\u0000"))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void coordinatesRoundHalfUpBeforeGeometryChecks() {
    MapRectangle first = rectangle("0.0004", "0", "10.0004", "10");
    MapRectangle touching = rectangle("10.00049", "0", "5", "5");
    MapRectangle overlapping = rectangle("9.9994", "0", "5", "5");

    assertThat(first.left()).isEqualByComparingTo("0.000");
    assertThat(first.right()).isEqualByComparingTo("10.000");
    assertThat(first.overlapsArea(touching)).isFalse();
    assertThat(first.touches(touching)).isTrue();
    assertThat(first.overlapsArea(overlapping)).isTrue();
  }

  @Test
  void closedContainmentIncludesSharedBoundaries() {
    MapRectangle area = rectangle("0", "0", "10", "10");
    assertThat(area.containsClosed(new MapPoint(decimal("10"), decimal("10")))).isTrue();
    assertThat(area.containsClosed(rectangle("0", "0", "10", "10"))).isTrue();
  }

  private MapRectangle rectangle(String x, String y, String width, String height) {
    return new MapRectangle(decimal(x), decimal(y), decimal(width), decimal(height));
  }

  private BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }
}
