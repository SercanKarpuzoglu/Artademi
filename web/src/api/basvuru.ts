import { api } from './client';
import { publicApi } from './publicClient';
import type {
  ApiResponse,
  BasvuruDurumu,
  BasvuruFormBilgisi,
  BasvuruGonderInput,
  BasvuruResponse,
  OgrenciyeDonusturInput,
} from './types';

// --- Public uçlar (kimlik YOK; publicApi kullanılır, Keycloak'a dokunulmaz) ---

/** Formun açılış bilgisi: kurum adı + branş seçenekleri. Slug geçersizse 404. */
export async function getBasvuruFormu(slug: string): Promise<BasvuruFormBilgisi> {
  const res = await publicApi.get<ApiResponse<BasvuruFormBilgisi>>(
    `/api/public/basvuru/${encodeURIComponent(slug)}`,
  );
  return res.data.data;
}

/** Başvuru gönderir. Honeypot/soğuma/mükerrer kuralları backend'dedir. */
export async function basvuruGonder(slug: string, payload: BasvuruGonderInput): Promise<void> {
  await publicApi.post(`/api/public/basvuru/${encodeURIComponent(slug)}`, payload);
}

// --- Kurum içi uçlar (kimlik gerekli; paylaşılan api) ---

export interface GetBasvurularParams {
  durum?: BasvuruDurumu;
  page?: number;
  size?: number;
}

/** Zarfin tamamini dondurur (data + meta) — diger liste uclariyla ayni sozlesme. */
export async function getBasvurular(
  params: GetBasvurularParams = {},
): Promise<ApiResponse<BasvuruResponse[]>> {
  const res = await api.get<ApiResponse<BasvuruResponse[]>>('/api/basvurular', { params });
  return res.data;
}

/** Menüdeki rozet için: ilgilenilmemiş başvuru sayısı. */
export async function getYeniBasvuruSayisi(): Promise<number> {
  const res = await api.get<ApiResponse<number>>('/api/basvurular/yeni-sayisi');
  return res.data.data;
}

export async function durumGuncelle(
  id: number,
  durum: BasvuruDurumu,
): Promise<BasvuruResponse> {
  const res = await api.patch<ApiResponse<BasvuruResponse>>(`/api/basvurular/${id}/durum`, {
    durum,
  });
  return res.data.data;
}

export async function ogrenciyeDonustur(
  id: number,
  payload: OgrenciyeDonusturInput,
): Promise<BasvuruResponse> {
  const res = await api.post<ApiResponse<BasvuruResponse>>(
    `/api/basvurular/${id}/ogrenciye-donustur`,
    payload,
  );
  return res.data.data;
}
