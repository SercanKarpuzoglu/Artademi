import { api } from './client';
import type { ApiResponse, OdemeYontemi, PaymentInput, PaymentResponse } from './types';

export interface GetPaymentsParams {
  ogrenciId?: number;
  from?: string;
  to?: string;
  yontem?: OdemeYontemi;
  page?: number;
  size?: number;
}

/** Ödeme listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getPayments(
  params: GetPaymentsParams = {},
): Promise<ApiResponse<PaymentResponse[]>> {
  const res = await api.get<ApiResponse<PaymentResponse[]>>('/api/payments', { params });
  return res.data;
}

/** Yeni ödeme olusturur. */
export async function createPayment(payload: PaymentInput): Promise<PaymentResponse> {
  const res = await api.post<ApiResponse<PaymentResponse>>('/api/payments', payload);
  return res.data.data;
}
