import { z } from 'zod';
import type { GroupInput, GrupTipi, HaftaGunu } from '../../api/types';
import { GUN_ORDER } from './scheduleDisplay';

// Pozitif ondalik para degeri (string olarak girilir, virgul/nokta kabul).
const POSITIVE_DECIMAL = /^\d+([.,]\d+)?$/;

const optionalText = z.string().trim().optional();

/**
 * Istemci dogrulamasi backend'i aynalar: ad/tip/branş/eğitmen zorunlu; tipe gore
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
      .number({ message: 'Eğitmen zorunludur' })
      .int()
      .positive('Eğitmen zorunludur'),
    salonId: z.number().int().positive().optional(),
    // Sube OPSIYONEL ve tipten BAGIMSIZ: salonu olmayan OZEL grubun da subesi olabilir.
    subeId: z.number().int().positive().optional(),
    seviye: optionalText,
    aylikAidat: optionalText,
    dersBasiUcret: optionalText,
    // Dalga E: dönem (opsiyonel) ve dönemlik ücret (GRUP; boşsa dönemlik kayıt yapılamaz).
    donemId: z.number().int().positive().optional(),
    donemlikUcret: optionalText,
    // Oluşturma anında ders saatleri (12 Eylül talebi). Düzenlemede kullanılmaz (detaydaki panel).
    dersSaatleri: z
      .array(
        z.object({
          gun: z.enum(GUN_ORDER as unknown as [HaftaGunu, ...HaftaGunu[]], { message: 'Gün zorunludur' }),
          baslangicSaati: z.string().min(1, 'Başlangıç zorunludur'),
          bitisSaati: z.string().min(1, 'Bitiş zorunludur'),
        }),
      )
      .optional(),
  })
  .superRefine((data, ctx) => {
    (data.dersSaatleri ?? []).forEach((d, i) => {
      if (d.baslangicSaati && d.bitisSaati && d.bitisSaati <= d.baslangicSaati) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dersSaatleri', i, 'bitisSaati'],
          message: 'Bitiş başlangıçtan sonra olmalı',
        });
      }
    });
    if (data.tip === 'GRUP') {
      if (!data.salonId || data.salonId <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['salonId'],
          message: 'Salon zorunludur',
        });
      }
      const d = data.donemlikUcret?.trim() ?? '';
      if (d && (!POSITIVE_DECIMAL.test(d) || Number(d.replace(',', '.')) <= 0)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['donemlikUcret'],
          message: 'Geçerli, pozitif bir tutar giriniz',
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
    subeId: values.subeId || undefined,
    seviye: clean(values.seviye),
    aylikAidat: tip === 'GRUP' ? normalizeMoney(values.aylikAidat) : undefined,
    dersBasiUcret: tip === 'OZEL' ? normalizeMoney(values.dersBasiUcret) : undefined,
    donemId: values.donemId || undefined,
    donemlikUcret: tip === 'GRUP' && values.donemlikUcret?.trim() ? normalizeMoney(values.donemlikUcret) : undefined,
    dersSaatleri: values.dersSaatleri && values.dersSaatleri.length > 0 ? values.dersSaatleri : undefined,
  };
}
