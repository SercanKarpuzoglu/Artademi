import type { PayoutDurumu } from '../../api/types';

/** Hakediş durumu etiketi (liste/rozet icin). */
export const DURUM_LABEL: Record<PayoutDurumu, string> = {
  HESAPLANDI: 'Hesaplandı',
  ODENDI: 'Ödendi',
};

/** Hakediş durumu rozet rengi: HESAPLANDI amber, ODENDI yeşil. */
export const DURUM_BADGE: Record<PayoutDurumu, string> = {
  HESAPLANDI: 'b-amber',
  ODENDI: 'b-green',
};

// NOT: hakediş tipi etiket/rozet `teacherDisplay.ts` (HAKEDIS_*) icinde — burada tekrar tanimlanmaz.
// Para bicimleme ortak `lib/format.ts` (formatMoney).
