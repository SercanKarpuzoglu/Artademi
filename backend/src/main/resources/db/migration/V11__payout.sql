-- V11: Hakedis (payout) — ogretmen donemlik kazanc kaydi. Tenant-aware: her satir tenant_id (UUID,
-- NOT NULL) tasir ve global Hibernate tenant filtresine tabidir. tenant_id ASLA istemciden gelmez;
-- yazma sirasinda TenantContext'ten otomatik set edilir.
--
-- PARA KURALI: tum parasal alanlar NUMERIC(12,2) (BigDecimal); oran alanlari NUMERIC(5,2). Asla
-- double/float kullanilmaz.
--
-- payout: bir ogretmenin (ogretmen_id, zorunlu) belirli bir donemdeki (donem, "YYYY-MM") hakedisi.
-- hakedis_tipi ogretmenden hesaplama aninda KOPYALANIR (SAATLIK | CIRO_ORANI). Hesaplama dokumu tipe
-- gore farkli alanlarda tutulur:
--   SAATLIK     -> ders_sayisi (oturum sayisi), birim_ucret (saatlik ucret).
--   CIRO_ORANI  -> toplam_tahsilat, kdv_orani, net_ciro (KDV haric ciro), oran (kullanilan ciro orani).
-- hesaplanan_tutar her tipte doludur. durum HESAPLANDI ile baslar; odendiginde ODENDI + odeme_tarihi.
--
-- Referansin (ogretmen) BASKA tenant'a ait olamamasi uygulama katmaninda (findScopedById) garanti
-- edilir; buradaki FK yalnizca referans butunlugu icindir, tenant izolasyonu DEGIL.
--
-- Mukerrer engeli: ayni tenant'ta ayni ogretmen + donem icin tek hakediş (UNIQUE kisit + serviste 409).
--
-- Silme YOK. Seed YOK.

CREATE TABLE payout (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    ogretmen_id        BIGINT        NOT NULL REFERENCES teachers (id),
    donem              VARCHAR(7)    NOT NULL,
    hakedis_tipi       VARCHAR(20)   NOT NULL,
    hesaplanan_tutar   NUMERIC(12,2) NOT NULL,
    ders_sayisi        INTEGER,
    birim_ucret        NUMERIC(12,2),
    toplam_tahsilat    NUMERIC(12,2),
    kdv_orani          NUMERIC(5,2),
    net_ciro           NUMERIC(12,2),
    oran               NUMERIC(5,2),
    durum              VARCHAR(20)   NOT NULL,
    odeme_tarihi       DATE,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_payout_tenant_ogretmen_donem UNIQUE (tenant_id, ogretmen_id, donem)
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_payout_tenant ON payout (tenant_id);
CREATE INDEX idx_payout_tenant_ogretmen ON payout (tenant_id, ogretmen_id);
CREATE INDEX idx_payout_tenant_donem ON payout (tenant_id, donem);
