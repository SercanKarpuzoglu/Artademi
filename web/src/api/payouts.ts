import { api } from './client';
import type {
  ApiResponse,
  CalculatePayoutInput,
  PayoutDurumu,
  PayoutResponse,
} from './types';

export interface GetPayoutsParams {
  ogretmenId?: number;
  donem?: string;
  durum?: PayoutDurumu;
  page?: number;
  size?: number;
}

/** Hakediş listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. ADMIN. */
export async function getPayouts(
  params: GetPayoutsParams = {},
): Promise<ApiResponse<PayoutResponse[]>> {
  const res = await api.get<ApiResponse<PayoutResponse[]>>('/api/payouts', { params });
  return res.data;
}

/** Hakediş onizlemesi — KAYIT YAZMAZ, yalnizca hesaplanan sonucu doner (ADMIN). */
export async function onizlePayout({
  ogretmenId,
  donem,
  kdvOrani,
}: CalculatePayoutInput): Promise<PayoutResponse> {
  const res = await api.get<ApiResponse<PayoutResponse>>('/api/payouts/onizle', {
    params: { ogretmenId, donem, kdvOrani },
  });
  return res.data.data;
}

/** Hakediş hesaplar ve kaydeder (ADMIN). Ayni öğretmen+dönem → 409 CONFLICT. */
export async function hesaplaPayout(payload: CalculatePayoutInput): Promise<PayoutResponse> {
  const res = await api.post<ApiResponse<PayoutResponse>>('/api/payouts/hesapla', payload);
  return res.data.data;
}

/** Hakedişi "ödendi" olarak isaretler (ADMIN). Gövdesiz PATCH. */
export async function odePayout(id: number): Promise<PayoutResponse> {
  const res = await api.patch<ApiResponse<PayoutResponse>>(`/api/payouts/${id}/ode`);
  return res.data.data;
}
