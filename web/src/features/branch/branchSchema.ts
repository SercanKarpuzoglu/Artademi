import { z } from 'zod';
import type { BranchInput } from '../../api/types';

/** Istemci dogrulamasi backend'i aynalar: yalnizca ad zorunlu. */
export const branchSchema = z.object({
  ad: z.string().trim().min(1, 'Ad zorunludur'),
  aciklama: z.string().trim().optional(),
});

export type BranchFormValues = z.infer<typeof branchSchema>;

/** Form degerlerini API govdesine cevirir: bos opsiyonel alanlar gonderilmez. */
export function toPayload(values: BranchFormValues): BranchInput {
  const aciklama = values.aciklama?.trim();
  return {
    ad: values.ad.trim(),
    aciklama: aciklama ? aciklama : undefined,
  };
}
