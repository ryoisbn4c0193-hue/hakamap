import { z } from 'zod';

const sessionStatusSchema = z.object({
  authenticated: z.literal(true),
});

const fileSelectionSchema = z.object({
  status: z.enum(['selected', 'cancelled']),
  fileSelectionIds: z.array(z.uuid()),
  displayNames: z.array(z.string()),
});

const projectSchema = z.object({
  projectId: z.uuid(),
  name: z.string(),
  createdAt: z.iso.datetime(),
  updatedAt: z.iso.datetime(),
  state: z.enum(['active', 'trashed']),
  defaultProject: z.boolean(),
  available: z.boolean(),
  locationLabel: z.string(),
});

const catalogSchema = z.object({
  projects: z.array(projectSchema),
  openProjectId: z.uuid().nullable(),
});

export type CatalogProject = z.infer<typeof projectSchema>;
export type ProjectCatalog = z.infer<typeof catalogSchema>;

let csrfToken: string | undefined;

export async function exchangeBootstrapToken(): Promise<void> {
  const prefix = '#bootstrap=';
  if (!window.location.hash.startsWith(prefix)) {
    return;
  }
  const bootstrapToken = window.location.hash.slice(prefix.length);
  window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`);
  if (bootstrapToken.length === 0) {
    throw new Error('bootstrap-token-missing');
  }
  const response = await fetch('/bootstrap', {
    credentials: 'same-origin',
    headers: {
      'X-Hakamap-Bootstrap-Token': bootstrapToken,
    },
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error('bootstrap-exchange-failed');
  }
}

export async function initializeSession(): Promise<void> {
  const response = await fetch('/api/v1/session', {
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
    },
  });
  if (!response.ok) {
    csrfToken = undefined;
    throw new Error('session-initialization-failed');
  }
  sessionStatusSchema.parse(await response.json());
  const receivedToken = response.headers.get('X-Hakamap-CSRF-Token');
  if (receivedToken === null || receivedToken.length === 0) {
    csrfToken = undefined;
    throw new Error('csrf-token-missing');
  }
  csrfToken = receivedToken;
}

export async function hakamapFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (csrfToken === undefined) {
      throw new Error('csrf-token-unavailable');
    }
    headers.set('X-Hakamap-CSRF-Token', csrfToken);
  }
  const response = await fetch(input, {
    ...init,
    credentials: 'same-origin',
    headers,
  });
  if (response.status === 401 || response.status === 403) {
    csrfToken = undefined;
  }
  return response;
}

export async function requestApplicationExit(): Promise<void> {
  const response = await hakamapFetch('/api/v1/application/exit', {
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error('application-exit-failed');
  }
}

async function requireJson<T>(response: Response, schema: z.ZodType<T>): Promise<T> {
  if (!response.ok) {
    throw new Error(`api-request-failed-${response.status}`);
  }
  return schema.parse(await response.json());
}

async function changeProject(
  path: string,
  method: 'DELETE' | 'POST' | 'PUT',
  body?: unknown,
): Promise<void> {
  const response = await hakamapFetch(path, {
    body: body === undefined ? undefined : JSON.stringify(body),
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    method,
  });
  if (!response.ok) {
    throw new Error(`project-operation-failed-${response.status}`);
  }
}

export async function getProjectCatalog(): Promise<ProjectCatalog> {
  return requireJson(await hakamapFetch('/api/v1/catalog/projects'), catalogSchema);
}

export async function chooseProjectDirectory(
  purpose: 'projectCreateDirectory' | 'projectRelinkDirectory' | 'trashRestoreDirectory',
): Promise<{ id: string; displayName: string } | undefined> {
  const response = await hakamapFetch('/api/v1/file-selections', {
    body: JSON.stringify({ selectionMode: 'directory', purpose }),
    headers: { 'Content-Type': 'application/json' },
    method: 'POST',
  });
  const selected = await requireJson(response, fileSelectionSchema);
  if (selected.status === 'cancelled') {
    return undefined;
  }
  const id = selected.fileSelectionIds[0];
  const displayName = selected.displayNames[0];
  if (id === undefined || displayName === undefined) {
    throw new Error('file-selection-result-invalid');
  }
  return { id, displayName };
}

export async function createProject(name: string, directorySelectionId: string): Promise<void> {
  await changeProject('/api/v1/projects', 'POST', { name, directorySelectionId });
}

export async function registerProject(directorySelectionId: string): Promise<void> {
  await changeProject('/api/v1/catalog/projects', 'POST', { directorySelectionId });
}

export async function openProject(projectId: string): Promise<void> {
  await changeProject(`/api/v1/catalog/projects/${projectId}/open`, 'POST');
}

export async function closeProject(projectId: string, action: 'save' | 'discard'): Promise<void> {
  await changeProject(`/api/v1/projects/${projectId}/close`, 'POST', { action });
}

export async function setDefaultProject(projectId: string): Promise<void> {
  await changeProject('/api/v1/catalog/default-project', 'PUT', { projectId });
}

export async function relinkProject(
  projectId: string,
  directorySelectionId: string,
): Promise<void> {
  await changeProject(`/api/v1/catalog/projects/${projectId}/relink`, 'POST', {
    directorySelectionId,
  });
}

export async function unregisterProject(projectId: string): Promise<void> {
  await changeProject(`/api/v1/catalog/projects/${projectId}/registration`, 'DELETE');
}

export async function trashProject(projectId: string): Promise<void> {
  await changeProject(`/api/v1/catalog/projects/${projectId}/trash`, 'POST');
}

export async function restoreProject(
  projectId: string,
  directorySelectionId?: string,
): Promise<void> {
  await changeProject(`/api/v1/catalog/projects/${projectId}/restore`, 'POST', {
    directorySelectionId,
  });
}

export async function permanentlyDeleteProject(projectId: string): Promise<void> {
  await changeProject(`/api/v1/catalog/projects/${projectId}`, 'DELETE');
}

export function clearSessionForTest(): void {
  csrfToken = undefined;
}
