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
