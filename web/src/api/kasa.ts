import { api } from './client';
import type {
  ApiResponse,
  DuzeltmeInput,
  HareketResponse,
  KasaInput,
  KasaResponse,
  TransferInput,
} from './types';

export async function getKasalar(aktif?: boolean): Promise<KasaResponse[]> {
  const res = await api.get<ApiResponse<KasaResponse[]>>('/api/kasalar', { params: { aktif } });
  return res.data.data;
}

export async function createKasa(payload: KasaInput): Promise<KasaResponse> {
  const res = await api.post<ApiResponse<KasaResponse>>('/api/kasalar', payload);
  return res.data.data;
}

export async function updateKasa(id: number, payload: KasaInput): Promise<KasaResponse> {
  const res = await api.put<ApiResponse<KasaResponse>>(`/api/kasalar/${id}`, payload);
  return res.data.data;
}

export async function kasaDurum(id: number, aktif: boolean): Promise<KasaResponse> {
  const res = await api.patch<ApiResponse<KasaResponse>>(`/api/kasalar/${id}/durum`, null, {
    params: { aktif },
  });
  return res.data.data;
}

export async function getHareketler(kasaId: number): Promise<HareketResponse[]> {
  const res = await api.get<ApiResponse<HareketResponse[]>>(`/api/kasalar/${kasaId}/hareketler`);
  return res.data.data;
}

/** Transfer iki hareket satırı üretir (kaynakta çıkış, hedefte giriş). */
export async function transfer(payload: TransferInput): Promise<HareketResponse[]> {
  const res = await api.post<ApiResponse<HareketResponse[]>>('/api/kasalar/transfer', payload);
  return res.data.data;
}

export async function duzeltme(
  kasaId: number,
  payload: DuzeltmeInput,
): Promise<HareketResponse> {
  const res = await api.post<ApiResponse<HareketResponse>>(
    `/api/kasalar/${kasaId}/duzeltme`,
    payload,
  );
  return res.data.data;
}

/** Transferse İKİ bacak birden silinir; yarım transfer kalmaz. */
export async function hareketSil(hareketId: number): Promise<void> {
  await api.delete(`/api/kasalar/hareketler/${hareketId}`);
}
