import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthProvider } from './AuthContext';
import { useAuth } from './useAuth';
import { authApi } from '../api/auth.api';
import type { UserProfile } from '../types/auth';

vi.mock('../api/auth.api', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  },
}));

const profile: UserProfile = {
  id: 'u1',
  email: 'user@example.com',
  displayName: 'Test User',
  defaultCurrency: 'USD',
  measurementUnit: 'ML',
};

const authResponse = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  user: profile,
};

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

function renderAuth() {
  return renderHook(() => useAuth(), { wrapper });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(authApi.logout).mockResolvedValue(undefined);
});

describe('session restore', () => {
  it('restores the user from sessionStorage on first render', () => {
    sessionStorage.setItem('accessToken', 'access-1');
    sessionStorage.setItem('user', JSON.stringify(profile));

    const { result } = renderAuth();

    expect(result.current.user).toEqual(profile);
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.isLoading).toBe(false);
  });

  it('starts logged out when no session is stored', () => {
    const { result } = renderAuth();

    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('starts logged out when a token exists but no saved user', () => {
    sessionStorage.setItem('accessToken', 'access-1');

    const { result } = renderAuth();

    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('drops the whole session when the saved user is corrupt JSON', () => {
    sessionStorage.setItem('accessToken', 'access-1');
    sessionStorage.setItem('refreshToken', 'refresh-1');
    sessionStorage.setItem('user', '{not valid json');

    const { result } = renderAuth();

    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('refreshToken')).toBeNull();
    expect(sessionStorage.getItem('user')).toBeNull();
  });
});

describe('login / register', () => {
  it('login stores the token pair and user, and sets state', async () => {
    vi.mocked(authApi.login).mockResolvedValue(authResponse);
    const { result } = renderAuth();

    await act(() => result.current.login({ email: 'user@example.com', password: 'pw' }));

    expect(authApi.login).toHaveBeenCalledWith({ email: 'user@example.com', password: 'pw' });
    expect(sessionStorage.getItem('accessToken')).toBe('access-1');
    expect(sessionStorage.getItem('refreshToken')).toBe('refresh-1');
    expect(JSON.parse(sessionStorage.getItem('user')!)).toEqual(profile);
    expect(result.current.user).toEqual(profile);
    expect(result.current.isAuthenticated).toBe(true);
  });

  it('login failure leaves storage and state untouched', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('bad credentials'));
    const { result } = renderAuth();

    await expect(
      act(() => result.current.login({ email: 'user@example.com', password: 'nope' }))
    ).rejects.toThrow('bad credentials');

    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(result.current.user).toBeNull();
  });

  it('register stores the token pair and user, and sets state', async () => {
    vi.mocked(authApi.register).mockResolvedValue(authResponse);
    const { result } = renderAuth();

    await act(() => result.current.register({ email: 'user@example.com', password: 'pw' }));

    expect(sessionStorage.getItem('accessToken')).toBe('access-1');
    expect(result.current.user).toEqual(profile);
  });
});

describe('logout', () => {
  it('revokes the server session and clears local state', async () => {
    vi.mocked(authApi.login).mockResolvedValue(authResponse);
    const { result } = renderAuth();
    await act(() => result.current.login({ email: 'user@example.com', password: 'pw' }));

    act(() => result.current.logout());

    expect(authApi.logout).toHaveBeenCalledWith('refresh-1');
    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('refreshToken')).toBeNull();
    expect(sessionStorage.getItem('user')).toBeNull();
    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('skips the revoke call when no refresh token is stored', () => {
    const { result } = renderAuth();

    act(() => result.current.logout());

    expect(authApi.logout).not.toHaveBeenCalled();
    expect(result.current.user).toBeNull();
  });

  it('still clears the local session when the revoke request fails', async () => {
    vi.mocked(authApi.login).mockResolvedValue(authResponse);
    vi.mocked(authApi.logout).mockRejectedValue(new Error('network down'));
    const { result } = renderAuth();
    await act(() => result.current.login({ email: 'user@example.com', password: 'pw' }));

    act(() => result.current.logout());

    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(result.current.user).toBeNull();
    // Flush the rejected revoke promise so it can't surface after the test.
    await act(async () => {});
  });
});

describe('updateUser', () => {
  it('updates both storage and state', async () => {
    vi.mocked(authApi.login).mockResolvedValue(authResponse);
    const { result } = renderAuth();
    await act(() => result.current.login({ email: 'user@example.com', password: 'pw' }));

    const updated: UserProfile = { ...profile, displayName: 'Renamed', defaultCurrency: 'EUR' };
    act(() => result.current.updateUser(updated));

    expect(JSON.parse(sessionStorage.getItem('user')!)).toEqual(updated);
    expect(result.current.user).toEqual(updated);
    // Tokens are untouched.
    expect(sessionStorage.getItem('accessToken')).toBe('access-1');
  });
});
