import { z } from 'zod';
import type { StudentInput } from '../../api/types';

// Opsiyonel metin: bos string'e izin verir. Gonderim oncesi bos'lar undefined'a cevrilir.
const optionalText = z.string().trim().optional();
// Opsiyonel TC: bos VEYA tam 11 hane (backend @Pattern ile ayni).
const optionalTc = z
  .union([z.literal(''), z.string().regex(/^\d{11}$/, '11 haneli olmalıdır')])
  .optional();

/**
 * Istemci dogrulamasi backend'i aynalar: zorunlu alanlar + TC 11 hane + veli kurali
 * (yetiskin degilse anne VEYA baba icin ad+TC). Veli kurali hatasi 'veli' yoluna baglanir
 * (backend error.fields.veli ile ayni).
 */
export const studentSchema = z
  .object({
    ad: z.string().trim().min(1, 'Ad zorunludur'),
    soyad: z.string().trim().min(1, 'Soyad zorunludur'),
    tcKimlikNo: z.string().regex(/^\d{11}$/, 'TC kimlik no 11 haneli olmalıdır'),
    dogumTarihi: z.string().min(1, 'Doğum tarihi zorunludur'),
    telefon: optionalText,
    yetiskinMi: z.boolean(),
    anneAd: optionalText,
    anneTcKimlikNo: optionalTc,
    anneTelefon: optionalText,
    babaAd: optionalText,
    babaTcKimlikNo: optionalTc,
    babaTelefon: optionalText,
    veliMeslek: optionalText,
    evAdresi: optionalText,
    veliMail: optionalText,
  })
  .superRefine((data, ctx) => {
    if (data.yetiskinMi) {
      return;
    }
    const anneTam = Boolean(data.anneAd?.trim()) && Boolean(data.anneTcKimlikNo?.trim());
    const babaTam = Boolean(data.babaAd?.trim()) && Boolean(data.babaTcKimlikNo?.trim());
    if (!anneTam && !babaTam) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['veli'],
        message: 'Yetişkin değilse en az bir veli (ad ve TC) zorunludur',
      });
    }
  });

export type StudentFormValues = z.infer<typeof studentSchema>;

/** Form degerlerini API govdesine cevirir: bos opsiyonel alanlar gonderilmez (undefined). */
export function toPayload(values: StudentFormValues): StudentInput {
  const clean = (v?: string) => {
    const t = v?.trim();
    return t ? t : undefined;
  };
  return {
    ad: values.ad.trim(),
    soyad: values.soyad.trim(),
    tcKimlikNo: values.tcKimlikNo.trim(),
    dogumTarihi: values.dogumTarihi,
    yetiskinMi: values.yetiskinMi,
    telefon: clean(values.telefon),
    anneAd: clean(values.anneAd),
    anneTcKimlikNo: clean(values.anneTcKimlikNo),
    anneTelefon: clean(values.anneTelefon),
    babaAd: clean(values.babaAd),
    babaTcKimlikNo: clean(values.babaTcKimlikNo),
    babaTelefon: clean(values.babaTelefon),
    veliMeslek: clean(values.veliMeslek),
    evAdresi: clean(values.evAdresi),
    veliMail: clean(values.veliMail),
  };
}
