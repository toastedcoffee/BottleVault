import { createContext } from 'react';
import type { UserProfile, LoginRequest, RegisterRequest } from '../types/auth';

export interface AuthContextType {
  user: UserProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  updateUser: (profile: UserProfile) => void;
}

export const AuthContext = createContext<AuthContextType | null>(null);
