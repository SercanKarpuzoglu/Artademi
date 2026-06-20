import { z } from 'zod';
import type { HakedisTipi, TeacherInput } from '../../api/types';

// Pozitif ondalik para degeri (string olarak girilir, virgul/nokta kabul).
const POSITIVE_DECIMAL = /^\d+([.,]\d+)?$/;

const optionalText = z.string().trim().optional();

/**
 * Istemci dogrulamasi backend'i aynalar: ad/soyad/hakediş tipi zorunlu; e-posta verilirse format;
 * hakediş tipine gore ilgili para alani superRefine ile zorunlu (hata ilgili alana baglanir, ki
 * server error.fields.saatlikUcret/ciroOrani de ayni yere dussun).
 */
export const teacherSchema = z
  .object({
    ad: z.string().trim().min(1, 'Ad zorunludur'),
    soyad: z.string().trim().min(1, 'Soyad zorunludur'),
    telefon: optionalText,
    email: z
      .union([z.literal(''), z.string().trim().email('Geçerli bir e-posta giriniz')])
      .optional(),
    keycloakUserId: optionalText,
    hakedisTipi: z.enum(['SAATLIK', 'CIRO_ORANI'], { message: 'Hakediş tipi zorunludur' }),
    saatlikUcret: optionalText,
    ciroOrani: optionalText,
    bransIds: z.array(z.number()),
  })
  .superRefine((data, ctx) => {
    if (data.hakedisTipi === 'SAATLIK') {
      const v = data.saatlikUcret?.trim() ?? '';
      if (!v) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['saatlikUcret'],
          message: 'Saatlik ücret zorunludur',
        });
      } else if (!POSITIVE_DECIMAL.test(v) || Number(v.replace(',', '.')) <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['saatlikUcret'],
          message: 'Geçerli, pozitif bir tutar giriniz',
        });
      }
    } else if (data.hakedisTipi === 'CIRO_ORANI') {
      const v = data.ciroOrani?.trim() ?? '';
      if (!v) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['ciroOrani'],
          message: 'Ciro oranı zorunludur',
        });
      } else {
        const n = Number(v.replace(',', '.'));
        if (!POSITIVE_DECIMAL.test(v) || n <= 0 || n > 100) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['ciroOrani'],
            message: '0 ile 100 arasında bir oran giriniz',
          });
        }
      }
    }
  });

export type TeacherFormValues = z.infer<typeof teacherSchema>;

/** Para string'ini normalize eder: virgulu noktaya cevirir, trim eder. */
function normalizeMoney(v?: string): string {
  return (v ?? '').trim().replace(',', '.');
}

/**
 * Form degerlerini API govdesine cevirir: bos opsiyonel alanlar gonderilmez; para alani
 * (BigDecimal hassasiyeti) STRING gonderilir; yalnizca hakediş tipine uygun alan eklenir.
 */
export function toPayload(values: TeacherFormValues): TeacherInput {
  const clean = (v?: string) => {
    const t = v?.trim();
    return t ? t : undefined;
  };
  const hakedisTipi = values.hakedisTipi as HakedisTipi;
  return {
    ad: values.ad.trim(),
    soyad: values.soyad.trim(),
    telefon: clean(values.telefon),
    email: clean(values.email),
    keycloakUserId: clean(values.keycloakUserId),
    hakedisTipi,
    saatlikUcret: hakedisTipi === 'SAATLIK' ? normalizeMoney(values.saatlikUcret) : undefined,
    ciroOrani: hakedisTipi === 'CIRO_ORANI' ? normalizeMoney(values.ciroOrani) : undefined,
    bransIds: values.bransIds,
  };
}
