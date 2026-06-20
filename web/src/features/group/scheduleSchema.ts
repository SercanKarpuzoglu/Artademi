import { z } from 'zod';
import type { HaftaGunu, ScheduleInput } from '../../api/types';
import { GUN_ORDER } from './scheduleDisplay';

/**
 * Istemci dogrulamasi backend'i aynalar: gun + baslangic/bitis zorunlu;
 * bitis > baslangic kurali superRefine ile 'bitisSaati' alanina baglanir, ki sunucu
 * @SaatAraligiGecerli ile error.fields.bitisSaati de ayni yere dussun.
 */
export const scheduleSchema = z
  .object({
    gun: z.enum(GUN_ORDER as unknown as [HaftaGunu, ...HaftaGunu[]], {
      message: 'Gün zorunludur',
    }),
    baslangicSaati: z.string().min(1, 'Başlangıç saati zorunludur'),
    bitisSaati: z.string().min(1, 'Bitiş saati zorunludur'),
  })
  .superRefine((data, ctx) => {
    if (data.baslangicSaati && data.bitisSaati && data.bitisSaati <= data.baslangicSaati) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['bitisSaati'],
        message: 'Bitiş saati başlangıçtan sonra olmalıdır',
      });
    }
  });

export type ScheduleFormValues = z.infer<typeof scheduleSchema>;

/** Form degerlerini API govdesine cevirir (grupId disaridan eklenir). */
export function toPayload(grupId: number, values: ScheduleFormValues): ScheduleInput {
  return {
    grupId,
    gun: values.gun,
    baslangicSaati: values.baslangicSaati,
    bitisSaati: values.bitisSaati,
  };
}
