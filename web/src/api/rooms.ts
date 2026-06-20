import { api } from './client';
import type { ApiResponse, RoomInput, RoomResponse } from './types';

export interface GetRoomsParams {
  aktif?: boolean;
  q?: string;
  page?: number;
  size?: number;
}

/** Salon listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getRooms(params: GetRoomsParams = {}): Promise<ApiResponse<RoomResponse[]>> {
  const res = await api.get<ApiResponse<RoomResponse[]>>('/api/rooms', { params });
  return res.data;
}

/** Tek salon (detay/duzenleme icin). */
export async function getRoom(id: number): Promise<RoomResponse> {
  const res = await api.get<ApiResponse<RoomResponse>>(`/api/rooms/${id}`);
  return res.data.data;
}

/** Yeni salon olusturur. */
export async function createRoom(payload: RoomInput): Promise<RoomResponse> {
  const res = await api.post<ApiResponse<RoomResponse>>('/api/rooms', payload);
  return res.data.data;
}

/** Salon gunceller. */
export async function updateRoom(id: number, payload: RoomInput): Promise<RoomResponse> {
  const res = await api.put<ApiResponse<RoomResponse>>(`/api/rooms/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu degistirir. */
export async function setRoomActive(id: number, aktif: boolean): Promise<RoomResponse> {
  const res = await api.patch<ApiResponse<RoomResponse>>(`/api/rooms/${id}/active`, { aktif });
  return res.data.data;
}
