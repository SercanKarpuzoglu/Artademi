import { api } from './client';
import type { ApiResponse, StudentInput, StudentResponse, StudentStatus } from './types';

export interface GetStudentsParams {
  status?: StudentStatus;
  q?: string;
  page?: number;
  size?: number;
}

/**
 * Ogrenci listesi (sayfali). Tenant ASLA gonderilmez; backend JWT'deki tenant_id'den okur.
 * Zarfin tamamini dondurur (data + meta).
 */
export async function getStudents(
  params: GetStudentsParams = {},
): Promise<ApiResponse<StudentResponse[]>> {
  const res = await api.get<ApiResponse<StudentResponse[]>>('/api/students', { params });
  return res.data;
}

/** Tek ogrenci (detay/duzenleme icin). */
export async function getStudent(id: number): Promise<StudentResponse> {
  const res = await api.get<ApiResponse<StudentResponse>>(`/api/students/${id}`);
  return res.data.data;
}

/** Kardesler: ayni veli TC'sine bagli diger ogrenciler (backend tenant kapsaminda doner). */
export async function getSiblings(id: number): Promise<StudentResponse[]> {
  const res = await api.get<ApiResponse<StudentResponse[]>>(`/api/students/${id}/siblings`);
  return res.data.data;
}

/** Yeni ogrenci olusturur (backend statuyu DENEME yapar). */
export async function createStudent(payload: StudentInput): Promise<StudentResponse> {
  const res = await api.post<ApiResponse<StudentResponse>>('/api/students', payload);
  return res.data.data;
}

/** Ogrenci gunceller (statu bu uctan degismez). */
export async function updateStudent(id: number, payload: StudentInput): Promise<StudentResponse> {
  const res = await api.put<ApiResponse<StudentResponse>>(`/api/students/${id}`, payload);
  return res.data.data;
}
