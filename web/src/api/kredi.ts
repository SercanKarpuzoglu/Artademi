import { api } from './client';
import type { ApiResponse, KayitOnizleme, OdemePlani } from './types';

/** Gruba yazarken plan seçimi için hesap: ders sayısı, ücret, dönem. uygun=false ise neden döner. */
export async function getKayitOnizleme(
  grupId: number,
  plan: OdemePlani,
  tarih?: string,
): Promise<KayitOnizleme> {
  const res = await api.get<ApiResponse<KayitOnizleme>>(`/api/groups/${grupId}/kayit-onizleme`, {
    params: { plan, tarih },
  });
  return res.data.data;
}
