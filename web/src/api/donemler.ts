import { api } from './client';
import type { ApiResponse, DonemInput, DonemResponse } from './types';

/** Dönemler (ofis rolleri okur; yazma ADMIN). */
export async function getDonemler(aktif?: boolean): Promise<DonemResponse[]> {
  const res = await api.get<ApiResponse<DonemResponse[]>>('/api/donemler', { params: { aktif } });
  return res.data.data;
}

export async function getDonem(id: number): Promise<DonemResponse> {
  const res = await api.get<ApiResponse<DonemResponse>>(`/api/donemler/${id}`);
  return res.data.data;
}

export async function createDonem(payload: DonemInput): Promise<DonemResponse> {
  const res = await api.post<ApiResponse<DonemResponse>>('/api/donemler', payload);
  return res.data.data;
}

export async function updateDonem(id: number, payload: DonemInput): Promise<DonemResponse> {
  const res = await api.put<ApiResponse<DonemResponse>>(`/api/donemler/${id}`, payload);
  return res.data.data;
}

export async function setDonemActive(id: number, aktif: boolean): Promise<DonemResponse> {
  const res = await api.patch<ApiResponse<DonemResponse>>(`/api/donemler/${id}/active`, null, {
    params: { aktif },
  });
  return res.data.data;
}
