import { useState, useCallback, type ReactNode } from 'react';
import { authApi } from '../api/auth.api';
import type { UserProfile, LoginRequest, RegisterRequest } from '../types/auth';
import { AuthContext } from './auth-context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(() => {
    // Restore an existing session synchronously on first render, so there's no
    // flash of logged-out UI and no setState-in-effect on mount.
    const token = sessionStorage.getItem('accessToken');
    const savedUser = sessionStorage.getItem('user');
    if (token && savedUser) {
      try {
        return JSON.parse(savedUser) as UserProfile;
      } catch {
        // Corrupt stored user — drop the whole session.
        sessionStorage.clear();
      }
    }
    return null;
  });
  // The session is read synchronously above, so there is no async bootstrap to
  // wait on — auth state is already known on the first render. Kept in the
  // context shape (always false) so consumers/route guards don't need changes.
  const [isLoading] = useState(false);

  const handleAuthResponse = useCallback((accessToken: string, refreshToken: string, userProfile: UserProfile) => {
    sessionStorage.setItem('accessToken', accessToken);
    sessionStorage.setItem('refreshToken', refreshToken);
    sessionStorage.setItem('user', JSON.stringify(userProfile));
    setUser(userProfile);
  }, []);

  const login = useCallback(async (data: LoginRequest) => {
    const response = await authApi.login(data);
    handleAuthResponse(response.accessToken, response.refreshToken, response.user);
  }, [handleAuthResponse]);

  const register = useCallback(async (data: RegisterRequest) => {
    const response = await authApi.register(data);
    handleAuthResponse(response.accessToken, response.refreshToken, response.user);
  }, [handleAuthResponse]);

  const logout = useCallback(() => {
    // Revoke the server-side session, but don't block the UI on it — the
    // local session is cleared regardless, and the token expires server-side
    // within 7 days even if this request never arrives.
    const refreshToken = sessionStorage.getItem('refreshToken');
    if (refreshToken) {
      void authApi.logout(refreshToken).catch(() => {});
    }
    sessionStorage.clear();
    setUser(null);
  }, []);

  const updateUser = useCallback((profile: UserProfile) => {
    sessionStorage.setItem('user', JSON.stringify(profile));
    setUser(profile);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, register, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
}
