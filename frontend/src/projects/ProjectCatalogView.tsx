import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  chooseProjectDirectory,
  chooseTransferPath,
  createProject,
  getProjectCatalog,
  importProject,
  openProject,
  permanentlyDeleteProject,
  registerProject,
  relinkProject,
  resolveRecovery,
  restoreProject,
  setDefaultProject,
  trashProject,
  type CatalogProject,
  unregisterProject,
} from '../api/hakamapClient';

type ProjectCatalogViewProps = {
  onOpened: (projectId: string) => void;
};

function ProjectCatalogView({ onOpened }: ProjectCatalogViewProps) {
  const queryClient = useQueryClient();
  const catalog = useQuery({ queryFn: getProjectCatalog, queryKey: ['projectCatalog'] });
  const [createOpen, setCreateOpen] = useState(false);
  const [projectName, setProjectName] = useState('');
  const [error, setError] = useState<string>();
  const [recoveryProjectId, setRecoveryProjectId] = useState<string>();
  const defaultOpenAttempted = useRef(false);

  const refresh = async () => queryClient.invalidateQueries({ queryKey: ['projectCatalog'] });
  const operation = useMutation({
    mutationFn: async (action: () => Promise<unknown>) => action(),
    onError: () => setError('操作を完了できませんでした。保存場所と入力内容を確認してください。'),
    onSuccess: refresh,
  });

  const openAndResolveRecovery = useCallback(
    async (projectId: string) => {
      const opened = await openProject(projectId);
      if (opened.recoveryCandidate === null) {
        onOpened(projectId);
        return;
      }
      setRecoveryProjectId(projectId);
    },
    [onOpened],
  );

  const selectAndCreate = async () => {
    const selected = await chooseProjectDirectory('projectCreateDirectory');
    if (selected === undefined) {
      return;
    }
    await createProject(projectName, selected.id);
    setCreateOpen(false);
    setProjectName('');
  };

  const selectAndRegister = async () => {
    const selected = await chooseProjectDirectory('projectRelinkDirectory');
    if (selected !== undefined) {
      await registerProject(selected.id);
    }
  };

  const selectAndImport = async () => {
    const archive = await chooseTransferPath('importArchive');
    if (archive === undefined) return;
    const destination = await chooseTransferPath('importDestinationDirectory');
    if (destination === undefined) return;
    const projectId = await importProject(archive, destination);
    onOpened(projectId);
  };

  const activeProjects =
    catalog.data?.projects.filter((project) => project.state === 'active') ?? [];
  const trashedProjects =
    catalog.data?.projects.filter((project) => project.state === 'trashed') ?? [];

  useEffect(() => {
    if (defaultOpenAttempted.current || catalog.data === undefined) {
      return;
    }
    defaultOpenAttempted.current = true;
    const defaultProject = catalog.data.projects.find(
      (project) => project.defaultProject && project.available && project.state === 'active',
    );
    if (defaultProject !== undefined && catalog.data.openProjectId === null) {
      operation.mutate(async () => openAndResolveRecovery(defaultProject.projectId));
    }
  }, [catalog.data, onOpened, openAndResolveRecovery, operation]);

  const selectAndRelink = async (projectId: string) => {
    const selected = await chooseProjectDirectory('projectRelinkDirectory');
    if (selected !== undefined) {
      await relinkProject(projectId, selected.id);
    }
  };

  const selectAndRestore = async (projectId: string) => {
    const selected = await chooseProjectDirectory('trashRestoreDirectory');
    if (selected !== undefined) {
      await restoreProject(projectId, selected.id);
    }
  };

  return (
    <Box className="project-catalog" component="main">
      <Stack
        direction={{ sm: 'row', xs: 'column' }}
        spacing={2}
        sx={{ justifyContent: 'space-between' }}
      >
        <Box>
          <Typography component="h2" variant="h2">
            プロジェクト
          </Typography>
          <Typography color="text.secondary">
            墓地ごとのデータを作成するか、既存の保存場所を追加します。
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            onClick={() => {
              void operation.mutateAsync(selectAndImport);
            }}
            variant="outlined"
          >
            インポート
          </Button>
          <Button
            onClick={() => {
              void operation.mutateAsync(selectAndRegister);
            }}
            variant="outlined"
          >
            既存プロジェクトを追加
          </Button>
          <Button onClick={() => setCreateOpen(true)} variant="contained">
            新規作成
          </Button>
        </Stack>
      </Stack>

      {error === undefined ? null : <Alert severity="error">{error}</Alert>}
      {catalog.isPending ? <CircularProgress aria-label="プロジェクトを読み込み中" /> : null}
      {catalog.isError ? (
        <Alert severity="error">プロジェクト一覧を読み込めませんでした。</Alert>
      ) : null}

      <Stack spacing={2}>
        {activeProjects.length === 0 && !catalog.isPending ? (
          <Alert severity="info">プロジェクトはまだありません。</Alert>
        ) : null}
        {activeProjects.map((project) => (
          <ProjectCard
            key={project.projectId}
            busy={operation.isPending}
            onDefault={() => operation.mutateAsync(() => setDefaultProject(project.projectId))}
            onOpen={() => operation.mutateAsync(() => openAndResolveRecovery(project.projectId))}
            onRelink={() => operation.mutateAsync(() => selectAndRelink(project.projectId))}
            onTrash={() => operation.mutateAsync(() => trashProject(project.projectId))}
            onUnregister={() => operation.mutateAsync(() => unregisterProject(project.projectId))}
            project={project}
          />
        ))}
      </Stack>

      {trashedProjects.length > 0 ? (
        <Stack spacing={2}>
          <Typography component="h3" variant="h2">
            ごみ箱
          </Typography>
          {trashedProjects.map((project) => (
            <ProjectCard
              key={project.projectId}
              busy={operation.isPending}
              onDelete={() =>
                operation.mutateAsync(() => permanentlyDeleteProject(project.projectId))
              }
              onRestore={() => operation.mutateAsync(() => restoreProject(project.projectId))}
              onRestoreElsewhere={() =>
                operation.mutateAsync(() => selectAndRestore(project.projectId))
              }
              project={project}
            />
          ))}
        </Stack>
      ) : null}

      <Dialog fullWidth onClose={() => setCreateOpen(false)} open={createOpen}>
        <DialogTitle>プロジェクトを新規作成</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label="プロジェクト名"
            margin="dense"
            onChange={(event) => setProjectName(event.target.value)}
            required
            value={projectName}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>キャンセル</Button>
          <Button
            disabled={projectName.trim().length === 0 || operation.isPending}
            onClick={() => {
              void operation.mutateAsync(selectAndCreate);
            }}
            variant="contained"
          >
            保存場所を選んで作成
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={recoveryProjectId !== undefined}>
        <DialogTitle>未保存の編集を復旧しますか</DialogTitle>
        <DialogContent>
          <Typography>
            前回の未保存編集が見つかりました。復旧すると、内容を確認してから手動保存できます。
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button
            disabled={operation.isPending}
            onClick={() => {
              if (recoveryProjectId === undefined) {
                return;
              }
              void operation
                .mutateAsync(() => resolveRecovery(recoveryProjectId, 'discard'))
                .then(() => {
                  setRecoveryProjectId(undefined);
                  onOpened(recoveryProjectId);
                });
            }}
          >
            破棄して開く
          </Button>
          <Button
            disabled={operation.isPending}
            onClick={() => {
              if (recoveryProjectId === undefined) {
                return;
              }
              void operation
                .mutateAsync(() => resolveRecovery(recoveryProjectId, 'apply'))
                .then(() => {
                  setRecoveryProjectId(undefined);
                  onOpened(recoveryProjectId);
                });
            }}
            variant="contained"
          >
            復旧する
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

type ProjectCardProps = {
  project: CatalogProject;
  busy: boolean;
  onOpen?: () => Promise<unknown>;
  onDefault?: () => Promise<unknown>;
  onTrash?: () => Promise<unknown>;
  onRelink?: () => Promise<unknown>;
  onUnregister?: () => Promise<unknown>;
  onRestore?: () => Promise<unknown>;
  onRestoreElsewhere?: () => Promise<unknown>;
  onDelete?: () => Promise<unknown>;
};

function ProjectCard({
  project,
  busy,
  onOpen,
  onDefault,
  onTrash,
  onRelink,
  onUnregister,
  onRestore,
  onRestoreElsewhere,
  onDelete,
}: ProjectCardProps) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography component="h3" variant="h2">
          {project.name}
          {project.defaultProject ? '（デフォルト）' : ''}
        </Typography>
        <Typography color="text.secondary">保存場所: {project.locationLabel}</Typography>
        {project.recoveryCandidate ? (
          <Typography color="warning.main">未保存の復旧候補があります</Typography>
        ) : null}
        <Typography color={project.available ? 'text.secondary' : 'error'}>
          {project.available
            ? `更新: ${new Date(project.updatedAt).toLocaleString()}`
            : '保存場所を確認できません'}
        </Typography>
      </CardContent>
      <CardActions>
        {onOpen === undefined ? null : (
          <Button disabled={busy || !project.available} onClick={() => void onOpen()}>
            開く
          </Button>
        )}
        {onDefault === undefined ? null : (
          <Button disabled={busy || !project.available} onClick={() => void onDefault()}>
            デフォルトにする
          </Button>
        )}
        {onTrash === undefined ? null : (
          <Button color="error" disabled={busy} onClick={() => void onTrash()}>
            ごみ箱へ移動
          </Button>
        )}
        {onRelink === undefined ? null : (
          <Button disabled={busy} onClick={() => void onRelink()}>
            保存場所を再設定
          </Button>
        )}
        {onUnregister === undefined ? null : (
          <Button disabled={busy} onClick={() => void onUnregister()}>
            一覧から外す
          </Button>
        )}
        {onRestore === undefined ? null : (
          <Button disabled={busy} onClick={() => void onRestore()}>
            元の場所へ復元
          </Button>
        )}
        {onRestoreElsewhere === undefined ? null : (
          <Button disabled={busy} onClick={() => void onRestoreElsewhere()}>
            別の場所へ復元
          </Button>
        )}
        {onDelete === undefined ? null : (
          <Button color="error" disabled={busy} onClick={() => void onDelete()}>
            完全削除
          </Button>
        )}
      </CardActions>
    </Card>
  );
}

export default ProjectCatalogView;
