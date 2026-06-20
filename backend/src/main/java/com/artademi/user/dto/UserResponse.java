package com.artademi.user.dto;

import java.util.List;

/**
 * Tenant kapsamli kullanici cikti temsili (/api/users). Keycloak id'si ({@code sub}) ile birlikte
 * temel profil alanlari ve yonetilebilir rolleri tasir.
 */
public record UserResponse(
        String id,
        String kullaniciAdi,
        String ad,
        String soyad,
        String email,
        String telefon,
        List<String> roller,
        boolean enabled) {
}
