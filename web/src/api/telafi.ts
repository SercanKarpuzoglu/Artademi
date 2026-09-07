import { api } from './client';
import type { ApiResponse, TelafiAdayi, TelafiDurumu, TelafiResponse, TelafiVerInput } from './types';

export async function getTelafiler(params: {
  durum?: TelafiDurumu;
  ogrenciId?: number;
} = {}): Promise<TelafiResponse[]> {
  const res = await api.get<ApiResponse<TelafiResponse[]>>('/api/telafi', { params });
  return res.data.data;
}

/** Hak verilebilecek devamsızlıklar. Hak otomatik doğmaz; bu bir öneri listesidir. */
export async function getTelafiAdaylari(): Promise<TelafiAdayi[]> {
  const res = await api.get<ApiResponse<TelafiAdayi[]>>('/api/telafi/adaylar');
  return res.data.data;
}

export async function getBekleyenTelafiSayisi(): Promise<number> {
  const res = await api.get<ApiResponse<number>>('/api/telafi/bekleyen-sayisi');
  return res.data.data;
}

export async function telafiVer(payload: TelafiVerInput): Promise<TelafiResponse> {
  const res = await api.post<ApiResponse<TelafiResponse>>('/api/telafi', payload);
  return res.data.data;
}

export async function telafiKullan(
  id: number,
  kullanilanOturumId: number,
): Promise<TelafiResponse> {
  const res = await api.post<ApiResponse<TelafiResponse>>(`/api/telafi/${id}/kullan`, {
    kullanilanOturumId,
  });
  return res.data.data;
}

export async function telafiIptal(id: number): Promise<TelafiResponse> {
  const res = await api.post<ApiResponse<TelafiResponse>>(`/api/telafi/${id}/iptal`);
  return res.data.data;
}
