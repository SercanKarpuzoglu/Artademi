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

// --- Tanımlar modülü (Branş / Salon / Öğretmen) — backend DTO'lari ile birebir aynalanir ---

/** Branş yaniti — backend BranchResponse. */
export interface BranchResponse {
  id: number;
  ad: string;
  aciklama: string | null;
  aktif: boolean;
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

/** Branş olusturma/guncelleme govdesi. Bos opsiyonel alanlar gonderilmez. */
export interface BranchInput {
  ad: string;
  aciklama?: string;
}

/** Salon yaniti — backend RoomResponse. */
export interface RoomResponse {
  id: number;
  ad: string;
  kapasite: number | null;
  aciklama: string | null;
  aktif: boolean;
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

/** Salon olusturma/guncelleme govdesi. */
export interface RoomInput {
  ad: string;
  kapasite?: number;
  aciklama?: string;
}

/** Öğretmen hakediş tipi. */
export type HakedisTipi = 'SAATLIK' | 'CIRO_ORANI';

/** Öğretmenin bağlı olduğu branş referansı (özet). */
export interface TeacherBranchRef {
  id: number;
  ad: string;
}

/** Öğretmen yaniti — backend TeacherResponse. Para alanlari number VEYA string gelebilir. */
export interface TeacherResponse {
  id: number;
  ad: string;
  soyad: string;
  telefon: string | null;
  email: string | null;
  keycloakUserId: string | null;
  hakedisTipi: HakedisTipi;
  saatlikUcret: string | number | null;
  ciroOrani: string | number | null;
  aktif: boolean;
  branslar: TeacherBranchRef[];
  olusturulmaTarihi: string;
  guncellenmeTarihi: string;
}

/**
 * Öğretmen olusturma/guncelleme govdesi. Para alanlari (saatlikUcret/ciroOrani) BigDecimal
 * hassasiyetini korumak icin STRING gonderilir; hakediş tipine gore yalnizca ilgili alan eklenir.
 */
export interface TeacherInput {
  ad: string;
  soyad: string;
  telefon?: string;
  email?: string;
  keycloakUserId?: string;
  hakedisTipi: HakedisTipi;
  saatlikUcret?: string;
  ciroOrani?: string;
  bransIds: number[];
}
