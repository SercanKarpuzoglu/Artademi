import { api } from './client';
import type {
  AccrualGenerationResult,
  AccrualInput,
  AccrualResponse,
  ApiResponse,
} from './types';

export interface GetAccrualsParams {
  ogrenciId?: number;
  donem?: string;
  grupId?: number;
  page?: number;
  size?: number;
}

/** Tahakkuk listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getAccruals(
  params: GetAccrualsParams = {},
): Promise<ApiResponse<AccrualResponse[]>> {
  const res = await api.get<ApiResponse<AccrualResponse[]>>('/api/accruals', { params });
  return res.data;
}

/** Yeni tahakkuk olusturur. */
export async function createAccrual(payload: AccrualInput): Promise<AccrualResponse> {
  const res = await api.post<ApiResponse<AccrualResponse>>('/api/accruals', payload);
  return res.data.data;
}

/** Otomatik aylik tahakkuk onizlemesi — KAYIT OLUSTURMAZ (ADMIN). */
export async function uretOnizle(donem: string): Promise<AccrualGenerationResult> {
  const res = await api.get<ApiResponse<AccrualGenerationResult>>('/api/accruals/uret-onizle', {
    params: { donem },
  });
  return res.data.data;
}

/** Otomatik aylik tahakkuk uretir ve kaydeder (idempotent, ADMIN). */
export async function uret(donem: string): Promise<AccrualGenerationResult> {
  const res = await api.post<ApiResponse<AccrualGenerationResult>>('/api/accruals/uret', { donem });
  return res.data.data;
}
