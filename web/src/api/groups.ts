import { api } from './client';
import type { ApiResponse, GroupInput, GroupResponse, GrupTipi } from './types';

export interface GetGroupsParams {
  tip?: GrupTipi;
  aktif?: boolean;
  bransId?: number;
  ogretmenId?: number;
  salonId?: number;
  q?: string;
  page?: number;
  size?: number;
}

/** Grup listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getGroups(
  params: GetGroupsParams = {},
): Promise<ApiResponse<GroupResponse[]>> {
  const res = await api.get<ApiResponse<GroupResponse[]>>('/api/groups', { params });
  return res.data;
}

/** Tek grup (detay/duzenleme icin). */
export async function getGroupById(id: number): Promise<GroupResponse> {
  const res = await api.get<ApiResponse<GroupResponse>>(`/api/groups/${id}`);
  return res.data.data;
}

/** Yeni grup olusturur. */
export async function createGroup(payload: GroupInput): Promise<GroupResponse> {
  const res = await api.post<ApiResponse<GroupResponse>>('/api/groups', payload);
  return res.data.data;
}

/** Grup gunceller. */
export async function updateGroup(id: number, payload: GroupInput): Promise<GroupResponse> {
  const res = await api.put<ApiResponse<GroupResponse>>(`/api/groups/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu degistirir. */
export async function setGroupActive(id: number, aktif: boolean): Promise<GroupResponse> {
  const res = await api.patch<ApiResponse<GroupResponse>>(`/api/groups/${id}/active`, { aktif });
  return res.data.data;
}
