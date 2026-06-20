import { api } from './client';
import type { ApiResponse, HaftaGunu, ScheduleInput, ScheduleResponse } from './types';

export interface GetSchedulesParams {
  grupId?: number;
  gun?: HaftaGunu;
  aktif?: boolean;
  page?: number;
  size?: number;
}

/** Program listesi (sayfali). Zarfin tamamini dondurur (data + meta). */
export async function getSchedules(
  params: GetSchedulesParams = {},
): Promise<ApiResponse<ScheduleResponse[]>> {
  const res = await api.get<ApiResponse<ScheduleResponse[]>>('/api/schedules', { params });
  return res.data;
}

/** Bir grubun program (ders saati) listesi. Zarfin tamamini dondurur. */
export async function getGroupSchedules(
  groupId: number,
): Promise<ApiResponse<ScheduleResponse[]>> {
  const res = await api.get<ApiResponse<ScheduleResponse[]>>(
    `/api/groups/${groupId}/schedules`,
  );
  return res.data;
}

/** Tek program kaydi. */
export async function getScheduleById(id: number): Promise<ScheduleResponse> {
  const res = await api.get<ApiResponse<ScheduleResponse>>(`/api/schedules/${id}`);
  return res.data.data;
}

/** Yeni ders saati olusturur. */
export async function createSchedule(payload: ScheduleInput): Promise<ScheduleResponse> {
  const res = await api.post<ApiResponse<ScheduleResponse>>('/api/schedules', payload);
  return res.data.data;
}

/** Ders saati gunceller. */
export async function updateSchedule(
  id: number,
  payload: ScheduleInput,
): Promise<ScheduleResponse> {
  const res = await api.put<ApiResponse<ScheduleResponse>>(`/api/schedules/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu degistirir. */
export async function setScheduleActive(
  id: number,
  aktif: boolean,
): Promise<ScheduleResponse> {
  const res = await api.patch<ApiResponse<ScheduleResponse>>(
    `/api/schedules/${id}/active`,
    { aktif },
  );
  return res.data.data;
}
