package jp.hakamap.project.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jp.hakamap.persistence.json.PersistenceTestFixtures;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.project.domain.value.ProjectName;
import org.junit.jupiter.api.Test;

class ProjectEditingSessionTest {
  private final ProjectFingerprintCalculator fingerprints =
      new ProjectFingerprintCalculator(PersistenceTestFixtures.codec(), new ProjectFileV1Mapper());

  @Test
  void appliesUndoAndRedoUsingFixedDelta() {
    ProjectEditingSession session = session();
    ProjectChangeSet change = rename(1, "変更後");

    session.apply(0, change);

    assertThat(session.current().metadata().name().value()).isEqualTo("変更後");
    assertThat(session.revision()).isEqualTo(1);
    assertThat(session.dirty()).isTrue();
    session.undo(1);
    assertThat(session.current().metadata().name().value()).isEqualTo("テスト墓地");
    assertThat(session.revision()).isEqualTo(2);
    assertThat(session.dirty()).isFalse();
    session.redo(2);
    assertThat(session.current().metadata().name().value()).isEqualTo("変更後");
    assertThat(session.revision()).isEqualTo(3);
  }

  @Test
  void retainsHistoryAcrossSaveAndUsesFingerprintForDirtyState() {
    ProjectEditingSession session = session();
    session.apply(0, rename(1, "保存名"));
    StateFingerprint saved = fingerprints.calculate(session.current());

    session.markSaved(session.current(), saved, "1".repeat(64));

    assertThat(session.dirty()).isFalse();
    assertThat(session.undoSize()).isEqualTo(1);
    assertThat(session.history().getFirst().savedMarker()).isTrue();
    session.undo(1);
    assertThat(session.dirty()).isTrue();
    session.redo(2);
    assertThat(session.dirty()).isFalse();
  }

  @Test
  void limitsHistoryToOneHundredAndClearsRedoOnBranch() {
    ProjectEditingSession session = session();
    for (int index = 1; index <= 101; index++) {
      String before = session.current().metadata().name().value();
      session.apply(session.revision(), rename(index, before, "名称" + index));
    }

    assertThat(session.undoSize()).isEqualTo(100);
    session.undo(session.revision());
    assertThat(session.redoSize()).isEqualTo(1);
    String before = session.current().metadata().name().value();
    session.apply(session.revision(), rename(200, before, "分岐"));
    assertThat(session.redoSize()).isZero();
  }

  @Test
  void rejectsStaleRevisionWithoutChangingState() {
    ProjectEditingSession session = session();
    session.apply(0, rename(1, "変更後"));

    assertThatThrownBy(() -> session.undo(0))
        .isInstanceOfSatisfying(
            EditingSessionException.class,
            exception -> assertThat(exception.code()).isEqualTo("project-revision-conflict"));
    assertThat(session.revision()).isEqualTo(1);
    assertThat(session.current().metadata().name().value()).isEqualTo("変更後");
  }

  private ProjectEditingSession session() {
    return new ProjectEditingSession(
        PersistenceTestFixtures.emptyProject(), "0".repeat(64), fingerprints);
  }

  private ProjectChangeSet rename(int sequence, String after) {
    return rename(sequence, "テスト墓地", after);
  }

  private ProjectChangeSet rename(int sequence, String before, String after) {
    return new ProjectChangeSet(
        new CommandId(commandUuid(sequence)),
        CommandType.RENAME_PROJECT,
        Instant.parse("2026-01-02T03:04:05.006Z"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Optional.of(ValueDelta.changed(new ProjectName(before), new ProjectName(after))),
        Optional.empty(),
        Set.of());
  }

  private UUID commandUuid(int sequence) {
    return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", sequence));
  }
}
