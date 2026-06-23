package com.artademi.platform.dto;

import java.util.List;

/** Platform konsolunda bir tenant'in kullanici satiri (SUPER_ADMIN gorur). */
public record TenantUserView(
        String id,
        String kullaniciAdi,
        String ad,
        String soyad,
        String email,
        String telefon,
        List<String> roller,
        boolean enabled) {
}
