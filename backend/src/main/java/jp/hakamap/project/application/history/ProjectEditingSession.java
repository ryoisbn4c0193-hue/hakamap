package jp.hakamap.project.application.history;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.value.AssetId;

public final class ProjectEditingSession {
  private static final int MAXIMUM_HISTORY_SIZE = 100;

  private final ProjectFingerprintCalculator fingerprints;

  private ProjectAggregate current;

  private long revision;

  private final Deque<ProjectChangeSet> undo = new ArrayDeque<>();

  private final Deque<ProjectChangeSet> redo = new ArrayDeque<>();

  private StateFingerprint lastSavedFingerprint;

  private String baseProjectSha256;

  private Optional<CommandId> lastSavedCommandId = Optional.empty();

  private boolean editingStopped;

  public ProjectEditingSession(
      ProjectAggregate initial,
      String baseProjectSha256,
      ProjectFingerprintCalculator fingerprints) {
    this(initial, initial, baseProjectSha256, fingerprints);
  }

  public ProjectEditingSession(
      ProjectAggregate initial,
      ProjectAggregate lastSaved,
      String baseProjectSha256,
      ProjectFingerprintCalculator fingerprints) {
    this.current = Objects.requireNonNull(initial, "initial");
    this.baseProjectSha256 = requireSha256(baseProjectSha256);
    this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
    this.lastSavedFingerprint =
        fingerprints.calculate(Objects.requireNonNull(lastSaved, "lastSaved"));
  }

  public ProjectAggregate current() {
    return current;
  }

  public long revision() {
    return revision;
  }

  public String baseProjectSha256() {
    return baseProjectSha256;
  }

  public boolean dirty() {
    return !fingerprints.calculate(current).equals(lastSavedFingerprint);
  }

  public boolean editingStopped() {
    return editingStopped;
  }

  public int undoSize() {
    return undo.size();
  }

  public int redoSize() {
    return redo.size();
  }

  public void apply(long expectedRevision, ProjectChangeSet changeSet) {
    requireEditable(expectedRevision);
    ProjectAggregate candidate = ProjectStateTransitions.applyAfter(current, changeSet);
    if (fingerprints.calculate(candidate).equals(fingerprints.calculate(current))) {
      return;
    }
    current = candidate;
    revision++;
    redo.clear();
    undo.addLast(changeSet);
    while (undo.size() > MAXIMUM_HISTORY_SIZE) {
      undo.removeFirst();
    }
  }

  public void requireRevision(long expectedRevision) {
    requireEditable(expectedRevision);
  }

  public void undo(long expectedRevision) {
    requireEditable(expectedRevision);
    ProjectChangeSet changeSet = undo.peekLast();
    if (changeSet == null) {
      throw new EditingSessionException("undo-empty");
    }
    ProjectAggregate candidate = ProjectStateTransitions.applyBefore(current, changeSet);
    current = candidate;
    undo.removeLast();
    redo.addLast(changeSet);
    revision++;
  }

  public void redo(long expectedRevision) {
    requireEditable(expectedRevision);
    ProjectChangeSet changeSet = redo.peekLast();
    if (changeSet == null) {
      throw new EditingSessionException("redo-empty");
    }
    ProjectAggregate candidate = ProjectStateTransitions.applyAfter(current, changeSet);
    current = candidate;
    redo.removeLast();
    undo.addLast(changeSet);
    revision++;
  }

  public List<HistoryEntry> history() {
    List<HistoryEntry> entries = new ArrayList<>();
    undo.forEach(changeSet -> entries.add(toEntry(changeSet, true)));
    redo.forEach(changeSet -> entries.add(toEntry(changeSet, false)));
    entries.sort(
        Comparator.comparing(HistoryEntry::commandTimestamp)
            .thenComparing(entry -> entry.commandId().value())
            .reversed());
    return List.copyOf(entries);
  }

  public Set<AssetId> retainedAssetIds() {
    return java.util.stream.Stream.concat(undo.stream(), redo.stream())
        .flatMap(changeSet -> changeSet.retainedAssetIds().stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  public Optional<CommandId> lastSavedCommandId() {
    return lastSavedCommandId;
  }

  public void markSaved(ProjectAggregate saved, StateFingerprint fingerprint, String jsonSha256) {
    current = Objects.requireNonNull(saved, "saved");
    lastSavedFingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    baseProjectSha256 = requireSha256(jsonSha256);
    lastSavedCommandId = Optional.ofNullable(undo.peekLast()).map(ProjectChangeSet::commandId);
  }

  public void stopEditing() {
    editingStopped = true;
  }

  public void resumeEditing() {
    editingStopped = false;
  }

  public void resumeAfterOutcomeResolution(
      ProjectAggregate resolved, StateFingerprint fingerprint, String jsonSha256) {
    current = Objects.requireNonNull(resolved, "resolved");
    lastSavedFingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    baseProjectSha256 = requireSha256(jsonSha256);
    editingStopped = false;
  }

  private void requireEditable(long expectedRevision) {
    if (editingStopped) {
      throw new EditingSessionException("editing-stopped");
    }
    if (expectedRevision != revision) {
      throw new EditingSessionException("project-revision-conflict");
    }
  }

  private HistoryEntry toEntry(ProjectChangeSet changeSet, boolean applied) {
    return new HistoryEntry(
        changeSet.commandId(),
        changeSet.commandType(),
        changeSet.commandTimestamp(),
        changeSet.targetCount(),
        applied,
        lastSavedCommandId.map(changeSet.commandId()::equals).orElse(false));
  }

  private String requireSha256(String value) {
    if (value == null || !value.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("invalid-project-sha256");
    }
    return value;
  }
}
