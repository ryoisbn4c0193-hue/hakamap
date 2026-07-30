package jp.hakamap.project.application.editing;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.service.TextNormalizationService;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AssetType;
import jp.hakamap.project.domain.value.GraveId;

final class ProjectSearchService {
  private final TextNormalizationService normalization = new TextNormalizationService();

  List<SearchResult> search(ProjectAggregate project, String input) {
    String query = normalize(input);
    if (query.isEmpty()) {
      return List.of();
    }
    Map<GraveId, StringBuilder> values = new HashMap<>();
    project.graves().keySet().forEach(id -> values.put(id, new StringBuilder()));
    project
        .people()
        .values()
        .forEach(
            person -> {
              append(
                  values.get(person.graveId()),
                  person.name().map(value -> value.value()).orElse(null));
              append(
                  values.get(person.graveId()),
                  person.posthumousName().map(value -> value.value()).orElse(null));
            });
    project.assets().values().stream()
        .filter(asset -> asset.type() == AssetType.ATTACHMENT)
        .forEach(
            asset -> {
              StringBuilder target = values.get(asset.graveId().orElseThrow());
              append(target, asset.displayName().map(value -> value.value()).orElse(null));
              append(target, asset.description().map(value -> value.value()).orElse(null));
            });

    Map<AreaId, Area> areas = project.areas();
    List<SearchResult> results = new ArrayList<>();
    for (Grave grave : project.graves().values()) {
      AreaId areaId = project.graveStatus(grave.id()).areaId().orElse(null);
      Area area = areaId == null ? null : areas.get(areaId);
      append(values.get(grave.id()), area == null ? null : area.name().value());
      append(
          values.get(grave.id()),
          grave.managementNumber().map(value -> value.value()).orElse(null));
      append(values.get(grave.id()), grave.name().map(value -> value.value()).orElse(null));
      if (normalize(values.get(grave.id()).toString()).contains(query)) {
        results.add(
            new SearchResult(
                grave.id().value(),
                area == null ? null : area.name().value(),
                area == null ? Integer.MAX_VALUE : area.displayOrder().value(),
                grave.managementNumber().map(value -> value.value()).orElse(null),
                grave.name().map(value -> value.value()).orElse(null)));
      }
    }
    results.sort(this::compare);
    return List.copyOf(results);
  }

  private int compare(SearchResult left, SearchResult right) {
    int result = Boolean.compare(left.areaName() == null, right.areaName() == null);
    if (result == 0) {
      result = Integer.compare(left.areaOrder(), right.areaOrder());
    }
    if (result == 0) {
      result = Boolean.compare(left.managementNumber() == null, right.managementNumber() == null);
    }
    if (result == 0) {
      result = naturalCompare(left.managementNumber(), right.managementNumber());
    }
    if (result == 0) {
      result = compareText(left.graveName(), right.graveName());
    }
    return result == 0 ? left.graveId().compareTo(right.graveId()) : result;
  }

  private int compareText(String left, String right) {
    return normalize(left).compareTo(normalize(right));
  }

  private int naturalCompare(String left, String right) {
    if (left == null || right == null) {
      return 0;
    }
    String a = normalize(left);
    String b = normalize(right);
    int ai = 0;
    int bi = 0;
    while (ai < a.length() && bi < b.length()) {
      boolean digits = Character.isDigit(a.charAt(ai)) && Character.isDigit(b.charAt(bi));
      int ae = ai + 1;
      int be = bi + 1;
      while (ae < a.length() && Character.isDigit(a.charAt(ae)) == digits) {
        ae++;
      }
      while (be < b.length() && Character.isDigit(b.charAt(be)) == digits) {
        be++;
      }
      int result =
          digits
              ? new BigInteger(a.substring(ai, ae)).compareTo(new BigInteger(b.substring(bi, be)))
              : a.substring(ai, ae).compareTo(b.substring(bi, be));
      if (result != 0) {
        return result;
      }
      ai = ae;
      bi = be;
    }
    return Integer.compare(a.length(), b.length());
  }

  private void append(StringBuilder target, String value) {
    if (target != null && value != null) {
      target.append('\n').append(value);
    }
  }

  private String normalize(String value) {
    if (value == null
        || value
            .codePoints()
            .allMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) {
      return "";
    }
    return normalization.comparisonKey(value).value();
  }

  record SearchResult(
      UUID graveId, String areaName, int areaOrder, String managementNumber, String graveName) {}
}
