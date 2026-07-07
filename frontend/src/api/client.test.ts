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
