import { api } from './client';
import type {
  ApiResponse,
  CreateTenantInput,
  CreateTenantResult,
  CreateTenantUserInput,
  PlatformTenant,
  PlatformTenantUser,
  TenantStatus,
} from './types';

/** Platform (SUPER_ADMIN) tenant listeleme filtreleri. */
export interface GetTenantsParams {
  status?: TenantStatus;
  q?: string;
}

/** Tum tenant'lar (SUPER_ADMIN). status/q opsiyonel sunucu filtreleri. */
export async function getTenants(params: GetTenantsParams): Promise<PlatformTenant[]> {
  const res = await api.get<ApiResponse<PlatformTenant[]>>('/api/platform/tenants', { params });
  return res.data.data;
}

/** Yeni tenant + ilk ADMIN olustur. Provisioning sonucu (+ olası uyari) doner. */
export async function createTenant(payload: CreateTenantInput): Promise<CreateTenantResult> {
  const res = await api.post<ApiResponse<CreateTenantResult>>('/api/platform/tenants', payload);
  return res.data.data;
}

/** Tenant durumunu degistirir (AKTIF/ASKIDA). */
export async function updateTenantStatus(
  id: string,
  status: TenantStatus,
): Promise<PlatformTenant> {
  const res = await api.patch<ApiResponse<PlatformTenant>>(
    `/api/platform/tenants/${id}/status`,
    { status },
  );
  return res.data.data;
}

/** Tenant'i soft-delete eder (status=SILINDI). Veri silinmez; listede gizlenir, kullanicilari kilitli. */
export async function softDeleteTenant(id: string): Promise<PlatformTenant> {
  const res = await api.delete<ApiResponse<PlatformTenant>>(`/api/platform/tenants/${id}`);
  return res.data.data;
}

/** Bir tenant'in kullanicilari (SUPER_ADMIN). */
export async function getTenantUsers(tenantId: string): Promise<PlatformTenantUser[]> {
  const res = await api.get<ApiResponse<PlatformTenantUser[]>>(
    `/api/platform/tenants/${tenantId}/users`,
  );
  return res.data.data;
}

/** Tenant'a kullanici ekler (tenant_id path'ten; ilk parola Artademi2026!). */
export async function createTenantUser(
  tenantId: string,
  payload: CreateTenantUserInput,
): Promise<PlatformTenantUser> {
  const res = await api.post<ApiResponse<PlatformTenantUser>>(
    `/api/platform/tenants/${tenantId}/users`,
    payload,
  );
  return res.data.data;
}

/** Tenant'tan kullanici siler. */
export async function deleteTenantUser(tenantId: string, userId: string): Promise<void> {
  await api.delete<ApiResponse<void>>(`/api/platform/tenants/${tenantId}/users/${userId}`);
}
