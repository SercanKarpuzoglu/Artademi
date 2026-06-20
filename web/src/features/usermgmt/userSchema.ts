import { z } from 'zod';
import type { CreateUserInput, UpdateUserInput } from '../../api/types';

/**
 * İstemci doğrulaması backend'i aynalar: kullaniciAdi/ad/soyad zorunlu, email opsiyonel ama
 * verilirse format, en az bir rol. SUPER_ADMIN form seçeneklerinde hiç yer almaz (userDisplay).
 */
export const userSchema = z.object({
  kullaniciAdi: z.string().trim().min(1, 'Kullanıcı adı zorunludur'),
  ad: z.string().trim().min(1, 'Ad zorunludur'),
  soyad: z.string().trim().min(1, 'Soyad zorunludur'),
  email: z
    .string()
    .trim()
    .optional()
    .refine((v) => !v || z.string().email().safeParse(v).success, 'Geçerli bir e-posta girin'),
  telefon: z.string().trim().optional(),
  roller: z.array(z.string()).min(1, 'En az bir rol seçin'),
});

export type UserFormValues = z.infer<typeof userSchema>;

/** Create gövdesi: boş opsiyonel metinler gönderilmez. */
export function toCreatePayload(values: UserFormValues): CreateUserInput {
  const email = values.email?.trim();
  const telefon = values.telefon?.trim();
  return {
    kullaniciAdi: values.kullaniciAdi.trim(),
    ad: values.ad.trim(),
    soyad: values.soyad.trim(),
    email: email ? email : undefined,
    telefon: telefon ? telefon : undefined,
    roller: values.roller,
  };
}

/** Update gövdesi: kullaniciAdi gönderilmez (backend PUT almaz). */
export function toUpdatePayload(values: UserFormValues): UpdateUserInput {
  const email = values.email?.trim();
  const telefon = values.telefon?.trim();
  return {
    ad: values.ad.trim(),
    soyad: values.soyad.trim(),
    email: email ? email : undefined,
    telefon: telefon ? telefon : undefined,
    roller: values.roller,
  };
}
