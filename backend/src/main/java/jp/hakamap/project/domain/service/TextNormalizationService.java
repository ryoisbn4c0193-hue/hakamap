package jp.hakamap.project.domain.service;

import java.text.Normalizer;
import java.util.Locale;
import jp.hakamap.project.domain.value.TextComparisonKey;

public final class TextNormalizationService {
  public TextComparisonKey comparisonKey(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    StringBuilder result = new StringBuilder(normalized.length());
    normalized
        .codePoints()
        .forEach(
            point -> {
              if (!Character.isWhitespace(point) && !Character.isSpaceChar(point)) {
                result.appendCodePoint(toHiragana(point));
              }
            });
    return new TextComparisonKey(result.toString());
  }

  private int toHiragana(int point) {
    if (point >= 0x30A1 && point <= 0x30F6) {
      return point - 0x60;
    }
    return point;
  }
}
