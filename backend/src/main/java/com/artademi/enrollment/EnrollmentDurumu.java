package com.artademi.enrollment;

/**
 * Kaydin (enrollment) durumu. Yeni kayit {@link #AKTIF} baslar; ayrilma silme yerine
 * {@link #AYRILDI} ile yapilir (veri korunur, ayrilma_tarihi set edilir).
 */
public enum EnrollmentDurumu {

    /** Ogrenci grupta aktif kayitli. Yeni kaydin varsayilani. */
    AKTIF,

    /** Ogrenci gruptan ayrildi. Kayit silinmez, bu duruma alinir. */
    AYRILDI
}
