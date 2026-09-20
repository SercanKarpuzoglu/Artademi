import { api } from './client';
import type {
  ApiResponse,
  SaleInput,
  SaleResponse,
  SatisIadeOnizleme,
  SatisIadeInput,
} from './types';

export interface GetSalesParams {
  urunId?: number;
  ogrenciId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** Satis listesi (sayfali, satis tarihine gore azalan). Zarfin tamamini dondurur (data + meta). */
export async function getSales(params: GetSalesParams = {}): Promise<ApiResponse<SaleResponse[]>> {
  const res = await api.get<ApiResponse<SaleResponse[]>>('/api/sales', { params });
  return res.data;
}

/**
 * Yeni satis olusturur (stok dusulur). birimFiyat/toplamTutar backend'de hesaplanir.
 * Yetersiz stok -> 409 (CONFLICT). ADMIN + FRONTDESK_ACCOUNTING.
 */
export async function createSale(payload: SaleInput): Promise<SaleResponse> {
  const res = await api.post<ApiResponse<SaleResponse>>('/api/sales', payload);
  return res.data.data;
}

/**
 * Urun iadesi (ADMIN + muhasebe). Orijinal satira dokunulmaz; NEGATIF adet/tutarli yeni satir
 * yazilir ve stok geri eklenir. Sinir asimi -> 400 (error.fields.adet).
 */
export async function iadeEt(satisId: number, payload: SatisIadeInput): Promise<SaleResponse> {
  const res = await api.post<ApiResponse<SaleResponse>>(`/api/sales/${satisId}/iade`, payload);
  return res.data.data;
}

/** Urun iadesi onay ozeti: kac adet iade edilebilir, hangi fiyattan, engel var mi. */
export async function getSatisIadeOnizleme(satisId: number): Promise<SatisIadeOnizleme> {
  const res = await api.get<ApiResponse<SatisIadeOnizleme>>(`/api/sales/${satisId}/iade-onizleme`);
  return res.data.data;
}
