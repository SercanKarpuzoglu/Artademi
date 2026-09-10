package com.artademi.student.dto;

import com.artademi.student.StudentStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ogrenci LISTESI satiri (GET /api/students/liste) — Dalga B: ad soyad, gruplar, statu, odeme durumu,
 * devam durumu, kara liste. Tam DTO yerine listeye ozel; toplu sorgularla doldurulur (N+1 yok).
 *
 * <ul>
 *   <li>{@code bakiye} — tahakkuk − odeme (pozitif = borc). PARASAL: yalnizca ADMIN /
 *       FRONTDESK_ACCOUNTING icin dolu; on buro icin NULL (hic gonderilmez).</li>
 *   <li>{@code devamsizlikSerisi} — en son yoklamadan geriye ardisik GELMEDI sayisi, NEGATIF
 *       ("-2 = 2 derstir gelmemis"); son yoklama GELDI/IZINLI ise 0; hic yoklama yoksa null.
 *       Pencere: son 90 gun.</li>
 * </ul>
 */
public record StudentListeSatiri(
        Long id,
        String ad,
        String soyad,
        String tcKimlikNo,
        StudentStatus status,
        boolean karaListe,
        String karaListeAciklama,
        List<GrupRef> gruplar,
        BigDecimal bakiye,
        Integer devamsizlikSerisi) {

    /** Aktif kayitli oldugu grup (id + ad). */
    public record GrupRef(Long id, String ad) {
    }
}
