import { api } from './client';
import type { ApiResponse, SubeInput, SubeResponse } from './types';

export interface GetSubelerParams {
  aktif?: boolean;
  q?: string;
  page?: number;
  size?: number;
}

/** Şube listesi (sayfalı). Zarfın tamamını döndürür (data + meta). Tenant JWT'den okunur. */
export async function getSubeler(
  params: GetSubelerParams = {},
): Promise<ApiResponse<SubeResponse[]>> {
  const res = await api.get<ApiResponse<SubeResponse[]>>('/api/subeler', { params });
  return res.data;
}

/** Tek şube (detay/düzenleme için). */
export async function getSube(id: number): Promise<SubeResponse> {
  const res = await api.get<ApiResponse<SubeResponse>>(`/api/subeler/${id}`);
  return res.data.data;
}

/** Yeni şube oluşturur. */
export async function createSube(payload: SubeInput): Promise<SubeResponse> {
  const res = await api.post<ApiResponse<SubeResponse>>('/api/subeler', payload);
  return res.data.data;
}

/** Şube günceller. */
export async function updateSube(id: number, payload: SubeInput): Promise<SubeResponse> {
  const res = await api.put<ApiResponse<SubeResponse>>(`/api/subeler/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu değiştirir (silme yerine pasifleştirme). */
export async function setSubeActive(id: number, aktif: boolean): Promise<SubeResponse> {
  const res = await api.patch<ApiResponse<SubeResponse>>(`/api/subeler/${id}/active`, { aktif });
  return res.data.data;
}
