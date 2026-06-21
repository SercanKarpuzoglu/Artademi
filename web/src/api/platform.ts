import { api } from './client';
import type {
  ApiResponse,
  CreateTenantInput,
  CreateTenantResult,
  PlatformTenant,
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
