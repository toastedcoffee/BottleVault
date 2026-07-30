// @vitest-environment happy-dom
// @vitest-environment-options {"happyDOM": {"url": "http://localhost:3000/"}}
//
// happy-dom instead of the suite-default jsdom: asserting the refresh-failure
// redirect requires observing a write to window.location, which jsdom forbids
// (Location is spec-unforgeable). happy-dom's detached window records the
// navigation as a plain URL change we can read back.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios, {
  AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import apiClient from './client';
import { getServiceState, markAvailable } from '../lib/serviceState';

// Replace the transport layer: every request apiClient dispatches lands in
// `handler` instead of the network. Rejections must be AxiosErrors carrying
// config + response, exactly like axios's built-in adapters produce.
let handler: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;
let adapterCalls: InternalAxiosRequestConfig[] = [];

apiClient.defaults.adapter = (config) => {
  adapterCalls.push(config);
  return handler(config);
};

function ok(config: InternalAxiosRequestConfig, data: unknown = {}): Promise<AxiosResponse> {
  return Promise.resolve({ data, status: 200, statusText: 'OK', headers: {}, config });
}

function httpError(config: InternalAxiosRequestConfig, status: number): Promise<AxiosResponse> {
  const response = { data: {}, status, statusText: '', headers: {}, config } as AxiosResponse;
  return Promise.reject(
    new AxiosError(`Request failed with status code ${status}`, AxiosError.ERR_BAD_REQUEST, config, null, response)
  );
}

// 401 on the first attempt of each request, success on the retry.
function failOnceWith401() {
  const seen = new Set<string>();
  handler = (config) => {
    const key = `${config.method} ${config.url}`;
    if (!seen.has(key)) {
      seen.add(key);
      return httpError(config, 401);
    }
    return ok(config, { retried: true });
  };
}

function seedSession(accessToken = 'stale-access', refreshToken = 'refresh-1') {
  sessionStorage.setItem('accessToken', accessToken);
  sessionStorage.setItem('refreshToken', refreshToken);
}

function mockRefreshSuccess(accessToken = 'new-access', refreshToken = 'new-refresh') {
  return vi
    .spyOn(axios, 'post')
    .mockResolvedValue({ data: { accessToken, refreshToken } });
}

beforeEach(() => {
  adapterCalls = [];
  handler = (config) => ok(config);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('request interceptor', () => {
  it('attaches the stored access token as a Bearer header', async () => {
    seedSession('token-abc');

    await apiClient.get('/bottles');

    expect(adapterCalls[0]!.headers.Authorization).toBe('Bearer token-abc');
  });

  it('sends no Authorization header when no token is stored', async () => {
    await apiClient.get('/bottles');

    expect(adapterCalls[0]!.headers.Authorization).toBeUndefined();
  });
});

describe('401 refresh flow', () => {
  it('refreshes the token, stores the rotated pair, and retries the original request', async () => {
    seedSession();
    failOnceWith401();
    const postSpy = mockRefreshSuccess();

    const response = await apiClient.get('/bottles');

    // Exactly one refresh call, against the auth endpoint, with the stored token.
    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(postSpy).toHaveBeenCalledWith('/api/auth/refresh', { refreshToken: 'refresh-1' });

    // Rotated pair persisted for subsequent requests.
    expect(sessionStorage.getItem('accessToken')).toBe('new-access');
    expect(sessionStorage.getItem('refreshToken')).toBe('new-refresh');

    // Original request was retried with the new token and its result returned.
    expect(adapterCalls).toHaveLength(2);
    expect(adapterCalls[1]!.url).toBe('/bottles');
    expect(adapterCalls[1]!.headers.Authorization).toBe('Bearer new-access');
    expect(response.data).toEqual({ retried: true });
  });

  it('shares a single refresh across concurrent 401s', async () => {
    seedSession();
    failOnceWith401();

    // Hold the refresh open until both failed requests have subscribed to it.
    let resolveRefresh!: (value: { data: { accessToken: string; refreshToken: string } }) => void;
    const postSpy = vi
      .spyOn(axios, 'post')
      .mockImplementation(() => new Promise((resolve) => { resolveRefresh = resolve; }));

    const results = Promise.all([apiClient.get('/bottles'), apiClient.get('/stats')]);

    // A macrotask boundary guarantees both 401 handlers have run up to their
    // `await refreshPromise` before the refresh resolves.
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(postSpy).toHaveBeenCalledTimes(1);

    resolveRefresh({ data: { accessToken: 'new-access', refreshToken: 'new-refresh' } });
    const [bottles, stats] = await results;

    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(bottles.data).toEqual({ retried: true });
    expect(stats.data).toEqual({ retried: true });
    // 2 original attempts + 2 retries, all retried with the refreshed token.
    expect(adapterCalls).toHaveLength(4);
    expect(adapterCalls[2]!.headers.Authorization).toBe('Bearer new-access');
    expect(adapterCalls[3]!.headers.Authorization).toBe('Bearer new-access');
  });

  it('clears the session and redirects to /login when the refresh itself fails', async () => {
    seedSession();
    failOnceWith401();
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh token revoked'));

    await expect(apiClient.get('/bottles')).rejects.toMatchObject({
      response: { status: 401 },
    });

    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('refreshToken')).toBeNull();
    expect(window.location.href).toBe('http://localhost:3000/login');
    // No retry after a failed refresh.
    expect(adapterCalls).toHaveLength(1);
  });

  it('rejects without attempting a refresh when no refresh token is stored', async () => {
    sessionStorage.setItem('accessToken', 'stale-access');
    failOnceWith401();
    const postSpy = vi.spyOn(axios, 'post');

    await expect(apiClient.get('/bottles')).rejects.toMatchObject({
      response: { status: 401 },
    });

    expect(postSpy).not.toHaveBeenCalled();
    expect(adapterCalls).toHaveLength(1);
  });

  it('refreshes only once when the retried request also 401s', async () => {
    seedSession();
    handler = (config) => httpError(config, 401);
    const postSpy = mockRefreshSuccess();

    await expect(apiClient.get('/bottles')).rejects.toMatchObject({
      response: { status: 401 },
    });

    // One refresh, one retry, then give up — no infinite 401/refresh loop.
    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(adapterCalls).toHaveLength(2);
  });
});

describe('non-401 errors', () => {
  // Regression guard for the PR #89 incident: the backend returned 403 for
  // expired tokens and the client (correctly) never refreshed, stranding the
  // session. The client's contract is 401-only — these pin that both sides
  // of the contract stay visible.
  it.each([400, 403, 500])('passes a %i through without refresh or retry', async (status) => {
    seedSession();
    handler = (config) => httpError(config, status);
    const postSpy = vi.spyOn(axios, 'post');

    await expect(apiClient.get('/bottles')).rejects.toMatchObject({
      response: { status },
    });

    expect(postSpy).not.toHaveBeenCalled();
    expect(adapterCalls).toHaveLength(1);
    // Session untouched.
    expect(sessionStorage.getItem('accessToken')).toBe('stale-access');
    expect(sessionStorage.getItem('refreshToken')).toBe('refresh-1');
  });
});

describe('degraded service detection', () => {
  beforeEach(() => {
    markAvailable();
  });

  it.each([502, 503, 504])('marks the service unavailable on a %i', async (status) => {
    handler = (config) => httpError(config, status);

    await expect(apiClient.get('/bottles')).rejects.toMatchObject({ response: { status } });

    expect(getServiceState().unavailable).toBe(true);
  });

  it('captures Retry-After as the countdown hint', async () => {
    handler = (config) =>
      Promise.reject(
        new AxiosError('Service Unavailable', AxiosError.ERR_BAD_RESPONSE, config, null, {
          data: {}, status: 503, statusText: '', headers: { 'retry-after': '900' }, config,
        } as AxiosResponse)
      );

    await expect(apiClient.get('/bottles')).rejects.toMatchObject({ response: { status: 503 } });

    expect(getServiceState().retryAfterSeconds).toBe(900);
  });

  it('leaves the hint null when Retry-After is absent or unparseable', async () => {
    handler = (config) => httpError(config, 503);

    await expect(apiClient.get('/bottles')).rejects.toBeInstanceOf(AxiosError);

    expect(getServiceState().retryAfterSeconds).toBeNull();
  });

  it('clears the unavailable state on the next successful response', async () => {
    handler = (config) => httpError(config, 503);
    await expect(apiClient.get('/bottles')).rejects.toBeInstanceOf(AxiosError);
    expect(getServiceState().unavailable).toBe(true);

    handler = (config) => ok(config);
    await apiClient.get('/bottles');

    expect(getServiceState().unavailable).toBe(false);
  });

  it('does not mark unavailable on a 500', async () => {
    // A 500 is a bug in a running API, not an absent one. Showing "back shortly"
    // for an application error would be a lie.
    handler = (config) => httpError(config, 500);

    await expect(apiClient.get('/bottles')).rejects.toMatchObject({ response: { status: 500 } });

    expect(getServiceState().unavailable).toBe(false);
  });
});

describe('Cloudflare Access session recovery', () => {
  const RELOAD_KEY = 'bv.accessReloadAt';

  beforeEach(() => {
    sessionStorage.removeItem(RELOAD_KEY);
    markAvailable();
  });

  function networkError(config: InternalAxiosRequestConfig): Promise<AxiosResponse> {
    // No `response` — what a cross-origin Access redirect actually produces.
    return Promise.reject(new AxiosError('Network Error', AxiosError.ERR_NETWORK, config, null));
  }

  it('reloads when a request fails with no response at all', async () => {
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => {});
    handler = networkError;

    await expect(apiClient.get('/bottles')).rejects.toBeInstanceOf(AxiosError);

    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('reloads when the API answers with HTML instead of JSON', async () => {
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => {});
    handler = (config) =>
      Promise.reject(
        new AxiosError('Unauthorized', AxiosError.ERR_BAD_REQUEST, config, null, {
          data: '<html><body>Sign in</body></html>',
          status: 401,
          statusText: '',
          headers: { 'content-type': 'text/html; charset=utf-8' },
          config,
        } as AxiosResponse)
      );

    await expect(apiClient.get('/bottles')).rejects.toBeInstanceOf(AxiosError);

    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('reloads at most once per guard window', async () => {
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => {});
    handler = networkError;

    await expect(apiClient.get('/bottles')).rejects.toBeInstanceOf(AxiosError);
    await expect(apiClient.get('/stats')).rejects.toBeInstanceOf(AxiosError);

    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('does not reload on an HTML 502 from nginx', async () => {
    // The reload-loop hazard: a 502 while the API boots is an HTML page. It must
    // route to the maintenance banner, never to a navigation.
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => {});
    handler = (config) =>
      Promise.reject(
        new AxiosError('Bad Gateway', AxiosError.ERR_BAD_RESPONSE, config, null, {
          data: '<html><body>502 Bad Gateway</body></html>',
          status: 502,
          statusText: '',
          headers: { 'content-type': 'text/html' },
          config,
        } as AxiosResponse)
      );

    await expect(apiClient.get('/bottles')).rejects.toBeInstanceOf(AxiosError);

    expect(reload).not.toHaveBeenCalled();
    expect(getServiceState().unavailable).toBe(true);
  });

  it('marks unavailable and does not reload on an HTML 530 (Cloudflare error 1033)', async () => {
    // Reproduces the pre-Worker cold-boot failure: a tunnel-down origin returns
    // a raw Cloudflare edge error page, not one of the codes
    // edge/maintenance-worker normalizes to 503. This isn't in
    // UNAVAILABLE_STATUSES, so it must be caught by the general "5xx with an
    // HTML body" rule, or looksLikeAccessWall would navigate the user out of
    // the SPA during a genuine outage.
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => {});
    handler = (config) =>
      Promise.reject(
        new AxiosError('Origin Down', AxiosError.ERR_BAD_RESPONSE, config, null, {
          data: '<html><body>Error 1033</body></html>',
          status: 530,
          statusText: '',
          headers: { 'content-type': 'text/html' },
          config,
        } as AxiosResponse)
      );

    await expect(apiClient.get('/bottles')).rejects.toBeInstanceOf(AxiosError);

    expect(reload).not.toHaveBeenCalled();
    expect(getServiceState().unavailable).toBe(true);
  });

  it('does not reload on a normal JSON 401, leaving the refresh flow alone', async () => {
    const reload = vi.spyOn(window.location, 'reload').mockImplementation(() => {});
    seedSession();
    failOnceWith401();
    mockRefreshSuccess();

    await apiClient.get('/bottles');

    expect(reload).not.toHaveBeenCalled();
  });
});
