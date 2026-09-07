package com.artademi.gizli;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kurumun gizli ayarlarini sifreli saklar ve okur.
 *
 * <h2>Kullanim kurali</h2>
 * <ul>
 *   <li>{@link #kaydet} / {@link #sil} — yonetici islemleri</li>
 *   <li>{@link #maskeliListe} — ARAYUZE gidecek olan; duz deger TASIMAZ</li>
 *   <li>{@link #oku} — <b>yalnizca sunucu ici entegrasyon kodu</b> (gonderim aninda).
 *       Bu metodun sonucu bir HTTP yanitina, log satirina veya hata mesajina ASLA konmaz.</li>
 * </ul>
 *
 * <p>Tenant izolasyonu {@code TenantAware} + global filtreden gelir: bir kurumun kimlik
 * bilgisi baska kurumun sorgusunda gorunmez.
 */
@Service
public class GizliAyarService {

    private final GizliAyarRepository repository;
    private final Sifreleme sifreleme;

    public GizliAyarService(GizliAyarRepository repository, Sifreleme sifreleme) {
        this.repository = repository;
        this.sifreleme = sifreleme;
    }

    /**
     * Degeri sifreleyip kaydeder; anahtar varsa gunceller.
     *
     * @throws IllegalStateException sifreleme anahtari yapilandirilmamissa (fail-closed —
     *     duz metin YAZILMAZ)
     */
    @Transactional
    public void kaydet(String anahtar, String duzDeger) {
        String sifreli = sifreleme.sifrele(duzDeger);
        String maske = Sifreleme.maskele(duzDeger);
        String kim = kullaniciAdi();

        repository.findByAnahtar(anahtar)
                .ifPresentOrElse(
                        mevcut -> mevcut.guncelle(sifreli, maske, kim),
                        () -> repository.save(GizliAyar.of(anahtar, sifreli, maske, kim)));
    }

    /**
     * ⚠️ DUZ degeri dondurur — yalnizca sunucu ici entegrasyon kullanimi icindir.
     *
     * <p>Kayit yoksa {@link Optional#empty()}. Kayit varsa ama cozulemiyorsa (kurcalanmis
     * veya anahtar degismis) istisna firlar; sessizce "yok" demeyiz, cunku o durumda
     * entegrasyon "hic yapilandirilmamis" gibi davranir ve gercek sorun gizlenir.
     */
    @Transactional(readOnly = true)
    public Optional<String> oku(String anahtar) {
        return repository.findByAnahtar(anahtar)
                .map(g -> sifreleme.coz(g.getDegerSifreli()));
    }

    /** Anahtar tanimli mi (degeri cozmeden). */
    @Transactional(readOnly = true)
    public boolean tanimliMi(String anahtar) {
        return repository.findByAnahtar(anahtar).isPresent();
    }

    /** Arayuze gidecek liste — duz deger TASIMAZ, yalnizca maske. */
    @Transactional(readOnly = true)
    public List<MaskeliAyar> maskeliListe() {
        return repository.findAllByOrderByAnahtarAsc().stream()
                .map(g -> new MaskeliAyar(g.getAnahtar(), g.getMaske(), g.getGuncelleyen(),
                        g.getGuncellenmeTarihi()))
                .toList();
    }

    @Transactional
    public void sil(String anahtar) {
        repository.deleteByAnahtar(anahtar);
    }

    /** Gizli ayarin arayuze acilan gorunumu. Duz deger BURADA YOKTUR. */
    public record MaskeliAyar(String anahtar, String maske, String guncelleyen,
            Instant guncellenmeTarihi) {
    }

    /** Zamanlanmis/sistem baglaminda kimlik olmayabilir; o zaman "sistem". */
    private static String kullaniciAdi() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("preferred_username");
            if (u != null && !u.isBlank()) {
                return u;
            }
        }
        return "sistem";
    }
}
