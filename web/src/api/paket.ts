import { api } from './client';
import type { ApiResponse, PaketResponse, PaketSatInput } from './types';

export async function getPaketler(ogrenciId?: number): Promise<PaketResponse[]> {
  const res = await api.get<ApiResponse<PaketResponse[]>>('/api/paketler', {
    params: { ogrenciId },
  });
  return res.data.data;
}

/** Paket satar; backend PEŞİN tek tahakkuk üretir. */
export async function paketSat(payload: PaketSatInput): Promise<PaketResponse> {
  const res = await api.post<ApiResponse<PaketResponse>>('/api/paketler', payload);
  return res.data.data;
}

/** Paketi iptal eder. Tahakkuk otomatik silinmez (tahsilat yapılmış olabilir). */
export async function paketIptal(id: number): Promise<PaketResponse> {
  const res = await api.post<ApiResponse<PaketResponse>>(`/api/paketler/${id}/iptal`);
  return res.data.data;
}
