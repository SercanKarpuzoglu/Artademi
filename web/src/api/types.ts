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

/**
 * Backend StudentResponse ile aynalanir. Iskelet asamasinda yalnizca temel alanlar;
 * ogrenci ekraninda (3b/3c) tam tip kullanilacak.
 */
export interface StudentResponse {
  id: number;
  ad: string;
  soyad: string;
  tcKimlikNo: string;
  status: string;
}
