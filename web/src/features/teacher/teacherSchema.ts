import { z } from 'zod';
import type { HakedisSatiriInput, HakedisTipi, TeacherInput } from '../../api/types';

// Pozitif ondalik para degeri (string olarak girilir, virgul/nokta kabul).
const POSITIVE_DECIMAL = /^\d+([.,]\d+)?$/;

const optionalText = z.string().trim().optional();

/** Form icindeki tek hakediş satiri (Model C). */
const hakedisRowSchema = z.object({
  tip: z.enum(['SAATLIK', 'CIRO_ORANI', 'OZEL_DERS']),
  saatlikUcret: optionalText,
  ciroOrani: optionalText,
  dersBasiUcret: optionalText,
});

export type HakedisRowValues = z.infer<typeof hakedisRowSchema>;

/**
 * Istemci dogrulamasi backend'i (Model C) aynalar: ad/soyad zorunlu; e-posta verilirse format;
 * hakedisler en az 1 satir, her tip &le;1 kez, her satirin tipiyle eslesen para alani zorunlu/gecerli.
 * Hatalar ilgili satir/alana baglanir (server error.fields.hakedisler / hakedisler[i].saatlikUcret
 * ile uyumlu).
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
    hakedisler: z.array(hakedisRowSchema),
    bransIds: z.array(z.number()),
  })
  .superRefine((data, ctx) => {
    if (data.hakedisler.length === 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['hakedisler'],
        message: 'En az bir hakediş tipi tanımlayın',
      });
      return;
    }

    const gorulen = new Set<HakedisTipi>();
    data.hakedisler.forEach((row, i) => {
      if (gorulen.has(row.tip)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['hakedisler', i, 'tip'],
          message: 'Aynı hakediş tipi birden fazla kez girilemez',
        });
        return;
      }
      gorulen.add(row.tip);

      if (row.tip === 'SAATLIK') {
        validatePositive(ctx, i, 'saatlikUcret', row.saatlikUcret, 'Saatlik ücret zorunludur');
      } else if (row.tip === 'OZEL_DERS') {
        validatePositive(ctx, i, 'dersBasiUcret', row.dersBasiUcret, 'Ders başı ücret zorunludur');
      } else if (row.tip === 'CIRO_ORANI') {
        const v = row.ciroOrani?.trim() ?? '';
        if (!v) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['hakedisler', i, 'ciroOrani'],
            message: 'Ciro oranı zorunludur',
          });
        } else {
          const n = Number(v.replace(',', '.'));
          if (!POSITIVE_DECIMAL.test(v) || n <= 0 || n > 100) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              path: ['hakedisler', i, 'ciroOrani'],
              message: '0 ile 100 arasında bir oran giriniz',
            });
          }
        }
      }
    });
  });

function validatePositive(
  ctx: z.RefinementCtx,
  index: number,
  field: 'saatlikUcret' | 'dersBasiUcret',
  value: string | undefined,
  emptyMsg: string,
): void {
  const v = value?.trim() ?? '';
  if (!v) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['hakedisler', index, field], message: emptyMsg });
  } else if (!POSITIVE_DECIMAL.test(v) || Number(v.replace(',', '.')) <= 0) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['hakedisler', index, field],
      message: 'Geçerli, pozitif bir tutar giriniz',
    });
  }
}

export type TeacherFormValues = z.infer<typeof teacherSchema>;

/** Para string'ini normalize eder: virgulu noktaya cevirir, trim eder. */
function normalizeMoney(v?: string): string {
  return (v ?? '').trim().replace(',', '.');
}

/**
 * Form degerlerini API govdesine cevirir: bos opsiyonel alanlar gonderilmez; para alanlari
 * (BigDecimal hassasiyeti) STRING gonderilir; her hakediş satirinda yalnizca tipe uygun alan eklenir.
 */
export function toPayload(values: TeacherFormValues): TeacherInput {
  const clean = (v?: string) => {
    const t = v?.trim();
    return t ? t : undefined;
  };
  const hakedisler: HakedisSatiriInput[] = values.hakedisler.map((row) => {
    switch (row.tip) {
      case 'SAATLIK':
        return { tip: 'SAATLIK', saatlikUcret: normalizeMoney(row.saatlikUcret) };
      case 'CIRO_ORANI':
        return { tip: 'CIRO_ORANI', ciroOrani: normalizeMoney(row.ciroOrani) };
      case 'OZEL_DERS':
        return { tip: 'OZEL_DERS', dersBasiUcret: normalizeMoney(row.dersBasiUcret) };
    }
  });
  return {
    ad: values.ad.trim(),
    soyad: values.soyad.trim(),
    telefon: clean(values.telefon),
    email: clean(values.email),
    keycloakUserId: clean(values.keycloakUserId),
    hakedisler,
    bransIds: values.bransIds,
  };
}
