import axios, { AxiosError } from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse, ErrorResponse, TokenResponse } from '../types/auth';
import { tokenStorage } from '../utils/tokenStorage';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export const axiosClient = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

// Request Interceptor: Tự động đính kèm header Authorization: Bearer <token>
axiosClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = tokenStorage.getAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// Quản lý refresh token đồng thời tránh race condition
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value?: unknown) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: AxiosError | null, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// Response Interceptor: Bắt lỗi 401 và xử lý làm mới token (Refresh Token Flow)
axiosClient.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error: AxiosError<ErrorResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Bỏ qua nếu lỗi không phải 401 hoặc request là đăng nhập/đăng ký/refresh
    if (!error.response || error.response.status !== 401 || originalRequest?.url?.includes('/auth/login') || originalRequest?.url?.includes('/auth/register')) {
      return Promise.reject(extractErrorMessage(error));
    }

    // Nếu endpoint refresh bị 401 -> token hết hạn hoàn toàn -> Logout
    if (originalRequest?.url?.includes('/auth/refresh')) {
      tokenStorage.clearAuth();
      if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
        window.location.href = '/login?expired=true';
      }
      return Promise.reject(extractErrorMessage(error));
    }

    if (originalRequest && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return axiosClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = tokenStorage.getRefreshToken();
      if (!refreshToken) {
        tokenStorage.clearAuth();
        if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
          window.location.href = '/login?session=ended';
        }
        return Promise.reject(new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'));
      }

      try {
        const response = await axios.post<ApiResponse<TokenResponse>>(
          `${BASE_URL}/auth/refresh`,
          { refreshToken },
          { headers: { 'Content-Type': 'application/json' } }
        );

        const newTokens = response.data.data;
        tokenStorage.setTokens(newTokens.accessToken, newTokens.refreshToken);

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newTokens.accessToken}`;
        }

        processQueue(null, newTokens.accessToken);
        return axiosClient(originalRequest);
      } catch (refreshErr) {
        processQueue(refreshErr as AxiosError, null);
        tokenStorage.clearAuth();
        if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
          window.location.href = '/login?expired=true';
        }
        return Promise.reject(new Error('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'));
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(extractErrorMessage(error));
  }
);

/**
 * Trích xuất câu thông báo lỗi rõ ràng từ backend envelope
 */
export function extractErrorMessage(error: AxiosError<ErrorResponse>): Error {
  if (error.response?.data) {
    const data = error.response.data;
    if (data.message) {
      return new Error(data.message);
    }
    if (data.errors && typeof data.errors === 'object') {
      const firstKey = Object.keys(data.errors)[0];
      if (firstKey) {
        return new Error(data.errors[firstKey]);
      }
    }
  }
  if (error.message === 'Network Error') {
    return new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra đường truyền mạng hoặc server.');
  }
  return new Error(error.message || 'Đã có lỗi xảy ra, vui lòng thử lại.');
}
