-- V10: Tahsilat/Muhasebe (finance) — tahakkuk (accrual), odeme (payment), gider (expense).
-- Tenant-aware: her satir tenant_id (UUID, NOT NULL) tasir ve global Hibernate tenant filtresine
-- tabidir. tenant_id ASLA istemciden gelmez; yazma sirasinda TenantContext'ten otomatik set edilir.
--
-- PARA KURALI: tum parasal alanlar NUMERIC(12,2) (BigDecimal). Asla double/float kullanilmaz.
--
-- accrual: bir ogrenciye (ogrenci_id, zorunlu) kesilen borc/tahakkuk. Opsiyonel grup (grup_id) ve
-- donem (donem, "YYYY-MM") ile iliskilendirilebilir.
--
-- payment: bir ogrenciden (ogrenci_id, zorunlu) alinan tahsilat. Opsiyonel olarak bir tahakkuga
-- (accrual_id) ve/veya gruba (grup_id) baglanabilir. odeme_yontemi NAKIT/KART/HAVALE.
--
-- expense: kurumun gideri (ogrenci/gruba bagli DEGIL). Serbest kategori metni.
--
-- Referanslarin (ogrenci, grup, accrual) BASKA tenant'a ait olamamasi uygulama katmaninda
-- (findScopedById) garanti edilir; buradaki FK'lar yalnizca referans butunlugu icindir, tenant
-- izolasyonu DEGIL. Bakiye = SUM(accrual.tutar) - SUM(payment.tutar) (ogrenci bazinda, tenant-filtreli).
--
-- Silme YOK. Seed YOK.

CREATE TABLE accrual (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    ogrenci_id         BIGINT        NOT NULL REFERENCES students (id),
    grup_id            BIGINT        REFERENCES lesson_group (id),
    donem              VARCHAR(7),
    tutar              NUMERIC(12,2) NOT NULL,
    aciklama           TEXT,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_accrual_tenant ON accrual (tenant_id);
CREATE INDEX idx_accrual_tenant_ogrenci ON accrual (tenant_id, ogrenci_id);
CREATE INDEX idx_accrual_tenant_donem ON accrual (tenant_id, donem);

CREATE TABLE payment (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    ogrenci_id         BIGINT        NOT NULL REFERENCES students (id),
    accrual_id         BIGINT        REFERENCES accrual (id),
    grup_id            BIGINT        REFERENCES lesson_group (id),
    tutar              NUMERIC(12,2) NOT NULL,
    odeme_tarihi       DATE          NOT NULL,
    odeme_yontemi      VARCHAR(20)   NOT NULL,
    aciklama           TEXT,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_payment_tenant ON payment (tenant_id);
CREATE INDEX idx_payment_tenant_ogrenci ON payment (tenant_id, ogrenci_id);
CREATE INDEX idx_payment_tenant_tarih ON payment (tenant_id, odeme_tarihi);

CREATE TABLE expense (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    tutar              NUMERIC(12,2) NOT NULL,
    gider_tarihi       DATE          NOT NULL,
    kategori           VARCHAR(100),
    aciklama           TEXT,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_expense_tenant ON expense (tenant_id);
CREATE INDEX idx_expense_tenant_tarih ON expense (tenant_id, gider_tarihi);
