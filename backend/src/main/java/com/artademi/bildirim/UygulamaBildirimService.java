package com.artademi.bildirim;

import com.artademi.bildirim.dto.UygulamaBildirimiResponse;
import com.artademi.common.exception.NotFoundException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uygulama ici bildirim (Dalga C): uretim ({@link #gonder}) ve zil ({@link #zil}). Suzme kurali: satirin
 * {@code hedefKullanici}'si doluysa yalniz o sub gorur; degilse cagiranin rollerinden biri
 * {@code hedefRoller}'de olmali. Son 100 satir cekilir, bellekte suzulur (kucuk kurum; sorgu sabit).
 */
@Service
public class UygulamaBildirimService {

    /** Ofis hedefi: "yoklama alindi" gibi operasyon bildirimleri. */
    public static final String OFIS = "ADMIN,FRONTDESK,FRONTDESK_ACCOUNTING";
    /** Zilin gosterdigi azami satir. */
    static final int ZIL_SATIR = 30;
    private static final int TARAMA = 100;

    private final UygulamaBildirimiRepository repository;

    public UygulamaBildirimService(UygulamaBildirimiRepository repository) {
        this.repository = repository;
    }

    /** Bildirim uret. Cagiran islem icinde olmali (tenant baglami) — servisler icinden cagrilir. */
    @Transactional
    public UygulamaBildirimi gonder(UygulamaBildirimTipi tip, String hedefRoller, String hedefKullanici,
            String baslik, String metin, String baglanti) {
        return repository.save(UygulamaBildirimi.of(tip, hedefRoller, hedefKullanici, baslik, metin, baglanti));
    }

    @Transactional(readOnly = true)
    public UygulamaBildirimiResponse zil() {
        Set<String> roller = roller();
        String sub = sub();
        List<UygulamaBildirimi> benim = repository.sonBildirimler(PageRequest.of(0, TARAMA)).stream()
                .filter(b -> gorur(b, roller, sub))
                .toList();
        long okunmamis = benim.stream().filter(b -> b.getOkunduTarihi() == null).count();
        return new UygulamaBildirimiResponse(okunmamis,
                benim.stream().limit(ZIL_SATIR).map(UygulamaBildirimiResponse.Satir::from).toList());
    }

    @Transactional
    public void okundu(Long id) {
        UygulamaBildirimi b = repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Bildirim bulunamadı: " + id));
        if (!gorur(b, roller(), sub())) {
            throw new NotFoundException("Bildirim bulunamadı: " + id);
        }
        if (b.getOkunduTarihi() == null) {
            b.setOkunduTarihi(Instant.now());
        }
    }

    @Transactional
    public int hepsiniOkundu() {
        Set<String> roller = roller();
        String sub = sub();
        int n = 0;
        for (UygulamaBildirimi b : repository.sonBildirimler(PageRequest.of(0, TARAMA))) {
            if (b.getOkunduTarihi() == null && gorur(b, roller, sub)) {
                b.setOkunduTarihi(Instant.now());
                n++;
            }
        }
        return n;
    }

    private static boolean gorur(UygulamaBildirimi b, Set<String> roller, String sub) {
        if (b.getHedefKullanici() != null && !b.getHedefKullanici().isBlank()) {
            return b.getHedefKullanici().equals(sub);
        }
        return Arrays.stream(b.getHedefRoller().split(",")).map(String::trim).anyMatch(roller::contains);
    }

    private static Set<String> roller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toSet());
    }

    private static String sub() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null;
    }
}
