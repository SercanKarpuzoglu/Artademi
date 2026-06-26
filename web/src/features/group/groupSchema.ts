import { z } from 'zod';
import type { GroupInput, GrupTipi } from '../../api/types';

// Pozitif ondalik para degeri (string olarak girilir, virgul/nokta kabul).
const POSITIVE_DECIMAL = /^\d+([.,]\d+)?$/;

const optionalText = z.string().trim().optional();

/**
 * Istemci dogrulamasi backend'i aynalar: ad/tip/branş/öğretmen zorunlu; tipe gore
 * superRefine ile salon ve para alani zorunlu (hata ilgili alana baglanir, ki server
 * error.fields.salonId/aylikAidat/dersBasiUcret de ayni yere dussun):
 *  - GRUP: salonId zorunlu (>0) + aylikAidat zorunlu pozitif ondalik
 *  - OZEL: salonId opsiyonel + dersBasiUcret zorunlu pozitif ondalik
 */
export const groupSchema = z
  .object({
    ad: z.string().trim().min(1, 'Ad zorunludur'),
    tip: z.enum(['GRUP', 'OZEL'], { message: 'Tip zorunludur' }),
    // Model C: grubun hakediş tipi (zorunlu seçim; varsayilan tipe gore on-doldurulur).
    hakedisTipi: z.enum(['SAATLIK', 'CIRO_ORANI', 'OZEL_DERS'], {
      message: 'Hakediş tipi zorunludur',
    }),
    bransId: z.number({ message: 'Branş zorunludur' }).int().positive('Branş zorunludur'),
    ogretmenId: z
      .number({ message: 'Öğretmen zorunludur' })
      .int()
      .positive('Öğretmen zorunludur'),
    salonId: z.number().int().positive().optional(),
    seviye: optionalText,
    aylikAidat: optionalText,
    dersBasiUcret: optionalText,
  })
  .superRefine((data, ctx) => {
    if (data.tip === 'GRUP') {
      if (!data.salonId || data.salonId <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['salonId'],
          message: 'Salon zorunludur',
        });
      }
      const v = data.aylikAidat?.trim() ?? '';
      if (!v) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['aylikAidat'],
          message: 'Aylık aidat zorunludur',
        });
      } else if (!POSITIVE_DECIMAL.test(v) || Number(v.replace(',', '.')) <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['aylikAidat'],
          message: 'Geçerli, pozitif bir tutar giriniz',
        });
      }
    } else if (data.tip === 'OZEL') {
      const v = data.dersBasiUcret?.trim() ?? '';
      if (!v) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dersBasiUcret'],
          message: 'Ders başı ücret zorunludur',
        });
      } else if (!POSITIVE_DECIMAL.test(v) || Number(v.replace(',', '.')) <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dersBasiUcret'],
          message: 'Geçerli, pozitif bir tutar giriniz',
        });
      }
    }
  });

export type GroupFormValues = z.infer<typeof groupSchema>;

/** Para string'ini normalize eder: virgulu noktaya cevirir, trim eder. */
function normalizeMoney(v?: string): string {
  return (v ?? '').trim().replace(',', '.');
}

/**
 * Form degerlerini API govdesine cevirir: para (BigDecimal hassasiyeti) STRING gonderilir;
 * yalnizca tipe uygun para alani eklenir; salonId number veya gonderilmez; id'ler number.
 */
export function toPayload(values: GroupFormValues): GroupInput {
  const clean = (v?: string) => {
    const t = v?.trim();
    return t ? t : undefined;
  };
  const tip = values.tip as GrupTipi;
  return {
    ad: values.ad.trim(),
    tip,
    hakedisTipi: values.hakedisTipi,
    bransId: values.bransId,
    ogretmenId: values.ogretmenId,
    salonId: tip === 'GRUP' ? values.salonId : values.salonId || undefined,
    seviye: clean(values.seviye),
    aylikAidat: tip === 'GRUP' ? normalizeMoney(values.aylikAidat) : undefined,
    dersBasiUcret: tip === 'OZEL' ? normalizeMoney(values.dersBasiUcret) : undefined,
  };
}
