import type { EnrollmentDurumu, GrupTipi } from '../../api/types';

/** Grup tipi etiketi (liste/rozet icin). */
export const TIP_LABEL: Record<GrupTipi, string> = {
  GRUP: 'Grup',
  OZEL: 'Özel',
};

/** Grup tipi rozet rengi: GRUP ahududu, OZEL mavi. */
export const TIP_BADGE: Record<GrupTipi, string> = {
  GRUP: 'b-rasp',
  OZEL: 'b-blue',
};

/** Kayıt durumu etiketi. */
export const DURUM_LABEL: Record<EnrollmentDurumu, string> = {
  AKTIF: 'Aktif',
  AYRILDI: 'Ayrıldı',
};

/** Kayıt durumu rozet rengi: AKTIF yeşil, AYRILDI gri. */
export const DURUM_BADGE: Record<EnrollmentDurumu, string> = {
  AKTIF: 'b-green',
  AYRILDI: 'b-gray',
};
