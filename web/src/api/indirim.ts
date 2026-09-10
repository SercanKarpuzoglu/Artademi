import { api } from './client';
import type {
  ApiResponse,
  IndirimInput,
  IndirimResponse,
  OgrenciIndirimiInput,
  OgrenciIndirimiResponse,
} from './types';

/** İndirim tanımları (ADMIN + muhasebe okur; yazma ADMIN). */
export async function getIndirimler(aktif?: boolean): Promise<IndirimResponse[]> {
  const res = await api.get<ApiResponse<IndirimResponse[]>>('/api/indirimler', { params: { aktif } });
  return res.data.data;
}

export async function createIndirim(payload: IndirimInput): Promise<IndirimResponse> {
  const res = await api.post<ApiResponse<IndirimResponse>>('/api/indirimler', payload);
  return res.data.data;
}

export async function updateIndirim(id: number, payload: IndirimInput): Promise<IndirimResponse> {
  const res = await api.put<ApiResponse<IndirimResponse>>(`/api/indirimler/${id}`, payload);
  return res.data.data;
}

export async function indirimDurum(id: number, aktif: boolean): Promise<IndirimResponse> {
  const res = await api.patch<ApiResponse<IndirimResponse>>(`/api/indirimler/${id}/durum`, null, {
    params: { aktif },
  });
  return res.data.data;
}

/** Öğrencinin indirim atamaları (aktif + bitmiş). */
export async function getOgrenciIndirimleri(ogrenciId: number): Promise<OgrenciIndirimiResponse[]> {
  const res = await api.get<ApiResponse<OgrenciIndirimiResponse[]>>(`/api/students/${ogrenciId}/indirimler`);
  return res.data.data;
}

export async function ogrenciyeIndirimAta(
  ogrenciId: number,
  payload: OgrenciIndirimiInput,
): Promise<OgrenciIndirimiResponse> {
  const res = await api.post<ApiResponse<OgrenciIndirimiResponse>>(
    `/api/students/${ogrenciId}/indirimler`,
    payload,
  );
  return res.data.data;
}

export async function ogrenciIndirimiBitir(id: number): Promise<OgrenciIndirimiResponse> {
  const res = await api.patch<ApiResponse<OgrenciIndirimiResponse>>(`/api/ogrenci-indirimleri/${id}/bitir`);
  return res.data.data;
}
