import axios, { type AxiosError } from 'axios';
import { markAvailable, markUnavailable } from '../lib/serviceState';

const apiClient = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

// Request interceptor: attach JWT token
apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Refresh tokens are single-use (the server rotates them), so concurrent 401s
// must share ONE refresh request. Whichever request hits 401 first starts the
// refresh; the rest await the same promise and retry with the new token.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(refreshToken: string): Promise<string> {
  // Plain axios, not apiClient: avoids re-entering these interceptors.
  const { data } = await axios.post('/api/auth/refresh', { refreshToken });
  sessionStorage.setItem('accessToken', data.accessToken);
  sessionStorage.setItem('refreshToken', data.refreshToken);
  return data.accessToken;
}

// 502/503/504 all mean "the API isn't there," and all three genuinely occur:
// nginx emits a raw 502 while the API boots (LAN access has no Worker in front
// of it), and the edge Worker emits a structured 503 during maintenance.
// Treating them uniformly also keeps them off the HTML-detection path below,
// which is what stops nginx's HTML 502 page from triggering a reload loop.
export const UNAVAILABLE_STATUSES: readonly number[] = [502, 503, 504];

function isUnavailable(error: AxiosError): boolean {
  // Narrow before calling includes(): with a readonly number[] and a possibly
  // undefined status, `includes(error.response?.status)` is a type error the
  // moment anyone annotates this handler's parameter.
  if (error.response === undefined) return false;
  const { status, headers } = error.response;
  if (UNAVAILABLE_STATUSES.includes(status)) return true;

  // Any other 5xx with an HTML body is a server or edge failure that the
  // maintenance Worker (edge/maintenance-worker) didn't get a chance to
  // normalize to 503 -- e.g. a raw Cloudflare edge error (521-530, including
  // 1033/"tunnel not running" on a cold boot) reaching the browser directly
  // because the Worker isn't deployed in front of the tunnel yet, or simply
  // isn't in the request path. Deliberately excludes JSON 5xx: a JSON error
  // means the app itself answered and is reporting a real bug, which the
  // "back shortly" banner must not paper over. Checking the HTML-ness
  // instead of hardcoding Cloudflare's status list keeps this from silently
  // drifting out of sync with that list again.
  if (status >= 500) {
    return String(headers?.['content-type'] ?? '').includes('text/html');
  }
  return false;
}

function retryAfterSeconds(error: AxiosError): number | null {
  const raw = Number(error.response?.headers?.['retry-after']);
  return Number.isFinite(raw) && raw > 0 ? raw : null;
}

// A full navigation is what triggers Cloudflare Access's login flow properly —
// an XHR can't, because the redirect target is cross-origin. The guard exists
// because any bug that makes this condition sticky would otherwise reload
// forever; 15s is long enough to break a loop, short enough to stay invisible.
const ACCESS_RELOAD_KEY = 'bv.accessReloadAt';
const ACCESS_RELOAD_GUARD_MS = 15_000;

function looksLikeAccessWall(error: AxiosError): boolean {
  // No response object at all: the browser followed Access's 302 to a
  // cross-origin host that sends no CORS headers for us, so the XHR failed
  // before any body was readable. This is the common symptom, not the HTML one.
  if (!error.response) return true;
  // A Cloudflare Access session wall is a login redirect; it never carries a
  // 5xx. A 5xx -- HTML or not -- is a server or edge failure instead, and
  // isUnavailable (checked first, above) is what decides whether that
  // specific failure gets the "back shortly" banner. Without this guard, an
  // HTML 5xx that isUnavailable doesn't recognize would land here and
  // reload the user out of the SPA mid-outage -- the opposite of what the
  // banner exists to prevent.
  if (error.response.status >= 500) return false;
  const contentType = String(error.response.headers?.['content-type'] ?? '');
  return contentType.includes('text/html');
}

function recoverFromAccessWall(): void {
  const last = Number(sessionStorage.getItem(ACCESS_RELOAD_KEY) ?? 0);
  if (Date.now() - last < ACCESS_RELOAD_GUARD_MS) return;
  sessionStorage.setItem(ACCESS_RELOAD_KEY, String(Date.now()));
  window.location.reload();
}

// Response interceptor: handle 401 by attempting token refresh
apiClient.interceptors.response.use(
  (response) => {
    markAvailable();
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    if (isUnavailable(error)) {
      markUnavailable(retryAfterSeconds(error));
      return Promise.reject(error);
    }

    if (looksLikeAccessWall(error)) {
      recoverFromAccessWall();
      return Promise.reject(error);
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      const refreshToken = sessionStorage.getItem('refreshToken');

      if (refreshToken) {
        try {
          refreshPromise ??= refreshAccessToken(refreshToken);
          const accessToken = await refreshPromise;
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          return apiClient(originalRequest);
        } catch {
          // Refresh failed (expired, revoked, or already used) — session is over.
          sessionStorage.clear();
          window.location.href = '/login';
        } finally {
          refreshPromise = null;
        }
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
