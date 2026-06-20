import type { HakedisTipi } from '../../api/types';

/** Hakediş tipi etiketi (liste/rozet icin). */
export const HAKEDIS_LABEL: Record<HakedisTipi, string> = {
  SAATLIK: 'Saatlik',
  CIRO_ORANI: 'Cirodan',
};

/** Hakediş tipi rozet rengi: SAATLIK mavi, CIRO_ORANI ahududu. */
export const HAKEDIS_BADGE: Record<HakedisTipi, string> = {
  SAATLIK: 'b-blue',
  CIRO_ORANI: 'b-rasp',
};

/** Para degerini (number|string|null) defansif bicimde gosterir; bos -> "—". */
export function formatMoney(v: string | number | null | undefined): string {
  if (v === null || v === undefined || v === '') {
    return '—';
  }
  const n = typeof v === 'number' ? v : Number(String(v).replace(',', '.'));
  if (Number.isNaN(n)) {
    return String(v);
  }
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
