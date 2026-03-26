import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import { authApi } from '../api/auth.api';
import type { UserProfile, LoginRequest, RegisterRequest } from '../types/auth';

interface AuthContextType {
  user: UserProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Check for existing session on mount
    const token = sessionStorage.getItem('accessToken');
    const savedUser = sessionStorage.getItem('user');
    if (token && savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch {
        sessionStorage.clear();
      }
    }
    setIsLoading(false);
  }, []);

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
    sessionStorage.clear();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
