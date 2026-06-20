import { api } from './client';
import type { ApiResponse, ChangePasswordInput, MeResponse, UpdateMeInput } from './types';

/** Oturum sahibinin profili (her authenticated rol). mustChangePassword ilk-parola kilidini surer. */
export async function getMe(): Promise<MeResponse> {
  const res = await api.get<ApiResponse<MeResponse>>('/api/me');
  return res.data.data;
}

/** Kendi profilini günceller. */
export async function updateMe(payload: UpdateMeInput): Promise<MeResponse> {
  const res = await api.put<ApiResponse<MeResponse>>('/api/me', payload);
  return res.data.data;
}

/** Şifre değiştirir. 400 "Mevcut parola hatalı" hata kodu VALIDATION_ERROR ile gelir. */
export async function changePassword(payload: ChangePasswordInput): Promise<void> {
  await api.post<ApiResponse<void>>('/api/me/change-password', payload);
}
