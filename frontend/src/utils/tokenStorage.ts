import type { UserInfoResponse } from '../types/auth';

const ACCESS_TOKEN_KEY = 'bmk_access_token';
const REFRESH_TOKEN_KEY = 'bmk_refresh_token';
const USER_INFO_KEY = 'bmk_user_info';

export const tokenStorage = {
  getAccessToken(): string | null {
    try {
      return localStorage.getItem(ACCESS_TOKEN_KEY);
    } catch {
      return null;
    }
  },

  getRefreshToken(): string | null {
    try {
      return localStorage.getItem(REFRESH_TOKEN_KEY);
    } catch {
      return null;
    }
  },

  setTokens(accessToken: string, refreshToken?: string): void {
    try {
      localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
      if (refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
      }
    } catch (e) {
      console.error('Failed to save tokens to localStorage', e);
    }
  },

  getUserInfo(): UserInfoResponse | null {
    try {
      const data = localStorage.getItem(USER_INFO_KEY);
      return data ? JSON.parse(data) : null;
    } catch {
      return null;
    }
  },

  setUserInfo(user: UserInfoResponse): void {
    try {
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(user));
    } catch (e) {
      console.error('Failed to save user info to localStorage', e);
    }
  },

  clearAuth(): void {
    try {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      localStorage.removeItem(USER_INFO_KEY);
    } catch (e) {
      console.error('Failed to clear localStorage', e);
    }
  },

  isAuthenticated(): boolean {
    return Boolean(this.getAccessToken());
  },
};
