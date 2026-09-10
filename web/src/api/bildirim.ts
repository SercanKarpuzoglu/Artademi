import { api } from './client';
import type { ApiResponse, BildirimAyari, UygulamaBildirimZili } from './types';

/** Kurumun bildirim tercihleri (ADMIN). Hiç kaydedilmemişse varsayılan (hepsi kapalı) döner. */
export async function getBildirimAyari(): Promise<BildirimAyari> {
  const res = await api.get<ApiResponse<BildirimAyari>>('/api/bildirim-ayarlari');
  return res.data.data;
}

export async function updateBildirimAyari(payload: BildirimAyari): Promise<BildirimAyari> {
  const res = await api.put<ApiResponse<BildirimAyari>>('/api/bildirim-ayarlari', payload);
  return res.data.data;
}

/** Zil: okunmamış sayısı + son bildirimler (tüm iş rolleri; herkes kendi hedefindekileri görür). */
export async function getZil(): Promise<UygulamaBildirimZili> {
  const res = await api.get<ApiResponse<UygulamaBildirimZili>>('/api/bildirimler');
  return res.data.data;
}

export async function bildirimOkundu(id: number): Promise<void> {
  await api.post(`/api/bildirimler/${id}/okundu`);
}

export async function bildirimlerOkundu(): Promise<void> {
  await api.post('/api/bildirimler/okundu-hepsi');
}
