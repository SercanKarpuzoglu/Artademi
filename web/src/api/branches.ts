import { api } from './client';
import type { ApiResponse, BranchInput, BranchResponse } from './types';

export interface GetBranchesParams {
  aktif?: boolean;
  q?: string;
  page?: number;
  size?: number;
}

/** Branş listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getBranches(
  params: GetBranchesParams = {},
): Promise<ApiResponse<BranchResponse[]>> {
  const res = await api.get<ApiResponse<BranchResponse[]>>('/api/branches', { params });
  return res.data;
}

/** Tek branş (detay/duzenleme icin). */
export async function getBranch(id: number): Promise<BranchResponse> {
  const res = await api.get<ApiResponse<BranchResponse>>(`/api/branches/${id}`);
  return res.data.data;
}

/** Yeni branş olusturur. */
export async function createBranch(payload: BranchInput): Promise<BranchResponse> {
  const res = await api.post<ApiResponse<BranchResponse>>('/api/branches', payload);
  return res.data.data;
}

/** Branş gunceller. */
export async function updateBranch(id: number, payload: BranchInput): Promise<BranchResponse> {
  const res = await api.put<ApiResponse<BranchResponse>>(`/api/branches/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu degistirir. */
export async function setBranchActive(id: number, aktif: boolean): Promise<BranchResponse> {
  const res = await api.patch<ApiResponse<BranchResponse>>(`/api/branches/${id}/active`, { aktif });
  return res.data.data;
}
