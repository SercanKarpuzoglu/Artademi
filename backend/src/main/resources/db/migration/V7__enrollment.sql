-- V7: Kayit (enrollment) — ogrenci <-> grup yazma iliskisi. Tenant-aware: her satir tenant_id
-- (UUID, NOT NULL) tasir ve global Hibernate tenant filtresine tabidir. tenant_id ASLA istemciden
-- gelmez; yazma sirasinda TenantContext'ten otomatik set edilir.
--
-- Kayit, ogrenci + grup (ikisi de zorunlu) referanslari tasir. Bu referanslarin BASKA tenant'a ait
-- olamamasi uygulama katmaninda (findScopedById) garanti edilir; buradaki FK'lar yalnizca referans
-- butunlugu icindir, tenant izolasyonu DEGIL.
--
-- Silme YOK: kayit silinmez; ayrilma durum=AYRILDI + ayrilma_tarihi ile yapilir (veri korunur).
-- Ucret/tahsilat YOK (sonraki modul). Seed YOK.

CREATE TABLE enrollment (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ogrenci_id         BIGINT       NOT NULL REFERENCES students (id),
    grup_id            BIGINT       NOT NULL REFERENCES lesson_group (id),
    kayit_tarihi       DATE         NOT NULL,
    durum              VARCHAR(20)  NOT NULL,
    ayrilma_tarihi     DATE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_enrollment_tenant ON enrollment (tenant_id);
CREATE INDEX idx_enrollment_tenant_grup ON enrollment (tenant_id, grup_id);
CREATE INDEX idx_enrollment_tenant_ogrenci ON enrollment (tenant_id, ogrenci_id);

-- Mukerrer AKTIF kaydi DB duzeyinde de engelle: ayni tenant'ta ayni ogrenci+grup icin yalnizca
-- bir AKTIF kayit olabilir. AYRILDI kayitlar bu kisitlamaya girmez (partial unique index), boylece
-- ayrilan ogrenci ayni gruba tekrar yazilabilir.
CREATE UNIQUE INDEX uq_enrollment_aktif
    ON enrollment (tenant_id, ogrenci_id, grup_id)
    WHERE durum = 'AKTIF';
