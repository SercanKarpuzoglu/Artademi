import { api } from './client';
import type { ApiResponse, TenantResponse, UpdateTenantInput } from './types';

/** Oturum sahibinin kendi tenant'i (tenant ASLA parametre alinmaz; backend context'ten cozer). */
export async function getTenant(): Promise<TenantResponse> {
  const res = await api.get<ApiResponse<TenantResponse>>('/api/tenant');
  return res.data.data;
}

/** Kendi tenant'inin adini gunceller (SADECE ADMIN). */
export async function updateTenant(payload: UpdateTenantInput): Promise<TenantResponse> {
  const res = await api.put<ApiResponse<TenantResponse>>('/api/tenant', payload);
  return res.data.data;
}
