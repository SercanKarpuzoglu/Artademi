import axios, { AxiosError } from 'axios';
import { ApiException } from './client';
import type { ApiResponse } from './types';

/**
 * Kimlik GEREKTİRMEYEN uçlar için ayrı istemci (`/api/public/**`).
 *
 * ⚠️ Neden ayrı: paylaşılan `api` istemcisinin istek interceptor'ı her çağrıda
 * `keycloak.updateToken()` çağırır ve başarısız olursa `keycloak.login()` ile giriş
 * ekranına yönlendirir. Public başvuru formunu dolduran veli için bu, formu görmeden
 * giriş ekranına düşmek demektir. Bu istemcinin Keycloak ile HİÇBİR bağı yoktur.
 */
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

// Zarfı açar; success:false ise tiplenmiş hata fırlatır (paylaşılan istemciyle aynı sözleşme).
publicApi.interceptors.response.use(
  (response) => {
    const envelope = response.data as ApiResponse<unknown>;
    if (envelope && envelope.success === false) {
      const err = envelope.error;
      throw new ApiException(err?.code ?? 'UNKNOWN', err?.message ?? 'Bir hata oluştu', err?.fields);
    }
    return response;
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const err = error.response?.data?.error;
    if (err) {
      return Promise.reject(new ApiException(err.code, err.message, err.fields));
    }
    return Promise.reject(
      new ApiException('NETWORK', 'Sunucuya ulaşılamadı. Lütfen tekrar deneyin.'),
    );
  },
);

export { publicApi };
