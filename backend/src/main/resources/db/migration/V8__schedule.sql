-- V8: Program / haftalik ders saati (schedule). Tenant-aware: her satir tenant_id (UUID, NOT NULL)
-- tasir ve global Hibernate tenant filtresine tabidir. tenant_id ASLA istemciden gelmez; yazma
-- sirasinda TenantContext'ten otomatik set edilir.
--
-- Her kayit bir gruba (grup_id, zorunlu) ait tek bir gun + saat araligini temsil eder. Grubun BASKA
-- tenant'a ait olamamasi uygulama katmaninda (group findScopedById) garanti edilir; buradaki FK
-- yalnizca referans butunlugu icindir, tenant izolasyonu DEGIL.
--
-- Cakisma kurallari (ayni salon/ogretmen + ayni gun + saat ortusmesi) servis katmaninda uygulanir.
--
-- Silme YOK: program silinmez, "aktif" alani ile pasiflestirilir. Seed YOK.

CREATE TABLE schedule (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    grup_id            BIGINT       NOT NULL REFERENCES lesson_group (id),
    gun                VARCHAR(20)  NOT NULL,
    baslangic_saati    TIME         NOT NULL,
    bitis_saati        TIME         NOT NULL,
    aktif              BOOLEAN      NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_schedule_tenant ON schedule (tenant_id);
-- Cakisma sorgularini hizlandirmak icin: tenant + grup + gun.
CREATE INDEX idx_schedule_tenant_grup_gun ON schedule (tenant_id, grup_id, gun);
