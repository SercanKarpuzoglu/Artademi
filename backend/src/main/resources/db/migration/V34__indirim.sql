-- V34: Indirim / kampanya (Dalga D, 9 Eylul talepleri).
--
-- indirim_tanimi: kurumun tanimladigi indirim turleri ("Kardes indirimi %15", "Nakit odeme %10", "Burs 500 TL").
-- ogrenci_indirimi: tanimin OGRENCIYE OZEL atamasi — grup_id NULL ise tum gruplar; tarih araligi (bitis NULL = surekli).
-- Uygulama: otomatik aylik tahakkukta brut (grup ucreti) - indirim = NET; accrual.tutar NET olarak yazilir (tum
-- mevcut tuketiciler net'i gorur), brut ve indirim ayrica saklanir ki makbuz/listede "500 - 75 = 425" gosterilsin.
-- Birden cok atama: oranlar TOPLANIR (brut uzerinden), tutarlar toplanir; indirim brut'u asamaz (net >= 0).
CREATE TABLE indirim_tanimi (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    ad                 VARCHAR(120)  NOT NULL,
    tip                VARCHAR(10)   NOT NULL,                -- ORAN (yuzde) | TUTAR (TL)
    deger              NUMERIC(12,2) NOT NULL,
    aciklama           VARCHAR(500),
    aktif              BOOLEAN       NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ   NOT NULL DEFAULT now(),
    guncellenme_tarihi TIMESTAMPTZ   NOT NULL DEFAULT now(),
    silindi_tarihi     TIMESTAMPTZ,
    silen              VARCHAR(100)
);
CREATE INDEX ix_indirim_tanimi_tenant ON indirim_tanimi (tenant_id);

CREATE TABLE ogrenci_indirimi (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ogrenci_id         BIGINT       NOT NULL REFERENCES students (id),
    indirim_id         BIGINT       NOT NULL REFERENCES indirim_tanimi (id),
    grup_id            BIGINT       REFERENCES lesson_group (id),   -- NULL = ogrencinin tum gruplari
    baslangic          DATE         NOT NULL,
    bitis              DATE,                                        -- NULL = surekli
    aciklama           VARCHAR(500),
    aktif              BOOLEAN      NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL DEFAULT now(),
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_ogrenci_indirimi_ogrenci ON ogrenci_indirimi (tenant_id, ogrenci_id);

ALTER TABLE accrual
    ADD COLUMN brut_tutar       NUMERIC(12,2),
    ADD COLUMN indirim_tutar    NUMERIC(12,2),
    ADD COLUMN indirim_aciklama VARCHAR(500);
