import { tokenStore } from '../session/tokenStore';
import { ORO_URL } from './config';
import { SignedIn } from './types';

/** an answer of Oro that is not a success: its status, its message (the server's, in plain words), when to retry */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }

  /** no answer at all: offline, Oro down */
  get offline(): boolean {
    return this.status === 0;
  }
}

/** the apps keep their refresh token themselves (Oro's accounts: X-Oro-Client native) */
export const CLIENT_HEADERS = { 'X-Oro-Client': 'native' } as const;

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
let signedOut: () => void = () => {};

/** what to do when the session cannot be renewed (signed out elsewhere, expired): back to the sign-in */
export function onSignedOut(handler: () => void): void {
  signedOut = handler;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/** the session renewed with the refresh token kept on the device; one renewal at a time */
export function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const token = await tokenStore.get();
      if (!token) {
        return false;
      }
      try {
        const res = await fetch(`${ORO_URL}/auth/refresh`, {
          method: 'POST',
          headers: { ...CLIENT_HEADERS, 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: token }),
        });
        if (!res.ok) {
          if (res.status === 401) {
            await tokenStore.clear();
          }
          return false;
        }
        const signedIn = (await res.json()) as SignedIn;
        accessToken = signedIn.accessToken;
        if (signedIn.refreshToken) {
          await tokenStore.set(signedIn.refreshToken);
        }
        return true;
      } catch {
        return false;
      }
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  form?: FormData;
  query?: Record<string, string | number | undefined>;
  /** false: a public route (the accounts) */
  auth?: boolean;
  signal?: AbortSignal;
}

function url(path: string, query?: RequestOptions['query']): string {
  const params = Object.entries(query ?? {})
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return `${ORO_URL}${path}${params.length ? `?${params.join('&')}` : ''}`;
}

async function errorOf(res: Response): Promise<ApiError> {
  let message = 'Something went wrong. Try again.';
  try {
    const body = (await res.json()) as { message?: string };
    if (body?.message) {
      message = body.message;
    }
  } catch {
    // no body (a stream refused, a proxy)
  }
  const retry = res.headers.get('Retry-After');
  return new ApiError(res.status, message, retry ? Number(retry) : undefined);
}

/** a call of Oro: its token, renewed once when it expired; the JSON of the answer, or an ApiError */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, form, query, auth = true, signal } = options;
  const send = () => {
    const headers: Record<string, string> = { ...CLIENT_HEADERS, Accept: 'application/json' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (auth && accessToken) {
      headers.Authorization = `Bearer ${accessToken}`;
    }
    return fetch(url(path, query), {
      method,
      headers,
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
      signal,
    });
  };
  let res: Response;
  try {
    res = await send();
    if (res.status === 401 && auth) {
      if (await refreshSession()) {
        res = await send();
      } else {
        signedOut();
        throw new ApiError(401, 'Your session has ended. Sign in again.');
      }
    }
  } catch (e) {
    if (e instanceof ApiError) {
      throw e;
    }
    if ((e as Error)?.name === 'AbortError') {
      throw e;
    }
    throw new ApiError(0, 'No internet connection');
  }
  if (!res.ok) {
    throw await errorOf(res);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export { url as apiUrl };
