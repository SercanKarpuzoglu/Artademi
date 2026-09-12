import type { EnrollmentResponse } from '../../api/types';

/** Kayıt satırı plan rozeti: Aylık / Dönemlik · Güz / Deneme dersi (amber — dikkat çeksin). */
export function planRozeti(e: EnrollmentResponse): { metin: string; sinif: string } {
  if (e.odemePlani === 'DENEME') return { metin: 'Deneme dersi', sinif: 'b-amber' };
  if (e.odemePlani === 'DONEMLIK') return { metin: `Dönemlik${e.donem ? ` · ${e.donem.ad}` : ''}`, sinif: 'b-gray' };
  return { metin: 'Aylık', sinif: 'b-gray' };
}
