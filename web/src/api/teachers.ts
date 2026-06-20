import { api } from './client';
import type { ApiResponse, TeacherInput, TeacherResponse } from './types';

export interface GetTeachersParams {
  aktif?: boolean;
  q?: string;
  bransId?: number;
  page?: number;
  size?: number;
}

/** Öğretmen listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getTeachers(
  params: GetTeachersParams = {},
): Promise<ApiResponse<TeacherResponse[]>> {
  const res = await api.get<ApiResponse<TeacherResponse[]>>('/api/teachers', { params });
  return res.data;
}

/** Tek öğretmen (detay/duzenleme icin). */
export async function getTeacher(id: number): Promise<TeacherResponse> {
  const res = await api.get<ApiResponse<TeacherResponse>>(`/api/teachers/${id}`);
  return res.data.data;
}

/** Yeni öğretmen olusturur. */
export async function createTeacher(payload: TeacherInput): Promise<TeacherResponse> {
  const res = await api.post<ApiResponse<TeacherResponse>>('/api/teachers', payload);
  return res.data.data;
}

/** Öğretmen gunceller. */
export async function updateTeacher(id: number, payload: TeacherInput): Promise<TeacherResponse> {
  const res = await api.put<ApiResponse<TeacherResponse>>(`/api/teachers/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu degistirir. */
export async function setTeacherActive(id: number, aktif: boolean): Promise<TeacherResponse> {
  const res = await api.patch<ApiResponse<TeacherResponse>>(`/api/teachers/${id}/active`, { aktif });
  return res.data.data;
}
