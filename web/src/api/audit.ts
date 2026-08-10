import { api } from './client';
import type { ApiResponse } from './types';

/** Kurum içi işlem kaydı satırı — backend TenantAuditController.AuditSatiri. */
export interface IslemKaydi {
  id: string;
  actor: string;
  actorAd: string | null;
  eylem: string;
  metot: string;
  yol: string;
  kayitId: string | null;
  createdAt: string;
}

/** Kendi kurumunun işlem kaydı (SADECE ADMIN). Sayfalıdır. */
export async function getIslemKaydi(params: {
  page?: number;
  size?: number;
}): Promise<{ rows: IslemKaydi[]; totalPages: number; totalElements: number }> {
  const res = await api.get<ApiResponse<IslemKaydi[]>>('/api/audit', { params });
  return {
    rows: res.data.data,
    totalPages: res.data.meta?.totalPages ?? 1,
    totalElements: res.data.meta?.totalElements ?? res.data.data.length,
  };
}
