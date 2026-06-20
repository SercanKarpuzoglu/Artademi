import { api } from './client';
import type {
  ApiResponse,
  CreateSessionInput,
  SessionResponse,
  UpdateEntryItem,
} from './types';

export interface GetSessionsParams {
  grupId?: number;
  tarih?: string; // YYYY-MM-DD
  page?: number;
  size?: number;
}

/**
 * Yoklama oturumlari (sayfali). Zarfin tamamini dondurur (data + meta).
 * TEACHER rolu icin backend otomatik olarak yalnizca kendi gruplarinin oturumlarina daraltir.
 */
export async function getSessions(
  params: GetSessionsParams = {},
): Promise<ApiResponse<SessionResponse[]>> {
  const res = await api.get<ApiResponse<SessionResponse[]>>('/api/attendance-sessions', {
    params,
  });
  return res.data;
}

export interface GetGroupSessionsParams {
  from?: string; // YYYY-MM-DD
  to?: string; // YYYY-MM-DD
}

/** Bir grubun yoklama oturumlari. Zarfin tamamini dondurur. */
export async function getGroupSessions(
  groupId: number,
  params: GetGroupSessionsParams = {},
): Promise<ApiResponse<SessionResponse[]>> {
  const res = await api.get<ApiResponse<SessionResponse[]>>(
    `/api/groups/${groupId}/attendance-sessions`,
    { params },
  );
  return res.data;
}

/** Tek yoklama oturumu (girisleri ile). */
export async function getSessionById(id: number): Promise<SessionResponse> {
  const res = await api.get<ApiResponse<SessionResponse>>(`/api/attendance-sessions/${id}`);
  return res.data.data;
}

/**
 * Yeni yoklama oturumu olusturur. Backend grubun aktif kayitlari icin GELMEDI
 * girislerini otomatik uretir.
 */
export async function createSession(payload: CreateSessionInput): Promise<SessionResponse> {
  const res = await api.post<ApiResponse<SessionResponse>>(
    '/api/attendance-sessions',
    payload,
  );
  return res.data.data;
}

/** Oturum girislerini toplu gunceller. Govde CIPLAK DIZIDIR (bare array). */
export async function updateEntries(
  sessionId: number,
  items: UpdateEntryItem[],
): Promise<SessionResponse> {
  const res = await api.put<ApiResponse<SessionResponse>>(
    `/api/attendance-sessions/${sessionId}/entries`,
    items,
  );
  return res.data.data;
}
