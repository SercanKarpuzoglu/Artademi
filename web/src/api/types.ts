// Backend api-contract zarfinin TS karsiligi. Backend DTO'lari degisince burasi guncellenir.

export interface ApiError {
  code: string;
  message: string;
  fields?: Record<string, string> | null;
}

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: ApiError | null;
  meta: PageMeta | null;
}

export type StudentStatus = 'AKTIF' | 'PASIF' | 'DENEME' | 'DONDURULMUS';

/**
 * Olusturma/guncelleme istek govdesi — backend CreateStudentRequest/UpdateStudentRequest
 * ile birebir. status ve tenant_id GONDERILMEZ (yeni kayit DENEME; tenant JWT'den).
 * Opsiyonel alanlar bos ise gonderilmez (undefined) — backend @Pattern bos string'i reddeder.
 */
export interface StudentInput {
  ad: string;
  soyad: string;
  tcKimlikNo: string;
  dogumTarihi: string; // YYYY-MM-DD
  telefon?: string;
  yetiskinMi: boolean;
  anneAd?: string;
  anneTcKimlikNo?: string;
  anneTelefon?: string;
  babaAd?: string;
  babaTcKimlikNo?: string;
  babaTelefon?: string;
  veliMeslek?: string;
  evAdresi?: string;
  veliMail?: string;
}

/** Backend StudentResponse ile birebir aynalanir. Backend degisince burasi guncellenir. */
export interface StudentResponse {
  id: number;
  ad: string;
  soyad: string;
  tcKimlikNo: string;
  dogumTarihi: string; // ISO tarih (YYYY-MM-DD)
  telefon: string | null;
  yetiskinMi: boolean;
  status: StudentStatus;
  anneAd: string | null;
  anneTcKimlikNo: string | null;
  anneTelefon: string | null;
  babaAd: string | null;
  babaTcKimlikNo: string | null;
  babaTelefon: string | null;
  veliMeslek: string | null;
  evAdresi: string | null;
  veliMail: string | null;
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}
