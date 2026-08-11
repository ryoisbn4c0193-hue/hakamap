import { z } from 'zod';

import { withFileSelectionActivity } from './fileSelectionActivity';

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

const backgroundSchema = z.object({
  assetId: z.uuid(),
  x: z.number(),
  y: z.number(),
  rotation: z.number(),
  scaleX: z.number(),
  scaleY: z.number(),
});

const backgroundTileManifestSchema = z.object({
  width: z.number().int().positive(),
  height: z.number().int().positive(),
  tileSize: z.number().int().positive().max(1024),
  maximumLevel: z.number().int().nonnegative(),
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
  background: backgroundSchema.nullable(),
  areas: z.array(
    z.object({
      areaId: z.uuid(),
      name: z.string(),
      x: z.number(),
      y: z.number(),
      width: z.number(),
      height: z.number(),
      rotation: z.number(),
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
  status: z.enum(['applied', 'noChange']),
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

const confirmationRequiredSchema = z.object({
  status: z.literal('confirmationRequired'),
  revision: z.number().int().nonnegative(),
  confirmationToken: z.string(),
  expiresAt: z.iso.datetime(),
  warnings: z.array(z.object({ code: z.string(), count: z.number().int().positive() })),
});

const commandExecutionResponseSchema = z.union([commandResponseSchema, confirmationRequiredSchema]);

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

const graveSearchPageSchema = z.object({
  projectId: z.uuid(),
  revision: z.number().int(),
  items: z.array(
    z.object({
      graveId: z.uuid(),
      areaName: z.string().nullable(),
      managementNumber: z.string().nullable(),
      graveName: z.string().nullable(),
    }),
  ),
  nextCursor: z.string().nullable(),
  totalCount: z.number().int().nonnegative(),
});

const backupListSchema = z.object({
  projectId: z.uuid(),
  revision: z.number().int().nonnegative(),
  items: z.array(
    z.object({
      backupId: z.string(),
      backupType: z.enum(['automatic', 'preRestore']),
      createdAt: z.iso.datetime(),
      sizeBytes: z.number().int().nonnegative(),
      applicationVersion: z.string().nullable(),
      projectName: z.string().nullable(),
      restorable: z.boolean(),
      unavailableReason: z.string().nullable(),
      backupVersion: z.string(),
    }),
  ),
});

const operationSchema = z.object({
  operationId: z.string(),
  status: z.enum(['queued', 'running', 'committing', 'succeeded', 'failed', 'cancelled']),
  cancellable: z.boolean(),
  phaseCode: z.string(),
  progressPercent: z.number().int().min(0).max(100).nullable(),
  projectId: z.uuid().nullable(),
  errorCode: z.string().nullable(),
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
export type PeoplePage = z.infer<typeof peoplePageSchema>;
export type GraveSearchPage = z.infer<typeof graveSearchPageSchema>;
export type BackupList = z.infer<typeof backupListSchema>;
export type BackgroundTileManifest = z.infer<typeof backgroundTileManifestSchema>;

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
    let code: string | undefined;
    try {
      const problem = z.object({ code: z.string() }).safeParse(await response.clone().json());
      if (problem.success) code = problem.data.code;
    } catch {
      // Problem Detailsを読めない場合もHTTP状態を使って安全に通知する。
    }
    throw new HakamapApiError(response.status, code);
  }
  return schema.parse(await response.json());
}

export class HakamapApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, code?: string) {
    super(code ?? `api-request-failed-${status}`);
    this.name = 'HakamapApiError';
    this.status = status;
    this.code = code;
  }
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
    let code: string | undefined;
    try {
      const problem = z.object({ code: z.string() }).safeParse(await response.clone().json());
      if (problem.success) code = problem.data.code;
    } catch {
      // Problem Detailsを読めない場合もHTTP状態を使って安全に通知する。
    }
    throw new HakamapApiError(response.status, code);
  }
}

export async function getProjectCatalog(): Promise<ProjectCatalog> {
  return requireJson(await hakamapFetch('/api/v1/catalog/projects'), catalogSchema);
}

export async function chooseProjectDirectory(
  purpose: 'projectCreateDirectory' | 'projectRelinkDirectory' | 'trashRestoreDirectory',
): Promise<{ id: string; displayName: string } | undefined> {
  const response = await withFileSelectionActivity(() =>
    hakamapFetch('/api/v1/file-selections', {
      body: JSON.stringify({ selectionMode: 'directory', purpose }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
  );
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
    await withFileSelectionActivity(() =>
      hakamapFetch('/api/v1/file-selections', {
        body: JSON.stringify({ purpose: 'attachmentImport', selectionMode: 'multipleFiles' }),
        headers: { 'Content-Type': 'application/json' },
        method: 'POST',
      }),
    ),
    fileSelectionSchema,
  );
  return selected.status === 'cancelled' ? [] : selected.fileSelectionIds;
}

export async function chooseBackgroundFile(): Promise<string | undefined> {
  const selected = await requireJson(
    await withFileSelectionActivity(() =>
      hakamapFetch('/api/v1/file-selections', {
        body: JSON.stringify({ purpose: 'backgroundImport', selectionMode: 'singleFile' }),
        headers: { 'Content-Type': 'application/json' },
        method: 'POST',
      }),
    ),
    fileSelectionSchema,
  );
  return selected.status === 'cancelled' ? undefined : selected.fileSelectionIds[0];
}

export async function chooseTransferPath(
  purpose: 'exportDestination' | 'importArchive' | 'importDestinationDirectory',
): Promise<string | undefined> {
  const selectionMode = purpose === 'importDestinationDirectory' ? 'directory' : 'singleFile';
  const selected = await requireJson(
    await withFileSelectionActivity(() =>
      hakamapFetch('/api/v1/file-selections', {
        body: JSON.stringify({ purpose, selectionMode }),
        headers: { 'Content-Type': 'application/json' },
        method: 'POST',
      }),
    ),
    fileSelectionSchema,
  );
  return selected.status === 'cancelled' ? undefined : selected.fileSelectionIds[0];
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

export async function getBackups(projectId: string): Promise<BackupList> {
  return requireJson(await hakamapFetch(`/api/v1/projects/${projectId}/backups`), backupListSchema);
}

export async function restoreBackup(
  projectId: string,
  expectedRevision: number,
  backupId: string,
  backupVersion: string,
): Promise<void> {
  await runOperation(
    await hakamapFetch(`/api/v1/projects/${projectId}/operations/backup-restore`, {
      body: JSON.stringify({
        backupId,
        backupVersion,
        confirmedNoUnsavedChanges: true,
        expectedRevision,
      }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
  );
}

export async function exportProject(
  projectId: string,
  expectedRevision: number,
  fileSelectionId: string,
): Promise<void> {
  await runOperation(
    await hakamapFetch(`/api/v1/projects/${projectId}/operations/export`, {
      body: JSON.stringify({ expectedRevision, fileSelectionId }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
  );
}

export async function importProject(
  fileSelectionId: string,
  destinationSelectionId: string,
): Promise<string> {
  const result = await runOperation(
    await hakamapFetch('/api/v1/catalog/operations/import', {
      body: JSON.stringify({ destinationSelectionId, fileSelectionId }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
  );
  if (result.projectId === null) throw new Error('operation-result-invalid');
  return result.projectId;
}

async function runOperation(response: Response): Promise<z.infer<typeof operationSchema>> {
  let operation = await requireJson(response, operationSchema);
  while (operation.status === 'queued' || operation.status === 'running') {
    await new Promise((resolve) => {
      window.setTimeout(resolve, 500);
    });
    operation = await requireJson(
      await hakamapFetch(`/api/v1/operations/${operation.operationId}`),
      operationSchema,
    );
  }
  if (operation.status !== 'succeeded') {
    throw new Error(operation.errorCode ?? 'operation-failed');
  }
  return operation;
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

export async function getBackgroundTileManifest(
  projectId: string,
): Promise<BackgroundTileManifest> {
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/background/tiles/manifest`),
    backgroundTileManifestSchema,
  );
}

export async function executeProjectCommand(
  projectId: string,
  expectedRevision: number,
  commandType: string,
  payload: unknown,
): Promise<z.infer<typeof commandExecutionResponseSchema>> {
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/commands`, {
      body: JSON.stringify({ commandType, expectedRevision, payload }),
      headers: { 'Content-Type': 'application/json' },
      method: 'POST',
    }),
    commandExecutionResponseSchema,
  );
}

export async function confirmProjectCommand(
  projectId: string,
  confirmationToken: string,
  expectedRevision: number,
): Promise<z.infer<typeof commandResponseSchema>> {
  return requireJson(
    await hakamapFetch(
      `/api/v1/projects/${projectId}/command-confirmations/${encodeURIComponent(confirmationToken)}`,
      {
        body: JSON.stringify({ expectedRevision }),
        headers: { 'Content-Type': 'application/json' },
        method: 'POST',
      },
    ),
    commandResponseSchema,
  );
}

export async function getGravePeople(
  projectId: string,
  graveId: string,
  cursor?: string,
): Promise<PeoplePage> {
  const query = cursor === undefined ? '' : `?cursor=${encodeURIComponent(cursor)}`;
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/graves/${graveId}/people${query}`),
    peoplePageSchema,
  );
}

export async function searchGraves(
  projectId: string,
  keyword: string,
  cursor?: string,
): Promise<GraveSearchPage> {
  const parameters = new URLSearchParams({ q: keyword });
  if (cursor !== undefined) parameters.set('cursor', cursor);
  return requireJson(
    await hakamapFetch(`/api/v1/projects/${projectId}/search?${parameters.toString()}`),
    graveSearchPageSchema,
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
