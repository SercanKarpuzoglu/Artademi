package com.artademi.bildirim;

import com.artademi.bildirim.dto.BildirimAyariRequest;
import com.artademi.bildirim.dto.BildirimAyariResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kurumun bildirim tercihleri.
 *
 * <p>Ayar satiri ILK GUNCELLEMEDE olusur; okuma sirasinda yazma YAPILMAZ. Aksi halde her
 * salt-okuma istegi (ve zamanlanmis isin her turu) yazma islemi tetikler ve
 * {@code @Transactional(readOnly = true)} sozlesmesi bozulurdu.
 */
@Service
public class BildirimAyariService {

    private final BildirimAyariRepository repository;

    public BildirimAyariService(BildirimAyariRepository repository) {
        this.repository = repository;
    }

    /** Kurumun ayari; hic kaydedilmemisse varsayilan (hepsi KAPALI) doner. */
    @Transactional(readOnly = true)
    public BildirimAyari aktifAyar() {
        return repository.aktifTenantAyari().orElseGet(BildirimAyari::varsayilan);
    }

    @Transactional(readOnly = true)
    public BildirimAyariResponse get() {
        return BildirimAyariResponse.from(aktifAyar());
    }

    @Transactional
    public BildirimAyariResponse guncelle(BildirimAyariRequest req) {
        BildirimAyari ayar = repository.aktifTenantAyari().orElseGet(BildirimAyari::varsayilan);
        ayar.setBorcHatirlatmaOtomatik(req.borcHatirlatmaOtomatik());
        ayar.setDevamsizlikBildirimi(req.devamsizlikBildirimi());
        ayar.setHaftalikOzet(req.haftalikOzet());
        ayar.setHaftalikOzetGunu(req.haftalikOzetGunu());
        ayar.setYoklamaAlinmadiEposta(req.yoklamaAlinmadiEposta());
        return BildirimAyariResponse.from(repository.save(ayar));
    }
}
