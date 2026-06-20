import type { YoklamaDurumu } from '../../api/types';

/** Yoklama durumu -> chip CSS sinifi (in/out/exc). */
export const DURUM_CHIP: Record<YoklamaDurumu, string> = {
  GELDI: 'in',
  GELMEDI: 'out',
  IZINLI: 'exc',
};

/** Yoklama durumu etiketi (Turkce). */
export const DURUM_LABEL: Record<YoklamaDurumu, string> = {
  GELDI: 'Geldi',
  GELMEDI: 'Gelmedi',
  IZINLI: 'İzinli',
};

/** Durum dongusu (NO bos state): GELMEDI -> GELDI -> IZINLI -> GELMEDI. */
export function nextDurum(d: YoklamaDurumu): YoklamaDurumu {
  switch (d) {
    case 'GELMEDI':
      return 'GELDI';
    case 'GELDI':
      return 'IZINLI';
    case 'IZINLI':
    default:
      return 'GELMEDI';
  }
}
