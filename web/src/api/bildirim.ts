import { api } from './client';
import type { ApiResponse, BildirimAyari } from './types';

/** Kurumun bildirim tercihleri (ADMIN). Hiç kaydedilmemişse varsayılan (hepsi kapalı) döner. */
export async function getBildirimAyari(): Promise<BildirimAyari> {
  const res = await api.get<ApiResponse<BildirimAyari>>('/api/bildirim-ayarlari');
  return res.data.data;
}

export async function updateBildirimAyari(payload: BildirimAyari): Promise<BildirimAyari> {
  const res = await api.put<ApiResponse<BildirimAyari>>('/api/bildirim-ayarlari', payload);
  return res.data.data;
}
