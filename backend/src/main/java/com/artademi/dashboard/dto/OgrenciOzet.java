package com.artademi.dashboard.dto;

import com.artademi.student.StudentStatus;
import java.time.Instant;

/** Son kayitli ogrenci ozeti (parasal alan icermez; her rol gorebilir). */
public record OgrenciOzet(String ad, String soyad, StudentStatus statu, Instant kayitTarihi) {
}
