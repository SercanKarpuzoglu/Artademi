-- V6: Grup/Sinif (lesson_group). Tenant-aware: her satir tenant_id (UUID, NOT NULL) tasir ve
-- global Hibernate tenant filtresine tabidir. tenant_id ASLA istemciden gelmez; yazma sirasinda
-- TenantContext'ten otomatik set edilir.
--
-- Tablo adi "lesson_group" cunku "group" SQL'de rezerve kelimedir.
--
-- Grup, brans + ogretmen (zorunlu) ve salon (GRUP'ta zorunlu, OZEL'de opsiyonel) referanslari
-- tasir. Bu referanslarin BASKA tenant'a ait olamamasi uygulama katmaninda (findScopedById)
-- garanti edilir; buradaki FK'lar yalnizca referans butunlugu icindir, tenant izolasyonu DEGIL.
--
-- Silme YOK: grup silinmez, "aktif" alani ile pasiflestirilir. Seed YOK.

CREATE TABLE lesson_group (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    ad                 VARCHAR(150)  NOT NULL,
    tip                VARCHAR(20)   NOT NULL,
    brans_id           BIGINT        NOT NULL REFERENCES branches (id),
    ogretmen_id        BIGINT        NOT NULL REFERENCES teachers (id),
    salon_id           BIGINT        REFERENCES rooms (id),
    seviye             VARCHAR(100),
    aylik_aidat        NUMERIC(10, 2),
    ders_basi_ucret    NUMERIC(10, 2),
    aktif              BOOLEAN       NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_lesson_group_tenant ON lesson_group (tenant_id);
CREATE INDEX idx_lesson_group_tenant_brans ON lesson_group (tenant_id, brans_id);
CREATE INDEX idx_lesson_group_tenant_ogretmen ON lesson_group (tenant_id, ogretmen_id);
CREATE INDEX idx_lesson_group_tenant_salon ON lesson_group (tenant_id, salon_id);
