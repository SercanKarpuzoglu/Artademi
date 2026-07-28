import { z } from 'zod';
import type { CheckoutStartInput } from '../../api/types';

/** Checkout fatura bilgileri — backend CheckoutStartRequest doğrulamasını aynalar. */
export const billingSchema = z.object({
  ad: z.string().trim().min(1, 'Ad zorunludur'),
  soyad: z.string().trim().min(1, 'Soyad zorunludur'),
  email: z.string().trim().min(1, 'E-posta zorunludur').email('Geçerli bir e-posta girin'),
  telefon: z.string().trim().min(1, 'Telefon zorunludur'),
  kimlikVergiNo: z
    .string()
    .trim()
    .regex(/^\d{10,11}$/, '10 haneli VKN veya 11 haneli TCKN girin'),
  adres: z.string().trim().min(1, 'Fatura adresi zorunludur'),
  sehir: z.string().trim().min(1, 'Şehir zorunludur'),
});

export type BillingFormValues = z.infer<typeof billingSchema>;

export function toCheckoutPayload(values: BillingFormValues): CheckoutStartInput {
  return {
    ad: values.ad.trim(),
    soyad: values.soyad.trim(),
    email: values.email.trim(),
    telefon: values.telefon.trim(),
    kimlikVergiNo: values.kimlikVergiNo.trim(),
    adres: values.adres.trim(),
    sehir: values.sehir.trim(),
  };
}
