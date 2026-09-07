package com.artademi.bildirim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.artademi.reminder.BorcHatirlatmaService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Zamanlanmis bildirim isinin MULTI-TENANT davranisi.
 *
 * <p>Burada kilitlenen sey mail metni degil, isin tenant baglamini DOGRU yonetmesidir:
 * zamanlanmis is bir istekten dogmadigi icin baglam bostur ve is onu kendisi kurar. Yanlis
 * yonetilirse ya hicbir sey calismaz (fail-closed filtre) ya da — cok daha kotusu — bir
 * kurumun isi baska kurumun verisiyle calisir.
 */
@ExtendWith(MockitoExtension.class)
class BildirimSchedulerTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock
    TenantRepository tenants;

    @Mock
    BildirimAyariService ayarlar;

    @Mock
    OtomatikBildirimService bildirimler;

    @Mock
    BorcHatirlatmaService borcHatirlatma;

    @InjectMocks
    BildirimScheduler scheduler;

    @BeforeEach
    void temizle() {
        TenantContext.clear();
    }

    @AfterEach
    void sonrasindaTemizle() {
        TenantContext.clear();
    }

    private static Tenant tenant(UUID id, String ad) {
        Tenant t = Tenant.create(ad);
        // Tenant.id veritabaninda uretilir; testte yansima ile set edilir.
        try {
            var f = Tenant.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return t;
    }

    private static BildirimAyari ayar(boolean borc, boolean devamsizlik, boolean ozet, int gun) {
        BildirimAyari a = BildirimAyari.varsayilan();
        a.setBorcHatirlatmaOtomatik(borc);
        a.setDevamsizlikBildirimi(devamsizlik);
        a.setHaftalikOzet(ozet);
        a.setHaftalikOzetGunu((short) gun);
        return a;
    }

    @Test
    void herKurumIcinKendiBaglami_kurulur() {
        // ⚠️ Isin kalbi: A'nin isi A baglaminda, B'nin isi B baglaminda calismali.
        given(tenants.findByStatus(TenantStatus.AKTIF))
                .willReturn(List.of(tenant(A, "A Kurumu"), tenant(B, "B Kurumu")));
        given(ayarlar.aktifAyar()).willReturn(ayar(false, true, false, 1));

        List<UUID> gorulenBaglamlar = new ArrayList<>();
        given(bildirimler.devamsizlikBildirimleri(any())).willAnswer(inv -> {
            gorulenBaglamlar.add(TenantContext.get());
            return 0;
        });

        scheduler.devamsizlikJobu();

        assertThat(gorulenBaglamlar).containsExactly(A, B);
    }

    @Test
    void baglamIsSonundaTEMIZLENIR() {
        given(tenants.findByStatus(TenantStatus.AKTIF)).willReturn(List.of(tenant(A, "A")));
        given(ayarlar.aktifAyar()).willReturn(ayar(false, true, false, 1));
        given(bildirimler.devamsizlikBildirimleri(any())).willReturn(0);

        scheduler.devamsizlikJobu();

        // Sizarsa bir sonraki job/istek YANLIS tenant'ta calisir.
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void birKurumunHatasi_digerleriniDURDURMAZ() {
        given(tenants.findByStatus(TenantStatus.AKTIF))
                .willReturn(List.of(tenant(A, "A"), tenant(B, "B")));
        given(ayarlar.aktifAyar()).willReturn(ayar(false, true, false, 1));

        List<UUID> islenen = new ArrayList<>();
        given(bildirimler.devamsizlikBildirimleri(any())).willAnswer(inv -> {
            UUID simdiki = TenantContext.get();
            islenen.add(simdiki);
            if (A.equals(simdiki)) {
                throw new IllegalStateException("A patladı");
            }
            return 1;
        });

        scheduler.devamsizlikJobu();

        // A patlasa da B islenmeli; aksi halde tek bozuk kurum tum platformu susturur.
        assertThat(islenen).containsExactly(A, B);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void ayarKapaliysa_gonderimYAPILMAZ() {
        // Varsayilan KAPALI olmasi bu ozelligin sozlesmesi: kurum acmadikca veliye mail gitmez.
        given(tenants.findByStatus(TenantStatus.AKTIF)).willReturn(List.of(tenant(A, "A")));
        given(ayarlar.aktifAyar()).willReturn(ayar(false, false, false, 1));

        scheduler.devamsizlikJobu();

        verify(bildirimler, never()).devamsizlikBildirimleri(any());
    }

    @Test
    void haftalikOzet_yalnizcaSECILEN_gunGonderilir() {
        given(tenants.findByStatus(TenantStatus.AKTIF)).willReturn(List.of(tenant(A, "A")));
        // Kurum CARSAMBA (3) secmis; is SALI (2) gunu calisiyor.
        given(ayarlar.aktifAyar()).willReturn(ayar(false, false, true, 3));

        scheduler.sabahJobuCalistir(DayOfWeek.TUESDAY);

        verify(bildirimler, never()).haftalikOzet();
    }

    @Test
    void haftalikOzet_gunuGeldiginde_gonderilir() {
        given(tenants.findByStatus(TenantStatus.AKTIF)).willReturn(List.of(tenant(A, "A")));
        given(ayarlar.aktifAyar()).willReturn(ayar(false, false, true, 3));
        given(bildirimler.haftalikOzet()).willReturn(true);

        scheduler.sabahJobuCalistir(DayOfWeek.WEDNESDAY);

        verify(bildirimler).haftalikOzet();
    }

    @Test
    void borcHatirlatmasi_ayarAcikkenGonderilir() {
        given(tenants.findByStatus(TenantStatus.AKTIF)).willReturn(List.of(tenant(A, "A")));
        given(ayarlar.aktifAyar()).willReturn(ayar(true, false, false, 1));
        given(borcHatirlatma.otomatikGonder()).willReturn(3);

        scheduler.sabahJobuCalistir(DayOfWeek.MONDAY);

        verify(borcHatirlatma).otomatikGonder();
    }

    @Test
    void askidakiKurum_ISLENMEZ() {
        // findByStatus(AKTIF) sozlesmesi: askidaki kurumun velisine mail GITMEZ.
        given(tenants.findByStatus(TenantStatus.AKTIF)).willReturn(List.of());

        scheduler.devamsizlikJobu();
        scheduler.sabahJobuCalistir(DayOfWeek.MONDAY);

        verify(bildirimler, never()).devamsizlikBildirimleri(any(LocalDate.class));
        verify(borcHatirlatma, never()).otomatikGonder();
    }
}
