-- V29: Ders paketi (kontor) satisi.
--
-- Ucuncu fiyatlandirma modeli. Mevcut ikisi grup uzerindeydi: aylik_aidat ve ders_basi_ucret.
-- Paket ise OGRENCI uzerindedir: "10 derslik bale paketi, 4.000 TL".
--
-- ⚠️ KALAN DERS SAKLANMAZ, HESAPLANIR: toplam_ders - (o pakete ait kullanim satiri sayisi).
-- Sayac tutulsaydi yoklama duzeltmelerinde (GELDI -> IZINLI) saparadi ve sapma sessiz olurdu.
--
-- ⚠️ KONTOR DUSUMU YOKLAMA DURUMUNA BAGLIDIR:
--   GELDI   -> duser
--   GELMEDI -> DUSER  (habersiz gelmeme; okul dersi tahsis etti)
--   IZINLI  -> dusmez (onceden haber verilmis)
-- Bu ayrim mevcut YoklamaDurumu'yla birebir ortusuyor; yeni bir "haber verdi mi" alani
-- eklemeye gerek kalmadi.

CREATE TABLE ders_paketi (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    ogrenci_id          BIGINT         NOT NULL REFERENCES students (id),
    ad                  VARCHAR(150)   NOT NULL,

    -- Pakete bagli grup (opsiyonel). Doluysa kontor once bu grubun derslerinden duser;
    -- ogrencinin birden fazla paketi varsa dogru paketin tuketilmesini saglar.
    grup_id             BIGINT         REFERENCES lesson_group (id),

    toplam_ders         INTEGER        NOT NULL CHECK (toplam_ders > 0),
    tutar               NUMERIC(12, 2) NOT NULL CHECK (tutar >= 0),
    satis_tarihi        DATE           NOT NULL,
    -- NULL = suresiz.
    son_kullanma_tarihi DATE,

    -- Satista uretilen PESIN tahakkuk. Paket iptal edilirse tahakkuk kurumun kararina
    -- birakilir (otomatik silinmez — tahsilat yapilmis olabilir).
    accrual_id          BIGINT         REFERENCES accrual (id),

    -- AKTIF | IPTAL   (BITTI YOK: kalan ders hesaplanir, durum degil)
    durum               VARCHAR(20)    NOT NULL DEFAULT 'AKTIF',
    aciklama            VARCHAR(500),
    olusturulma_tarihi  TIMESTAMPTZ    NOT NULL,
    guncellenme_tarihi  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_ders_paketi_tenant ON ders_paketi (tenant_id);
CREATE INDEX idx_ders_paketi_tenant_ogrenci ON ders_paketi (tenant_id, ogrenci_id);
CREATE INDEX idx_ders_paketi_tenant_durum ON ders_paketi (tenant_id, durum);

-- Tuketilen her ders bir satir. Sayac yerine satir tutmak, yoklama duzeltmelerini
-- (GELDI -> IZINLI) dogru yansitmayi mumkun kilar: satir silinir, kalan geri gelir.
CREATE TABLE paket_kullanim (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID        NOT NULL,
    paket_id           BIGINT      NOT NULL REFERENCES ders_paketi (id),
    oturum_id          BIGINT      NOT NULL REFERENCES attendance_session (id),
    ogrenci_id         BIGINT      NOT NULL REFERENCES students (id),
    kullanim_tarihi    DATE        NOT NULL,
    olusturulma_tarihi TIMESTAMPTZ NOT NULL
);

-- ⚠️ Ogrenci basina oturum basina EN FAZLA BIR kontor. Anahtar paket_id DEGIL ogrenci_id:
-- ogrencinin iki paketi varsa ayni dersten iki kontor dusmemelidir.
CREATE UNIQUE INDEX uq_paket_kullanim_ogrenci_oturum
    ON paket_kullanim (tenant_id, ogrenci_id, oturum_id);
CREATE INDEX idx_paket_kullanim_paket ON paket_kullanim (tenant_id, paket_id);

-- ⚠️ FK'ler ayni tenant'i GARANTI ETMEZ; atamalar serviste findScopedById ile dogrulanir.
