package com.artademi.student.dto;

import com.artademi.student.Student;
import com.artademi.student.StudentStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Ogrenci yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 */
public record StudentResponse(
        Long id,
        String ad,
        String soyad,
        String tcKimlikNo,
        LocalDate dogumTarihi,
        String telefon,
        boolean yetiskinMi,
        StudentStatus status,
        String anneAd,
        String anneTcKimlikNo,
        String anneTelefon,
        String babaAd,
        String babaTcKimlikNo,
        String babaTelefon,
        String veliMeslek,
        String evAdresi,
        String veliMail,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    public static StudentResponse from(Student s) {
        return new StudentResponse(
                s.getId(),
                s.getAd(),
                s.getSoyad(),
                s.getTcKimlikNo(),
                s.getDogumTarihi(),
                s.getTelefon(),
                s.isYetiskinMi(),
                s.getStatus(),
                s.getAnneAd(),
                s.getAnneTcKimlikNo(),
                s.getAnneTelefon(),
                s.getBabaAd(),
                s.getBabaTcKimlikNo(),
                s.getBabaTelefon(),
                s.getVeliMeslek(),
                s.getEvAdresi(),
                s.getVeliMail(),
                s.getOlusturulmaTarihi(),
                s.getGuncellenmeTarihi());
    }
}
