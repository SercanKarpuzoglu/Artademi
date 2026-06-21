import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createTenant,
  getTenants,
  updateTenantStatus,
  type GetTenantsParams,
} from '../../api/platform';
import type { CreateTenantInput, TenantStatus } from '../../api/types';

/** Tenant listesi sorgusu. Filtre degisince onceki veriyi korur. */
export function useTenants(params: GetTenantsParams) {
  return useQuery({
    queryKey: ['platform-tenants', params],
    queryFn: () => getTenants(params),
    placeholderData: keepPreviousData,
  });
}

/** Yeni tenant + admin; basarida liste tazelenir. */
export function useCreateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTenantInput) => createTenant(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
  });
}

/** Tenant durumu degistir (AKTIF/ASKIDA); basarida liste tazelenir. */
export function useUpdateTenantStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: TenantStatus }) =>
      updateTenantStatus(id, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
  });
}
