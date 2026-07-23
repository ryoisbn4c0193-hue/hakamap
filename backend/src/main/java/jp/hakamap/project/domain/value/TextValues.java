package jp.hakamap.project.domain.value;

import java.util.Optional;

final class TextValues {
  private TextValues() {}

  static String requiredSingleLine(String input, int maximumCodePoints, String code) {
    return optional(input, maximumCodePoints, false, code)
        .orElseThrow(() -> new DomainValidationException(code));
  }

  static Optional<String> optionalSingleLine(String input, int maximumCodePoints, String code) {
    return optional(input, maximumCodePoints, false, code);
  }

  static Optional<String> optionalMultiLine(String input, int maximumCodePoints, String code) {
    return optional(input, maximumCodePoints, true, code);
  }

  private static Optional<String> optional(
      String input, int maximumCodePoints, boolean allowLineFeed, String code) {
    if (input == null) {
      return Optional.empty();
    }
    String normalizedLineEndings = input.replace("\r\n", "\n").replace('\r', '\n');
    String value = stripUnicodeWhitespace(normalizedLineEndings);
    if (value.isEmpty()) {
      return Optional.empty();
    }
    if (value.codePointCount(0, value.length()) > maximumCodePoints) {
      throw new DomainValidationException(code);
    }
    value
        .codePoints()
        .filter(point -> Character.isISOControl(point) && !(allowLineFeed && point == '\n'))
        .findAny()
        .ifPresent(
            point -> {
              throw new DomainValidationException(code);
            });
    if (value.indexOf('\u2028') >= 0 || value.indexOf('\u2029') >= 0) {
      throw new DomainValidationException(code);
    }
    return Optional.of(value);
  }

  static String stripUnicodeWhitespace(String input) {
    int start = 0;
    int end = input.length();
    while (start < end) {
      int point = input.codePointAt(start);
      if (!isUnicodeWhitespace(point)) {
        break;
      }
      start += Character.charCount(point);
    }
    while (start < end) {
      int point = input.codePointBefore(end);
      if (!isUnicodeWhitespace(point)) {
        break;
      }
      end -= Character.charCount(point);
    }
    return input.substring(start, end);
  }

  static boolean isUnicodeWhitespace(int point) {
    return Character.isWhitespace(point) || Character.isSpaceChar(point);
  }
}
