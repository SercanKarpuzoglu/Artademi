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
        boolean enabled,
        /**
         * YALNIZCA olusturma yanitinda ve YALNIZCA e-postasi olmayan kullanicida dolu.
         * E-postasi olan kullaniciya parola belirleme baglantisi mail ile gider; o durumda
         * hicbir yerde duz metin parola bulunmaz ve bu alan {@code null} kalir.
         * Liste/detay uclarinda HER ZAMAN {@code null}.
         */
        String ilkParola) {

    /** Olusturma yanitinda tek seferlik parolayi ekler. */
    public UserResponse withIlkParola(String parola) {
        return new UserResponse(id, kullaniciAdi, ad, soyad, email, telefon, roller, enabled,
                parola);
    }
}
