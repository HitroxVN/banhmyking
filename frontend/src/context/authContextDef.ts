import { createContext } from 'react';
import type { LoginRequest, RegisterRequest, UserInfoResponse } from '../types/auth';

export interface AuthContextType {
  user: UserInfoResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  /** Trả về message của backend; tài khoản mới chưa xác thực email nên KHÔNG tự đăng nhập. */
  register: (data: RegisterRequest) => Promise<string>;
  logout: () => Promise<void>;
  refreshUserProfile: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
