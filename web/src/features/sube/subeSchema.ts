import { z } from 'zod';
import type { SubeInput } from '../../api/types';

/** İstemci doğrulaması backend'i aynalar: yalnızca ad zorunlu. */
export const subeSchema = z.object({
  ad: z.string().trim().min(1, 'Ad zorunludur').max(150, 'Ad en fazla 150 karakter olabilir'),
  adres: z.string().trim().max(500, 'Adres en fazla 500 karakter olabilir').optional(),
  telefon: z.string().trim().max(30, 'Telefon en fazla 30 karakter olabilir').optional(),
});

export type SubeFormValues = z.infer<typeof subeSchema>;

/** Form değerlerini API gövdesine çevirir: boş opsiyonel alanlar gönderilmez. */
export function toPayload(values: SubeFormValues): SubeInput {
  const adres = values.adres?.trim();
  const telefon = values.telefon?.trim();
  return {
    ad: values.ad.trim(),
    adres: adres ? adres : undefined,
    telefon: telefon ? telefon : undefined,
  };
}
