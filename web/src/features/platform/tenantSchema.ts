import { z } from 'zod';

/**
 * Tenant olusturma formu dogrulamasi — backend CreateTenantRequest'i aynalar
 * (ad/adminAd/adminSoyad zorunlu, adminEmail zorunlu + email format).
 */
export const tenantSchema = z.object({
  ad: z.string().min(1, 'Kurum adı zorunludur'),
  adminEmail: z
    .string()
    .min(1, 'Yönetici e-postası zorunludur')
    .email('Geçerli bir e-posta giriniz'),
  adminAd: z.string().min(1, 'Yönetici adı zorunludur'),
  adminSoyad: z.string().min(1, 'Yönetici soyadı zorunludur'),
});

export type TenantFormValues = z.infer<typeof tenantSchema>;
