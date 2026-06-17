import { api } from './client';
import type { ApiResponse, StudentResponse } from './types';

export interface GetStudentsParams {
  status?: string;
  q?: string;
  page?: number;
  size?: number;
}

/**
 * Ogrenci listesi. Tenant ASLA gonderilmez; backend JWT'deki tenant_id'den okur.
 * Iskelet asamasinda yalnizca baglanti kanitı icin kullanilir (3b'de TanStack Query ile).
 */
export async function getStudents(
  params: GetStudentsParams = {},
): Promise<ApiResponse<StudentResponse[]>> {
  const res = await api.get<ApiResponse<StudentResponse[]>>('/api/students', { params });
  return res.data;
}
