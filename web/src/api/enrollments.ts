import { api } from './client';
import type {
  ApiResponse,
  EnrollmentDurumu,
  EnrollmentInput,
  EnrollmentResponse,
} from './types';

export interface GetGroupEnrollmentsParams {
  durum?: EnrollmentDurumu;
  page?: number;
  size?: number;
}

/** Bir grubun kayıtları (sayfali). Zarfin tamamini dondurur (data + meta). */
export async function getGroupEnrollments(
  groupId: number,
  params: GetGroupEnrollmentsParams = {},
): Promise<ApiResponse<EnrollmentResponse[]>> {
  const res = await api.get<ApiResponse<EnrollmentResponse[]>>(
    `/api/groups/${groupId}/enrollments`,
    { params },
  );
  return res.data;
}

/** Yeni kayıt olusturur (öğrenciyi gruba ekler). kayitTarihi gonderilmezse backend bugunu kullanir. */
export async function createEnrollment(payload: EnrollmentInput): Promise<EnrollmentResponse> {
  const res = await api.post<ApiResponse<EnrollmentResponse>>('/api/enrollments', payload);
  return res.data.data;
}

/** Öğrenciyi gruptan çıkarır (durum AYRILDI olur; silinmez). */
export async function leaveEnrollment(id: number): Promise<EnrollmentResponse> {
  const res = await api.patch<ApiResponse<EnrollmentResponse>>(`/api/enrollments/${id}/leave`);
  return res.data.data;
}
