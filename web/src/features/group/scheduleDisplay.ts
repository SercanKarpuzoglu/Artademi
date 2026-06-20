import type { HaftaGunu } from '../../api/types';

/** Haftanin gunu etiketi (Turkce). */
export const GUN_LABEL: Record<HaftaGunu, string> = {
  PAZARTESI: 'Pazartesi',
  SALI: 'Salı',
  CARSAMBA: 'Çarşamba',
  PERSEMBE: 'Perşembe',
  CUMA: 'Cuma',
  CUMARTESI: 'Cumartesi',
  PAZAR: 'Pazar',
};

/** Enum ordinal sirasi — siralama ve select option'lari icin. */
export const GUN_ORDER: readonly HaftaGunu[] = [
  'PAZARTESI',
  'SALI',
  'CARSAMBA',
  'PERSEMBE',
  'CUMA',
  'CUMARTESI',
  'PAZAR',
];

/** "HH:mm:ss" -> "HH:mm" (backend LocalTime string'inden ilk 5 karakter). */
export function toHm(time: string): string {
  return time.slice(0, 5);
}
