-- V12: Stok/Urun Satisi (inventory) — urun (product) ve satis (sale). Tenant-aware: her satir
-- tenant_id (UUID, NOT NULL) tasir ve global Hibernate tenant filtresine tabidir. tenant_id ASLA
-- istemciden gelmez; yazma sirasinda TenantContext'ten otomatik set edilir.
--
-- PARA KURALI: tum parasal alanlar NUMERIC(12,2) (BigDecimal). Stok adetleri INTEGER. Asla
-- double/float kullanilmaz.
--
-- product: satilabilir urun tanimi. satis_fiyati guncel fiyat (>0); stok_adedi eldeki adet (>=0).
-- Silme YOK: kayit silinmez, aktif alani ile pasiflestirilir.
--
-- sale: bir urunun (urun_id, zorunlu) satisi. Opsiyonel olarak bir ogrenciye (ogrenci_id)
-- baglanabilir. birim_fiyat satis aninda product.satis_fiyati'ndan KOPYALANIR ve sonradan urun fiyati
-- degisse bile DEGISMEZ. toplam_tutar = birim_fiyat * adet. Satista stok dusurulur (ayni transaction;
-- yetersiz stokta satir olusmaz, stok degismez — uygulama katmaninda kontrol). Satis degismez/silinmez.
--
-- Referanslarin (urun, ogrenci) BASKA tenant'a ait olamamasi uygulama katmaninda (findScopedById)
-- garanti edilir; buradaki FK'lar yalnizca referans butunlugu icindir, tenant izolasyonu DEGIL.
--
-- Seed YOK.

CREATE TABLE product (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    ad                 VARCHAR(150)  NOT NULL,
    satis_fiyati       NUMERIC(12,2) NOT NULL,
    stok_adedi         INTEGER       NOT NULL DEFAULT 0,
    aciklama           TEXT,
    aktif              BOOLEAN       NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_product_tenant ON product (tenant_id);

CREATE TABLE sale (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    urun_id            BIGINT        NOT NULL REFERENCES product (id),
    ogrenci_id         BIGINT        REFERENCES students (id),
    adet               INTEGER       NOT NULL,
    birim_fiyat        NUMERIC(12,2) NOT NULL,
    toplam_tutar       NUMERIC(12,2) NOT NULL,
    satis_tarihi       DATE          NOT NULL,
    aciklama           TEXT,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_sale_tenant ON sale (tenant_id);
CREATE INDEX idx_sale_tenant_urun ON sale (tenant_id, urun_id);
CREATE INDEX idx_sale_tenant_ogrenci ON sale (tenant_id, ogrenci_id);
CREATE INDEX idx_sale_tenant_tarih ON sale (tenant_id, satis_tarihi);
