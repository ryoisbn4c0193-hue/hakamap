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
  recoveryCandidate: z.boolean(),
});

const catalogSchema = z.object({
  projects: z.array(projectSchema),
  openProjectId: z.uuid().nullable(),
});

const openedProjectSchema = z.object({
  projectId: z.uuid(),
  name: z.string(),
  createdAt: z.iso.datetime(),
  updatedAt: z.iso.datetime(),
  recoveryCandidate: z
    .object({
      recoveryCreatedAt: z.iso.datetime(),
      formalUpdatedAt: z.iso.datetime(),
      stagedAssetCount: z.number().int().nonnegative(),
    })
    .nullable(),
});

const recoveryResultSchema = z.object({
  status: z.enum(['applied', 'discarded', 'base_mismatch', 'invalid']),
  code: z.string(),
});

const historySummarySchema = z.object({
  canUndo: z.boolean(),
  canRedo: z.boolean(),
  undoCount: z.number().int().nonnegative(),
  redoCount: z.number().int().nonnegative(),
});

const graveSchema = z.object({
  graveId: z.uuid(),
  managementNumber: z.string().nullable(),
  name: z.string().nullable(),
  notes: z.string().nullable(),
  x: z.number(),
  y: z.number(),
  width: z.number(),
  height: z.number(),
  rotation: z.number(),
  updatedAt: z.iso.datetime(),
});

const graveStateSchema = z.object({
  graveId: z.uuid(),
  areaId: z.uuid().nullable(),
  completionStatus: z.string(),
  incompleteReasons: z.array(z.string()),
  warnings: z.array(z.string()),
});

const assetSchema = z.object({
  assetId: z.uuid(),
  assetType: z.string(),
  graveId: z.uuid().nullable(),
  displayName: z.string().nullable(),
  description: z.string().nullable(),
  mediaType: z.string(),
  sizeBytes: z.number().int().nonnegative(),
  createdAt: z.iso.datetime(),
  updatedAt: z.iso.datetime().nullable(),
  displayOrder: z.number().int().nullable(),
});

const projectSnapshotSchema = z.object({
  projectId: z.uuid(),
  revision: z.number().int().nonnegative(),
  dirty: z.boolean(),
  project: z.object({
    projectId: z.uuid(),
    name: z.string(),
    createdAt: z.iso.datetime(),
    updatedAt: z.iso.datetime(),
  }),
  background: z.unknown().nullable(),
  areas: z.array(
    z.object({
      areaId: z.uuid(),
      name: z.string(),
      x: z.number(),
      y: z.number(),
      width: z.number(),
      height: z.number(),
      colorPreset: z.string(),
      visible: z.boolean(),
      displayOrder: z.number().int(),
    }),
  ),
  graves: z.array(graveSchema),
  assets: z.array(assetSchema),
  graveStates: z.array(graveStateSchema),
  historySummary: historySummarySchema,
  capabilities: z.object({
    canSave: z.boolean(),
    canUndo: z.boolean(),
    canRedo: z.boolean(),
    canEdit: z.boolean(),
  }),
});

const commandResponseSchema = z.object({
  status: z.string(),
  revision: z.number().int().nonnegative(),
  dirty: z.boolean(),
  upsertedAreas: z.array(z.unknown()),
  deletedAreaIds: z.array(z.uuid()),
  upsertedGraves: z.array(graveSchema),
  deletedGraveIds: z.array(z.uuid()),
  personChanges: z.array(z.unknown()),
  upsertedAssets: z.array(assetSchema),
  deletedAssetIds: z.array(z.uuid()),
  graveStates: z.array(graveStateSchema),
  warnings: z.array(z.object({ code: z.string(), count: z.number().int() })),
  historySummary: historySummarySchema,
  result: z.unknown(),
});

const personSchema = z.object({
  personId: z.uuid(),
  graveId: z.uuid(),
  name: z.string().nullable(),
  posthumousName: z.string().nullable(),
  createdAt: z.iso.datetime(),
  updatedAt: z.iso.datetime(),
  displayOrder: z.number().int(),
});

const peoplePageSchema = z.object({
  projectId: z.uuid(),
  graveId: z.uuid(),
  revision: z.number().int(),
  items: z.array(personSchema),
  nextCursor: z.string().nullable(),
  totalCount: z.number().int().nonnegative(),
});

const historySchema = z.object({
  projectId: z.uuid(),
  revision: z.number().int(),
  dirty: z.boolean(),
  items: z.array(
    z.object({
      commandId: z.uuid(),
      commandType: z.string(),
      commandTimestamp: z.iso.datetime(),
      targetCount: z.number().int(),
      applied: z.boolean(),
      savedMarker: z.boolean(),
    }),
  ),
  historySummary: historySummarySchema,
});

export type CatalogProject = z.infer<typeof projectSchema>;
export type ProjectCatalog = z.infer<typeof catalogSchema>;
export type ProjectSnapshot = z.infer<typeof projectSnapshotSchema>;
export type Grave = z.infer<typeof graveSchema>;
export type Person = z.infer<typeof personSchema>;

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

export async function chooseAttachmentFiles(): Promise<string[]> {
  const selected = await requireJson(
    await hakamapFetch('/api/v1/file-selections', {
      body: JSON.stringify({ purpose: 'attachmentImport', selectionMode: 'multipleFiles' }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
    fileSelectionSchema,
  );
  return selected.status === 'cancelled' ? [] : selected.fileSelectionIds;
}

export async function createProject(name: string, directorySelectionId: string): Promise<void> {
  await changeProject('/api/v1/projects', 'POST', { name, directorySelectionId });
}

export async function registerProject(directorySelectionId: string): Promise<void> {
  await changeProject('/api/v1/catalog/projects', 'POST', { directorySelectionId });
}

export type OpenedProject = z.infer<typeof openedProjectSchema>;

export async function openProject(projectId: string): Promise<OpenedProject> {
  return requireJson(
    await hakamapFetch(`/api/v1/catalog/projects/${projectId}/open`, { method: 'POST' }),
    openedProjectSchema,
  );
}

export async function resolveRecovery(
  projectId: string,
  action: 'apply' | 'discard',
): Promise<void> {
  const result = await requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/recovery`, {
      body: JSON.stringify({ action }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
    recoveryResultSchema,
  );
  const expectedStatus = action === 'apply' ? 'applied' : 'discarded';
  if (result.status !== expectedStatus) {
    throw new Error(result.code);
  }
}

export async function closeProject(projectId: string, action: 'save' | 'discard'): Promise<void> {
  await changeProject(`/api/v1/projects/${projectId}/close`, 'POST', { action });
}

export async function saveProject(projectId: string): Promise<void> {
  await changeProject(`/api/v1/projects/${projectId}/save`, 'POST');
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

export async function getProjectSnapshot(projectId: string): Promise<ProjectSnapshot> {
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/snapshot`),
    projectSnapshotSchema,
  );
}

export async function executeProjectCommand(
  projectId: string,
  expectedRevision: number,
  commandType: string,
  payload: unknown,
): Promise<z.infer<typeof commandResponseSchema>> {
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/commands`, {
      body: JSON.stringify({ commandType, expectedRevision, payload }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
    commandResponseSchema,
  );
}

export async function getGravePeople(projectId: string, graveId: string) {
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/graves/${graveId}/people`),
    peoplePageSchema,
  );
}

export async function getProjectHistory(projectId: string) {
  return requireJson(await hakamapFetch(`/api/v1/projects/${projectId}/history`), historySchema);
}

export async function changeHistory(
  projectId: string,
  action: 'undo' | 'redo',
  expectedRevision: number,
) {
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/history/${action}`, {
      body: JSON.stringify({ expectedRevision }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
    commandResponseSchema,
  );
}

export function clearSessionForTest(): void {
  csrfToken = undefined;
}
