import { z } from 'zod';

const sessionStatusSchema = z.object({
  authenticated: z.literal(true),
});

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

export function clearSessionForTest(): void {
  csrfToken = undefined;
}
