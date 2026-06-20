import type { OdemeYontemi } from '../../api/types';

/** Ödeme yöntemi etiketi (liste/rozet icin). */
export const YONTEM_LABEL: Record<OdemeYontemi, string> = {
  NAKIT: 'Nakit',
  KART: 'Kart',
  HAVALE: 'Havale',
};

/** Ödeme yöntemi rozet rengi: NAKIT gri, KART mavi, HAVALE yeşil. */
export const YONTEM_BADGE: Record<OdemeYontemi, string> = {
  NAKIT: 'b-gray',
  KART: 'b-blue',
  HAVALE: 'b-green',
};
