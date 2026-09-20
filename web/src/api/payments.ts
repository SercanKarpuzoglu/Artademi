import { api } from './client';
import type {
  ApiResponse,
  IadeInput,
  IadeOnizleme,
  OdemeYontemi,
  PaymentInput,
  PaymentResponse,
} from './types';

export interface GetPaymentsParams {
  ogrenciId?: number;
  from?: string;
  to?: string;
  yontem?: OdemeYontemi;
  page?: number;
  size?: number;
}

/** Ödeme listesi (sayfali). Zarfin tamamini dondurur (data + meta). Tenant JWT'den okunur. */
export async function getPayments(
  params: GetPaymentsParams = {},
): Promise<ApiResponse<PaymentResponse[]>> {
  const res = await api.get<ApiResponse<PaymentResponse[]>>('/api/payments', { params });
  return res.data;
}

/**
 * Tahsilat makbuzunu PDF olarak indirir. Dosya adi backend'in Content-Disposition
 * basligindan okunur — ad uretim mantigi TEK YERDE (backend) kalsin.
 */
export async function indirMakbuz(id: number): Promise<{ blob: Blob; dosyaAdi: string }> {
  const res = await api.get(`/api/payments/${id}/makbuz.pdf`, { responseType: 'blob' });
  const disposition = String(res.headers['content-disposition'] ?? '');
  const eslesme = disposition.match(/filename="?([^"]+)"?/);
  return { blob: res.data as Blob, dosyaAdi: eslesme?.[1] ?? `makbuz_${id}.pdf` };
}

/** Yeni ödeme olusturur. */
export async function createPayment(payload: PaymentInput): Promise<PaymentResponse> {
  const res = await api.post<ApiResponse<PaymentResponse>>('/api/payments', payload);
  return res.data.data;
}

/**
 * Tahsilat iadesi (ADMIN + muhasebe). Orijinal satira dokunulmaz; NEGATIF tutarli yeni satir
 * yazilir ve ogrencinin KALAN KREDISI iptal edilir. Sinir asimi -> 400 (error.fields.tutar).
 */
export async function iadeEt(odemeId: number, payload: IadeInput): Promise<PaymentResponse> {
  const res = await api.post<ApiResponse<PaymentResponse>>(`/api/payments/${odemeId}/iade`, payload);
  return res.data.data;
}

/** Iade onay ekraninin ozeti: ne kadar iade edilebilir, kac kontor iptal olur, engel var mi. */
export async function getIadeOnizleme(odemeId: number): Promise<IadeOnizleme> {
  const res = await api.get<ApiResponse<IadeOnizleme>>(`/api/payments/${odemeId}/iade-onizleme`);
  return res.data.data;
}
