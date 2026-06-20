import { z } from 'zod';
import type { RoomInput } from '../../api/types';

/**
 * Istemci dogrulamasi backend'i aynalar: yalnizca ad zorunlu; kapasite opsiyonel ama
 * verilirse pozitif tam sayi. Bos string -> undefined.
 */
export const roomSchema = z.object({
  ad: z.string().trim().min(1, 'Ad zorunludur'),
  kapasite: z
    .union([z.literal(''), z.coerce.number().int('Tam sayı olmalıdır').positive('Pozitif olmalıdır')])
    .optional(),
  aciklama: z.string().trim().optional(),
});

export type RoomFormValues = z.infer<typeof roomSchema>;

/** Form degerlerini API govdesine cevirir: bos opsiyonel alanlar gonderilmez. */
export function toPayload(values: RoomFormValues): RoomInput {
  const aciklama = values.aciklama?.trim();
  const kapasite = typeof values.kapasite === 'number' ? values.kapasite : undefined;
  return {
    ad: values.ad.trim(),
    kapasite,
    aciklama: aciklama ? aciklama : undefined,
  };
}
