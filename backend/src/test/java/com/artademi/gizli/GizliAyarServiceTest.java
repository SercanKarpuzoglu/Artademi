package com.artademi.gizli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.artademi.common.tenant.TenantContext;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Gizli ayar saklama — uctan uca.
 *
 * <p>En kritik iki sey: (1) veritabaninda DUZ METIN bulunmamasi, (2) bir kurumun kimlik
 * bilgisinin baska kurumdan GORUNMEMESI. Ikisi de sessiz basarisizliklardir: kod calisir
 * gorunur, koruma yoktur.
 */
@SpringBootTest(properties = {
    // 32 baytlik test anahtari (uretimde: openssl rand -base64 32)
    "artademi.sifreleme.anahtar=YXJ0YWRlbWktdGVzdC1zaWZyZWxlbWUtYW5haHRhcjM=",
})
@Testcontainers
class GizliAyarServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    GizliAyarService service;

    @Autowired
    GizliAyarRepository repository;

    @AfterEach
    void temizle() {
        TenantContext.clear();
    }

    private <T> T kurumda(UUID tenant, java.util.function.Supplier<T> is) {
        TenantContext.set(tenant);
        try {
            return is.get();
        } finally {
            TenantContext.clear();
        }
    }

    private void kurumda(UUID tenant, Runnable is) {
        kurumda(tenant, () -> {
            is.run();
            return null;
        });
    }

    /**
     * Her test KENDI kurumunu kullanir.
     *
     * <p>Sabit tenant paylasilsaydi testler birbirinin kaydini gorurdu ve sonuc CALISMA
     * SIRASINA baglı olurdu — nitekim ilk yazimda "1 bekleniyordu, 2 geldi" diye patladi.
     * Taze kurum, testleri hem sirasiz hem gercekci kilar (uretimde de her kurum kendi
     * verisiyle yalnizdir).
     */
    private static UUID yeniKurum() {
        return UUID.randomUUID();
    }

    @Test
    void kaydet_oku_gidisDonus() {
        UUID A = yeniKurum();
        kurumda(A, () -> service.kaydet("sms.netgsm.parola", "gizli-parola-9911"));

        String okunan = kurumda(A, () -> service.oku("sms.netgsm.parola").orElseThrow());
        assertThat(okunan).isEqualTo("gizli-parola-9911");
    }

    @Test
    void veritabaninda_DUZ_METIN_BULUNMAZ() {
        UUID A = yeniKurum();
        // ⚠️ Bu testin amaci: yedek sizarsa musteri hesaplari ele gecmesin.
        String duz = "cok-gizli-parola-4821";
        kurumda(A, () -> service.kaydet("sms.netgsm.parola", duz));

        String saklanan = kurumda(A,
                () -> repository.findByAnahtar("sms.netgsm.parola").orElseThrow().getDegerSifreli());

        assertThat(saklanan).doesNotContain(duz);
        assertThat(saklanan).startsWith("v1:");
    }

    @Test
    void maske_sadeceSon4Hane_tamDegerSAKLANMAZ() {
        UUID A = yeniKurum();
        kurumda(A, () -> service.kaydet("sms.netgsm.parola", "cok-gizli-parola-4821"));

        var liste = kurumda(A, () -> service.maskeliListe());
        assertThat(liste).hasSize(1);
        assertThat(liste.get(0).maske()).isEqualTo("••••4821");
        // Maskeli gorunumde duz deger TASINMAZ — kayit yalnizca anahtar+maske gosterir.
        assertThat(liste.get(0).anahtar()).isEqualTo("sms.netgsm.parola");
    }

    @Test
    void baskaKurumunAyari_GORUNMEZ() {
        UUID A = yeniKurum();
        UUID B = yeniKurum();
        kurumda(A, () -> service.kaydet("sms.netgsm.parola", "A-parolasi"));

        // B, A'nin kimlik bilgisini ne okuyabilir ne listeleyebilir.
        assertThat(kurumda(B, () -> service.oku("sms.netgsm.parola"))).isEmpty();
        assertThat(kurumda(B, () -> service.maskeliListe())).isEmpty();
        assertThat(kurumda(B, () -> service.tanimliMi("sms.netgsm.parola"))).isFalse();
    }

    @Test
    void ayniAnahtar_ikinciKayit_GUNCELLER_yeniSatirACMAZ() {
        UUID A = yeniKurum();
        kurumda(A, () -> service.kaydet("sms.netgsm.parola", "eski-deger-1111"));
        kurumda(A, () -> service.kaydet("sms.netgsm.parola", "yeni-deger-2222"));

        assertThat(kurumda(A, () -> service.oku("sms.netgsm.parola").orElseThrow()))
                .isEqualTo("yeni-deger-2222");
        // Ikinci satir olusursa hangisinin gecerli oldugu belirsiz kalirdi.
        assertThat(kurumda(A, () -> service.maskeliListe())).hasSize(1);
    }

    @Test
    void olmayanAnahtar_bosDoner() {
        UUID A = yeniKurum();
        assertThat(kurumda(A, () -> service.oku("hic.tanimlanmadi"))).isEmpty();
        assertThat(kurumda(A, () -> service.tanimliMi("hic.tanimlanmadi"))).isFalse();
    }

    @Test
    void sil_ayariKaldirir() {
        UUID A = yeniKurum();
        kurumda(A, () -> service.kaydet("sms.gecici", "deger"));
        kurumda(A, () -> service.sil("sms.gecici"));

        assertThat(kurumda(A, () -> service.oku("sms.gecici"))).isEmpty();
    }

    @Test
    void kurcalananKayit_SESSIZCE_gecilmez() {
        UUID A = yeniKurum();
        // Veritabanindaki deger elle bozulursa "yok" gibi davranmayiz; entegrasyonun
        // "hic yapilandirilmamis" sanmasi gercek sorunu gizlerdi.
        kurumda(A, () -> service.kaydet("sms.bozuk", "saglam-deger"));
        kurumda(A, () -> {
            GizliAyar g = repository.findByAnahtar("sms.bozuk").orElseThrow();
            g.guncelle("v1:" + Base64.getEncoder().encodeToString(new byte[12]) + ":"
                    + Base64.getEncoder().encodeToString(new byte[32]), "••••", "test");
            repository.save(g);
        });

        assertThatThrownBy(() -> kurumda(A, () -> service.oku("sms.bozuk")))
                .isInstanceOf(IllegalStateException.class);
    }
}
