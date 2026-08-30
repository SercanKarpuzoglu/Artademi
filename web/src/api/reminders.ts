import { api } from './client';
import type { ApiResponse } from './types';

/** Hatırlatma gönderilebilecek borçlu öğrenci — backend BorcluAday. */
export interface BorcluAday {
  ogrenciId: number;
  adSoyad: string;
  bakiye: string | number;
  veliMail: string | null;
  sonHatirlatma: string | null;
  gonderilebilir: boolean;
  engelSebebi: string | null;
}

/** Toplu gönderim sonucu — kısmi başarı normaldir, öğrenci bazında sonuç döner. */
export interface HatirlatmaSonucu {
  gonderilen: number;
  atlanan: number;
  satirlar: Array<{
    ogrenciId: number;
    adSoyad: string;
    gonderildi: boolean;
    aciklama: string;
  }>;
}

/** Borçlu öğrenciler + gönderilebilirlik durumu (ADMIN + ACCOUNTING). */
export async function getBorcluAdaylar(): Promise<BorcluAday[]> {
  const res = await api.get<ApiResponse<BorcluAday[]>>('/api/reminders/candidates');
  return res.data.data;
}

/** Seçilen öğrencilerin velilerine hatırlatma gönderir. */
export async function hatirlatmaGonder(ogrenciIdleri: number[]): Promise<HatirlatmaSonucu> {
  const res = await api.post<ApiResponse<HatirlatmaSonucu>>('/api/reminders', { ogrenciIdleri });
  return res.data.data;
}
