package com.artademi.student.dto;

import com.artademi.student.Student;
import com.artademi.student.StudentStatus;

/**
 * Request DTO'larini Student entity'sine yansitir. tenant_id ve status BURADA ELLE
 * yonetilmez: tenant @PrePersist'te TenantContext'ten gelir, status ise serviste
 * (yeni kayitta DENEME, degisiklikte PATCH endpoint'i) yonetilir.
 */
public final class StudentMapper {

    private StudentMapper() {
    }

    /** Yeni ogrenci olusturur; status DENEME ile baslar. */
    public static Student toNewEntity(CreateStudentRequest req) {
        Student s = Student.create();
        s.setAd(req.ad());
        s.setSoyad(req.soyad());
        s.setTcKimlikNo(req.tcKimlikNo());
        s.setDogumTarihi(req.dogumTarihi());
        s.setTelefon(req.telefon());
        s.setYetiskinMi(req.yetiskinMi());
        s.setStatus(StudentStatus.DENEME);
        s.setAnneAd(req.anneAd());
        s.setAnneTcKimlikNo(req.anneTcKimlikNo());
        s.setAnneTelefon(req.anneTelefon());
        s.setBabaAd(req.babaAd());
        s.setBabaTcKimlikNo(req.babaTcKimlikNo());
        s.setBabaTelefon(req.babaTelefon());
        s.setVeliMeslek(req.veliMeslek());
        s.setEvAdresi(req.evAdresi());
        s.setVeliMail(req.veliMail());
        return s;
    }

    /** Mevcut ogrencinin alanlarini gunceller; status'a DOKUNMAZ. */
    public static void applyUpdate(Student s, UpdateStudentRequest req) {
        s.setAd(req.ad());
        s.setSoyad(req.soyad());
        s.setTcKimlikNo(req.tcKimlikNo());
        s.setDogumTarihi(req.dogumTarihi());
        s.setTelefon(req.telefon());
        s.setYetiskinMi(req.yetiskinMi());
        s.setAnneAd(req.anneAd());
        s.setAnneTcKimlikNo(req.anneTcKimlikNo());
        s.setAnneTelefon(req.anneTelefon());
        s.setBabaAd(req.babaAd());
        s.setBabaTcKimlikNo(req.babaTcKimlikNo());
        s.setBabaTelefon(req.babaTelefon());
        s.setVeliMeslek(req.veliMeslek());
        s.setEvAdresi(req.evAdresi());
        s.setVeliMail(req.veliMail());
    }
}
