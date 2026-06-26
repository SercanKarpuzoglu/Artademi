import type { HakedisTipi } from '../../api/types';

/** Hakediş tipi etiketi (liste/rozet icin). */
export const HAKEDIS_LABEL: Record<HakedisTipi, string> = {
  SAATLIK: 'Saatlik',
  CIRO_ORANI: 'Cirodan',
  OZEL_DERS: 'Ders Başı',
};

/** Hakediş tipi rozet rengi: SAATLIK mavi, CIRO_ORANI ahududu, OZEL_DERS erik. */
export const HAKEDIS_BADGE: Record<HakedisTipi, string> = {
  SAATLIK: 'b-blue',
  CIRO_ORANI: 'b-rasp',
  OZEL_DERS: 'b-plum',
};

// NOT: para bicimleme ortak `lib/format.ts` icindedir (formatMoney) — birden cok feature kullanir.
