import React, { useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import type { LoginRequest, RegisterRequest, UserInfoResponse } from '../types/auth';
import { tokenStorage } from '../utils/tokenStorage';
import { AuthContext } from './authContextDef';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserInfoResponse | null>(tokenStorage.getUserInfo());
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(tokenStorage.isAuthenticated());
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Khởi tạo và đồng bộ profile từ token khi tải ứng dụng
  useEffect(() => {
    const initAuth = async () => {
      const token = tokenStorage.getAccessToken();
      if (token) {
        try {
          const profile = await authApi.getMe();
          setUser(profile);
          setIsAuthenticated(true);
          tokenStorage.setUserInfo(profile);
        } catch (error) {
          console.error('Không thể lấy thông tin người dùng từ token hiện tại:', error);
          if (!tokenStorage.getAccessToken()) {
            setUser(null);
            setIsAuthenticated(false);
          }
        }
      } else {
        setUser(null);
        setIsAuthenticated(false);
      }
      setIsLoading(false);
    };

    initAuth();
  }, []);

  const login = async (data: LoginRequest) => {
    setIsLoading(true);
    try {
      const tokens = await authApi.login(data);
      tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
      const profile = await authApi.getMe();
      tokenStorage.setUserInfo(profile);
      setUser(profile);
      setIsAuthenticated(true);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (data: RegisterRequest) => {
    setIsLoading(true);
    try {
      const tokens = await authApi.register(data);
      tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
      const profile = await authApi.getMe();
      tokenStorage.setUserInfo(profile);
      setUser(profile);
      setIsAuthenticated(true);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      const refreshToken = tokenStorage.getRefreshToken();
      if (refreshToken) {
        await authApi.logout(refreshToken).catch((err) => {
          console.warn('Backend logout warning:', err);
        });
      }
    } finally {
      tokenStorage.clearAuth();
      setUser(null);
      setIsAuthenticated(false);
      setIsLoading(false);
    }
  };

  const refreshUserProfile = async () => {
    try {
      const profile = await authApi.getMe();
      setUser(profile);
      tokenStorage.setUserInfo(profile);
    } catch (e) {
      console.error('Failed to refresh user profile', e);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        isLoading,
        login,
        register,
        logout,
        refreshUserProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
