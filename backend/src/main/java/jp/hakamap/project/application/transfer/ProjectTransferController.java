package jp.hakamap.project.application.transfer;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import jp.hakamap.infrastructure.http.LocalApiSecurityFilter;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectTransferController {
  private final ProjectTransferService transfers;

  private final OpenProjectManager openProjects;

  private final OperationRegistry operations;

  public ProjectTransferController(
      ProjectTransferService transfers,
      OpenProjectManager openProjects,
      OperationRegistry operations) {
    this.transfers = transfers;
    this.openProjects = openProjects;
    this.operations = operations;
  }

  @GetMapping("/projects/{projectId}/backups")
  ResponseEntity<ProjectTransferService.BackupListResponse> backups(
      @PathVariable UUID projectId, HttpServletRequest request) {
    long revision =
        openProjects
            .currentEditingSession()
            .filter(open -> open.projectId().equals(projectId))
            .map(open -> open.editingSession().revision())
            .orElse(0L);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(transfers.backups(projectId, revision, sessionId(request)));
  }

  @PostMapping("/projects/{projectId}/operations/export")
  ResponseEntity<OperationRegistry.OperationView> export(
      @PathVariable UUID projectId, @RequestBody ExportRequest body, HttpServletRequest request) {
    requireRevision(projectId, body.expectedRevision());
    String sessionId = sessionId(request);
    return accepted(
        operations.start(
            sessionId,
            true,
            "project:" + projectId,
            control -> {
              requireRevision(projectId, body.expectedRevision());
              transfers.export(projectId, body.fileSelectionId(), sessionId, control);
              return projectId;
            }));
  }

  @PostMapping("/projects/{projectId}/operations/backup-restore")
  ResponseEntity<OperationRegistry.OperationView> restore(
      @PathVariable UUID projectId,
      @RequestBody BackupRestoreRequest body,
      HttpServletRequest request) {
    String sessionId = sessionId(request);
    return accepted(
        operations.start(
            sessionId,
            true,
            "project:" + projectId,
            control -> {
              transfers.restore(
                  projectId,
                  body.expectedRevision(),
                  body.confirmedNoUnsavedChanges(),
                  body.backupId(),
                  body.backupVersion(),
                  sessionId,
                  control);
              return projectId;
            }));
  }

  @PostMapping("/catalog/operations/import")
  ResponseEntity<OperationRegistry.OperationView> importArchive(
      @RequestBody ImportRequest body, HttpServletRequest request) {
    String sessionId = sessionId(request);
    return accepted(
        operations.start(
            sessionId,
            true,
            "catalog",
            control ->
                transfers.importArchive(
                    body.fileSelectionId(), body.destinationSelectionId(), sessionId, control)));
  }

  @GetMapping("/operations/{operationId}")
  OperationRegistry.OperationView operation(
      @PathVariable String operationId, HttpServletRequest request) {
    return operations.get(operationId, sessionId(request));
  }

  @DeleteMapping("/operations/{operationId}")
  OperationRegistry.OperationView cancel(
      @PathVariable String operationId, HttpServletRequest request) {
    return operations.cancel(operationId, sessionId(request));
  }

  private ResponseEntity<OperationRegistry.OperationView> accepted(
      OperationRegistry.OperationView operation) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header("Location", "/api/v1/operations/" + operation.operationId())
        .body(operation);
  }

  private void requireRevision(UUID projectId, long expectedRevision) {
    var current =
        openProjects
            .currentEditingSession()
            .filter(open -> open.projectId().equals(projectId))
            .orElseThrow(() -> new ProjectTransferException("project-not-open"));
    if (current.editingSession().revision() != expectedRevision
        || current.editingSession().dirty()) {
      throw new ProjectTransferException("project-revision-conflict");
    }
  }

  private String sessionId(HttpServletRequest request) {
    return (String) request.getAttribute(LocalApiSecurityFilter.AUTHENTICATED_SESSION_ATTRIBUTE);
  }

  public record ExportRequest(long expectedRevision, UUID fileSelectionId) {}

  public record BackupRestoreRequest(
      long expectedRevision,
      String backupId,
      String backupVersion,
      boolean confirmedNoUnsavedChanges) {}

  public record ImportRequest(UUID fileSelectionId, UUID destinationSelectionId) {}
}
