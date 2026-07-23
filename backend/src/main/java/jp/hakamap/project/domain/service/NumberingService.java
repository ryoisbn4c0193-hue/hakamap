package jp.hakamap.project.domain.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.ManagementNumber;

public final class NumberingService {
  private static final BigDecimal HALF = new BigDecimal("0.5");

  public List<NumberingAssignment> preview(
      java.util.Collection<Grave> graves, NumberingRequest request) {
    if (graves.isEmpty()) {
      throw new ProjectInvariantException("grave-selection-required");
    }
    if (graves.stream().anyMatch(grave -> grave.managementNumber().isPresent())) {
      throw new ProjectInvariantException("grave-number-already-set");
    }
    List<Grave> ordered = visualOrder(graves);
    List<NumberingAssignment> result = new ArrayList<>(ordered.size());
    BigInteger number = request.start();
    for (Grave grave : ordered) {
      String digits = number.toString();
      String padded = "0".repeat(Math.max(0, request.digits() - digits.length())) + digits;
      result.add(
          new NumberingAssignment(
              grave.id(), new ManagementNumber(request.prefix() + padded + request.suffix())));
      number = number.add(BigInteger.ONE);
    }
    return List.copyOf(result);
  }

  public List<Grave> visualOrder(java.util.Collection<Grave> graves) {
    List<Grave> remaining = new ArrayList<>(graves);
    remaining.sort(baseComparator());
    List<List<Grave>> rows = new ArrayList<>();
    while (!remaining.isEmpty()) {
      Grave base = remaining.removeFirst();
      List<Grave> row = new ArrayList<>();
      row.add(base);
      while (!remaining.isEmpty() && sameVisualRow(base, remaining.getFirst())) {
        row.add(remaining.removeFirst());
      }
      row.sort(rowComparator());
      rows.add(row);
    }
    rows.sort(
        Comparator.comparing((List<Grave> row) -> row.getFirst().rectangle().center().y())
            .thenComparing(row -> row.getFirst().rectangle().center().x())
            .thenComparing(row -> row.getFirst().id().value()));
    return rows.stream().flatMap(List::stream).toList();
  }

  private boolean sameVisualRow(Grave first, Grave second) {
    BigDecimal required =
        first.rectangle().size().height().min(second.rectangle().size().height()).multiply(HALF);
    return first.rectangle().verticalOverlap(second.rectangle()).compareTo(required) >= 0;
  }

  private Comparator<Grave> baseComparator() {
    return Comparator.comparing((Grave grave) -> grave.rectangle().center().y())
        .thenComparing(grave -> grave.rectangle().center().x())
        .thenComparing(grave -> grave.id().value());
  }

  private Comparator<Grave> rowComparator() {
    return Comparator.comparing((Grave grave) -> grave.rectangle().center().x())
        .thenComparing(grave -> grave.rectangle().center().y())
        .thenComparing(grave -> grave.id().value());
  }
}
