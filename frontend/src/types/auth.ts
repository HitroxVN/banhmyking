export type RoleName = 'CUSTOMER' | 'STAFF' | 'SHIPPER' | 'ADMIN';

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  phone?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserInfoResponse {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  role: RoleName;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

export interface ErrorResponse {
  success: boolean;
  message: string;
  errorCode?: string;
  errors?: Record<string, string>;
  timestamp?: string;
}

export interface AuthState {
  user: UserInfoResponse | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}
