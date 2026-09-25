import { createContext } from 'react';
import type { LoginRequest, RegisterRequest, UserInfoResponse } from '../types/auth';

export interface AuthContextType {
  user: UserInfoResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUserProfile: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
