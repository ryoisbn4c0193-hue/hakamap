package jp.hakamap.project.application.catalog;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import jp.hakamap.infrastructure.http.LocalApiSecurityFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectCatalogController {
  private final ProjectCatalogService catalog;

  public ProjectCatalogController(ProjectCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/catalog/projects")
  ProjectCatalogService.CatalogView list() {
    return catalog.list();
  }

  @PostMapping("/projects")
  ProjectCatalogService.ProjectView create(
      @RequestBody CreateProjectRequest body, HttpServletRequest request) {
    return catalog.create(sessionId(request), body.directorySelectionId(), body.name());
  }

  @PostMapping("/catalog/projects")
  ProjectCatalogService.ProjectView register(
      @RequestBody SelectionRequest body, HttpServletRequest request) {
    return catalog.registerExisting(sessionId(request), body.directorySelectionId());
  }

  @PostMapping("/catalog/projects/{projectId}/open")
  ProjectCatalogService.OpenProjectView open(@PathVariable UUID projectId) {
    return catalog.open(projectId);
  }

  @PostMapping("/projects/{projectId}/close")
  ProjectCatalogService.CloseProjectView close(
      @PathVariable UUID projectId,
      @RequestBody CloseProjectRequest body,
      HttpServletRequest request) {
    return catalog.close(projectId, body.action(), sessionId(request));
  }

  @PostMapping("/catalog/projects/{projectId}/relink")
  ProjectCatalogService.ProjectView relink(
      @PathVariable UUID projectId,
      @RequestBody SelectionRequest body,
      HttpServletRequest request) {
    return catalog.relink(projectId, sessionId(request), body.directorySelectionId());
  }

  @DeleteMapping("/catalog/projects/{projectId}/registration")
  ResponseEntity<Void> unregister(@PathVariable UUID projectId) {
    catalog.unregister(projectId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/catalog/default-project")
  ProjectCatalogService.ProjectView setDefault(@RequestBody DefaultProjectRequest body) {
    return catalog.setDefault(body.projectId());
  }

  @DeleteMapping("/catalog/default-project")
  ResponseEntity<Void> clearDefault() {
    catalog.clearDefault();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/catalog/projects/{projectId}/trash")
  ProjectCatalogService.ProjectView trash(@PathVariable UUID projectId) {
    return catalog.trash(projectId);
  }

  @PostMapping("/catalog/projects/{projectId}/restore")
  ProjectCatalogService.ProjectView restore(
      @PathVariable UUID projectId,
      @RequestBody RestoreProjectRequest body,
      HttpServletRequest request) {
    return catalog.restore(projectId, sessionId(request), body.directorySelectionId());
  }

  @DeleteMapping("/catalog/projects/{projectId}")
  ResponseEntity<Void> permanentlyDelete(@PathVariable UUID projectId) {
    catalog.permanentlyDelete(projectId);
    return ResponseEntity.noContent().build();
  }

  private String sessionId(HttpServletRequest request) {
    return (String) request.getAttribute(LocalApiSecurityFilter.AUTHENTICATED_SESSION_ATTRIBUTE);
  }

  public record CreateProjectRequest(String name, UUID directorySelectionId) {}

  public record SelectionRequest(UUID directorySelectionId) {}

  public record CloseProjectRequest(String action) {}

  public record DefaultProjectRequest(UUID projectId) {}

  public record RestoreProjectRequest(UUID directorySelectionId) {}
}
