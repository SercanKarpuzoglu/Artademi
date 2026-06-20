import { api } from './client';
import type { ApiResponse, CreateUserInput, UpdateUserInput, UserResponse } from './types';

export interface GetUsersParams {
  aktif?: boolean;
  rol?: string;
  q?: string;
}

/**
 * Kullanıcı listesi (YALNIZCA ADMIN). Backend bu uçta sayfalama meta'si DONDURMEZ (düz dizi),
 * yine de zarf sözleşmesi gereği tüm ApiResponse dönülür (data dizidir, meta null).
 */
export async function getUsers(
  params: GetUsersParams = {},
): Promise<ApiResponse<UserResponse[]>> {
  const res = await api.get<ApiResponse<UserResponse[]>>('/api/users', { params });
  return res.data;
}

/** Tek kullanıcı (düzenleme formu için). */
export async function getUser(id: string): Promise<UserResponse> {
  const res = await api.get<ApiResponse<UserResponse>>(`/api/users/${id}`);
  return res.data.data;
}

/** Yeni kullanıcı oluşturur (201). İlk parola backend tarafında sabit: Artademi2026!. */
export async function createUser(payload: CreateUserInput): Promise<UserResponse> {
  const res = await api.post<ApiResponse<UserResponse>>('/api/users', payload);
  return res.data.data;
}

/** Kullanıcı günceller (kullaniciAdi değişmez). */
export async function updateUser(id: string, payload: UpdateUserInput): Promise<UserResponse> {
  const res = await api.put<ApiResponse<UserResponse>>(`/api/users/${id}`, payload);
  return res.data.data;
}

/** Aktiflik durumunu değiştirir (enabled). */
export async function setUserActive(id: string, aktif: boolean): Promise<UserResponse> {
  const res = await api.patch<ApiResponse<UserResponse>>(`/api/users/${id}/active`, { aktif });
  return res.data.data;
}

/** Kullanıcıyı kalıcı olarak siler. */
export async function deleteUser(id: string): Promise<void> {
  await api.delete<ApiResponse<void>>(`/api/users/${id}`);
}
