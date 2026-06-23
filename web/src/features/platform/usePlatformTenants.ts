import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createTenant,
  createTenantUser,
  deleteTenantUser,
  getTenants,
  getTenantUsers,
  softDeleteTenant,
  updateTenantStatus,
  type GetTenantsParams,
} from '../../api/platform';
import type { CreateTenantInput, CreateTenantUserInput, TenantStatus } from '../../api/types';

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

/** Tenant soft-delete; basarida liste tazelenir. */
export function useSoftDeleteTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => softDeleteTenant(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['platform-tenants'] });
    },
  });
}

/** Bir tenant'in kullanicilari. */
export function useTenantUsers(tenantId: string) {
  return useQuery({
    queryKey: ['platform-tenant-users', tenantId],
    queryFn: () => getTenantUsers(tenantId),
  });
}

/** Tenant'a kullanici ekle; basarida o tenant'in kullanici listesi tazelenir. */
export function useCreateTenantUser(tenantId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateTenantUserInput) => createTenantUser(tenantId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['platform-tenant-users', tenantId] });
    },
  });
}

/** Tenant'tan kullanici sil; basarida liste tazelenir. */
export function useDeleteTenantUser(tenantId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => deleteTenantUser(tenantId, userId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['platform-tenant-users', tenantId] });
    },
  });
}
