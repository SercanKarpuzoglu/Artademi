import { api } from './client';

/**
 * Kurumun tüm verisini ZIP olarak indirir (KVKK veri taşınabilirliği, SADECE ADMIN).
 *
 * ⚠️ Yanıt JSON zarfı DEĞİL, ikili (binary) dosyadır — bu yüzden `responseType: 'blob'`
 * ve zarfı açan interceptor'ın karışmaması için ham yanıt kullanılır.
 */
export async function veriDisaAktar(): Promise<{ blob: Blob; dosyaAdi: string }> {
  const res = await api.get('/api/export', { responseType: 'blob' });

  // Dosya adı Content-Disposition başlığından gelir; okunamazsa makul bir varsayılan.
  const disposition = String(res.headers['content-disposition'] ?? '');
  const eslesme = disposition.match(/filename="?([^"]+)"?/);
  return {
    blob: res.data as Blob,
    dosyaAdi: eslesme?.[1] ?? 'artademi-veri.zip',
  };
}
