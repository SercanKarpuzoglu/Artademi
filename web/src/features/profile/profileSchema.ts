import { z } from 'zod';
import type { UpdateMeInput } from '../../api/types';

/** Profil bilgileri formu — ad/soyad zorunlu, email opsiyonel ama verilirse format. */
export const profileSchema = z.object({
  ad: z.string().trim().min(1, 'Ad zorunludur'),
  soyad: z.string().trim().min(1, 'Soyad zorunludur'),
  email: z
    .string()
    .trim()
    .optional()
    .refine((v) => !v || z.string().email().safeParse(v).success, 'Geçerli bir e-posta girin'),
  telefon: z.string().trim().optional(),
});

export type ProfileFormValues = z.infer<typeof profileSchema>;

/** Profil gövdesi: boş opsiyonel metinler gönderilmez. */
export function toUpdateMePayload(values: ProfileFormValues): UpdateMeInput {
  const email = values.email?.trim();
  const telefon = values.telefon?.trim();
  return {
    ad: values.ad.trim(),
    soyad: values.soyad.trim(),
    email: email ? email : undefined,
    telefon: telefon ? telefon : undefined,
  };
}

/**
 * Şifre değiştirme formu — yeni parola en az 8 karakter, tekrar ile eşleşmeli.
 * "Mevcut parola hatalı" backend'den 400 VALIDATION_ERROR ile gelir (mevcutParola altına basılır).
 */
export const changePasswordSchema = z
  .object({
    mevcutParola: z.string().min(1, 'Mevcut parola zorunludur'),
    yeniParola: z.string().min(8, 'En az 8 karakter olmalı'),
    yeniParolaTekrar: z.string().min(1, 'Tekrar zorunludur'),
  })
  .superRefine((v, ctx) => {
    if (v.yeniParola !== v.yeniParolaTekrar) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['yeniParolaTekrar'],
        message: 'Parolalar eşleşmiyor',
      });
    }
  });

export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;
