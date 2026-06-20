import { api } from './client';
import type { ApiResponse, SaleInput, SaleResponse } from './types';

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
