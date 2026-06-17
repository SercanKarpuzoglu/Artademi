import axios, { AxiosError } from 'axios';
import { keycloak } from '../lib/keycloak';
import type { ApiResponse } from './types';

/** Backend error zarfindan firlatilan tiplenmis hata. */
export class ApiException extends Error {
  readonly code: string;
  readonly fields?: Record<string, string> | null;

  constructor(code: string, message: string, fields?: Record<string, string> | null) {
    super(message);
    this.name = 'ApiException';
    this.code = code;
    this.fields = fields;
  }
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

// --- Istek: her cagriya taze Bearer token ekle ---
api.interceptors.request.use(async (config) => {
  try {
    // Token 30 sn icinde dolacaksa yenile (keycloak-js otomatik refresh).
    await keycloak.updateToken(30);
  } catch {
    // Yenileme basarisiz -> tekrar giris.
    await keycloak.login();
    return Promise.reject(new ApiException('TOKEN_REFRESH_FAILED', 'Oturum yenilenemedi'));
  }
  if (keycloak.token) {
    config.headers.Authorization = `Bearer ${keycloak.token}`;
  }
  return config;
});

// --- Yanit: zarfi ac, success:false ise hata firlat, 401'de yenile/giris ---
api.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined;
    if (body && typeof body === 'object' && 'success' in body && !body.success) {
      throw new ApiException(
        body.error?.code ?? 'INTERNAL',
        body.error?.message ?? 'Bilinmeyen bir hata oluştu',
        body.error?.fields,
      );
    }
    return response;
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401) {
      try {
        // Token'i zorla yenile; olmazsa login'e yonlendir.
        await keycloak.updateToken(-1);
      } catch {
        await keycloak.login();
      }
    }
    const apiError = error.response?.data?.error;
    if (apiError) {
      return Promise.reject(new ApiException(apiError.code, apiError.message, apiError.fields));
    }
    return Promise.reject(error);
  },
);
