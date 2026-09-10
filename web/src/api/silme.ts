import { api } from './client';
import type { ApiResponse } from './types';

/** Yumuşak silinebilen kayıt türleri — backend SilinebilirTur URL adları. */
export type SilinebilirTur =
  | 'ogrenci'
  | 'grup'
  | 'egitmen'
  | 'salon'
  | 'brans'
  | 'sube'
  | 'urun'
  | 'tedarikci'
  | 'kasa'
  | 'tahakkuk'
  | 'odeme'
  | 'gider'
  | 'satis'
  | 'paket'
  | 'telafi'
  | 'basvuru'
  | 'ders-saati'
  | 'yoklama-oturumu'
  | 'indirim';

export const TUR_ETIKET: Record<SilinebilirTur, string> = {
  ogrenci: 'Öğrenci',
  grup: 'Grup',
  egitmen: 'Eğitmen',
  salon: 'Salon',
  brans: 'Branş',
  sube: 'Şube',
  urun: 'Ürün',
  tedarikci: 'Tedarikçi',
  kasa: 'Kasa',
  tahakkuk: 'Tahakkuk',
  odeme: 'Ödeme',
  gider: 'Gider',
  satis: 'Satış',
  paket: 'Ders paketi',
  telafi: 'Telafi hakkı',
  basvuru: 'Başvuru',
  'ders-saati': 'Ders saati',
  'yoklama-oturumu': 'Yoklama oturumu',
  indirim: 'İndirim',
};

export interface SilmeOnizleme {
  tur: SilinebilirTur;
  id: number;
  ad: string;
  silinebilir: boolean;
  engel: string | null;
  etkiler: string[];
  bagliKayitlar: { ad: string; sayi: number }[];
}

export interface SilinenKayit {
  tur: SilinebilirTur;
  id: number;
  ad: string;
  silindiTarihi: string;
  silen: string | null;
}

/** Silme önizlemesi: engel / etkiler / bağlı kayıtlar (yalnız ADMIN). */
export async function silmeOnizle(tur: SilinebilirTur, id: number): Promise<SilmeOnizleme> {
  const res = await api.get<ApiResponse<SilmeOnizleme>>(`/api/silme/${tur}/${id}/onizleme`);
  return res.data.data;
}

/** Yumuşak sil (kayıt gizlenir, İşlem Kaydı'na düşer, geri alınabilir). Engel varsa 409 SILINEMEZ. */
export async function sil(tur: SilinebilirTur, id: number): Promise<void> {
  await api.delete(`/api/silme/${tur}/${id}`);
}

/** Silinen kayıtlar (geri alma listesi). */
export async function silinenler(tur: SilinebilirTur): Promise<SilinenKayit[]> {
  const res = await api.get<ApiResponse<SilinenKayit[]>>('/api/silme/silinenler', { params: { tur } });
  return res.data.data;
}

/** Geri al. */
export async function geriAl(tur: SilinebilirTur, id: number): Promise<void> {
  await api.post(`/api/silme/${tur}/${id}/geri-al`);
}
