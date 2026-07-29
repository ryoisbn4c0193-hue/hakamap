package jp.hakamap.project.application.editing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.hakamap.project.application.history.ProjectChangeSet;
import jp.hakamap.project.domain.service.NumberingAssignment;

final class EditingTokenStore {
  private static final Duration LIFETIME = Duration.ofMinutes(5);

  private final Clock clock;

  private final Map<String, ConfirmationCandidate> confirmations = new LinkedHashMap<>();

  private final Map<String, NumberingCandidate> numberings = new LinkedHashMap<>();

  private final Map<String, PeopleCursor> peopleCursors = new LinkedHashMap<>();

  EditingTokenStore(Clock clock) {
    this.clock = clock;
  }

  synchronized StoredConfirmation storeConfirmation(
      String sessionId,
      UUID projectId,
      long revision,
      ProjectChangeSet changeSet,
      EditingApiModels.CommandResultResponse result) {
    discardExpired();
    String token = UUID.randomUUID().toString();
    Instant expiresAt = clock.instant().plus(LIFETIME);
    confirmations.put(
        token,
        new ConfirmationCandidate(sessionId, projectId, revision, changeSet, result, expiresAt));
    return new StoredConfirmation(token, expiresAt);
  }

  synchronized ConfirmedCommand consumeConfirmation(
      String token, String sessionId, UUID projectId, long revision) {
    discardExpired();
    ConfirmationCandidate candidate = confirmations.remove(token);
    if (candidate == null
        || !candidate.sessionId().equals(sessionId)
        || !candidate.projectId().equals(projectId)
        || candidate.revision() != revision) {
      throw new EditingApiException("command-confirmation-invalid");
    }
    return new ConfirmedCommand(candidate.changeSet(), candidate.result());
  }

  synchronized void discardConfirmation(String token, String sessionId) {
    ConfirmationCandidate candidate = confirmations.get(token);
    if (candidate == null || !candidate.sessionId().equals(sessionId)) {
      throw new EditingApiException("command-confirmation-invalid");
    }
    confirmations.remove(token);
  }

  synchronized StoredNumbering storeNumbering(
      String sessionId, UUID projectId, long revision, List<NumberingAssignment> assignments) {
    discardExpired();
    String token = UUID.randomUUID().toString();
    Instant expiresAt = clock.instant().plus(LIFETIME);
    numberings.put(
        token,
        new NumberingCandidate(
            sessionId, projectId, revision, List.copyOf(assignments), expiresAt));
    return new StoredNumbering(token, expiresAt, assignments);
  }

  synchronized List<NumberingAssignment> consumeNumbering(
      String token, String sessionId, UUID projectId, long revision) {
    discardExpired();
    NumberingCandidate candidate = numberings.remove(token);
    if (candidate == null
        || !candidate.sessionId().equals(sessionId)
        || !candidate.projectId().equals(projectId)
        || candidate.revision() != revision) {
      throw new EditingApiException("numbering-preview-invalid");
    }
    return candidate.assignments();
  }

  synchronized void invalidateSession(String sessionId) {
    confirmations.values().removeIf(value -> value.sessionId().equals(sessionId));
    numberings.values().removeIf(value -> value.sessionId().equals(sessionId));
    peopleCursors.values().removeIf(value -> value.sessionId().equals(sessionId));
  }

  synchronized String storePeopleCursor(
      String sessionId, UUID projectId, UUID graveId, long revision, int start) {
    discardExpired();
    String token = UUID.randomUUID().toString();
    peopleCursors.put(
        token,
        new PeopleCursor(
            sessionId, projectId, graveId, revision, start, clock.instant().plus(LIFETIME)));
    return token;
  }

  synchronized int resolvePeopleCursor(
      String token, String sessionId, UUID projectId, UUID graveId, long revision) {
    if (token == null || token.isBlank()) {
      return 0;
    }
    discardExpired();
    PeopleCursor cursor = peopleCursors.remove(token);
    if (cursor == null
        || !cursor.sessionId().equals(sessionId)
        || !cursor.projectId().equals(projectId)
        || !cursor.graveId().equals(graveId)
        || cursor.revision() != revision) {
      throw new EditingApiException("request-field-invalid");
    }
    return cursor.start();
  }

  private void discardExpired() {
    Instant now = clock.instant();
    confirmations.values().removeIf(value -> !now.isBefore(value.expiresAt()));
    numberings.values().removeIf(value -> !now.isBefore(value.expiresAt()));
    peopleCursors.values().removeIf(value -> !now.isBefore(value.expiresAt()));
  }

  record StoredConfirmation(String token, Instant expiresAt) {}

  record ConfirmedCommand(
      ProjectChangeSet changeSet, EditingApiModels.CommandResultResponse result) {}

  record StoredNumbering(String token, Instant expiresAt, List<NumberingAssignment> assignments) {}

  private record ConfirmationCandidate(
      String sessionId,
      UUID projectId,
      long revision,
      ProjectChangeSet changeSet,
      EditingApiModels.CommandResultResponse result,
      Instant expiresAt) {}

  private record NumberingCandidate(
      String sessionId,
      UUID projectId,
      long revision,
      List<NumberingAssignment> assignments,
      Instant expiresAt) {}

  private record PeopleCursor(
      String sessionId,
      UUID projectId,
      UUID graveId,
      long revision,
      int start,
      Instant expiresAt) {}
}
