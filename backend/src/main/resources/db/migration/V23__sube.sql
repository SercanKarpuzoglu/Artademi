-- V23: Sube (fiziksel lokasyon) tanim tablosu + salon/grup baglantilari.
--
-- ⚠️ ISIMLENDIRME TUZAGI: bu tablo SUBE'dir (fiziksel lokasyon — "Kadikoy Subesi").
-- Mevcut "branches" tablosu BRANS'tir (Bale, Piyano, Gitar). Ikisi FARKLI kavramlardir;
-- Java tarafinda da Sube (com.artademi.sube) ve Branch (com.artademi.branch) ayridir.
--
-- Silme YOK: kayitlar silinmez, "aktif" alani ile pasiflestirilir.
-- Seed YOK.

CREATE TABLE sube (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ad                 VARCHAR(150) NOT NULL,
    adres              VARCHAR(500),
    telefon            VARCHAR(30),
    aktif              BOOLEAN      NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_sube_tenant ON sube (tenant_id);
CREATE INDEX idx_sube_tenant_ad ON sube (tenant_id, ad);

-- Salon ve grup hangi subede calisiyor?
--
-- NULLABLE olmasi BILINCLI: (a) mevcut kayitlarin subesi yoktur, NOT NULL migration'i
-- calisan kurumlari bozar; (b) tek subeli kurumlar hic sube tanimlamadan calismaya
-- devam edebilmelidir — sube kavramini kullanmak ZORUNLU degil, opsiyoneldir.
--
-- FK ayni tenant'i GARANTI ETMEZ; sube_id atamasi serviste findScopedById ile
-- dogrulanir (capraz-tenant referans kurali).
ALTER TABLE rooms ADD COLUMN sube_id BIGINT REFERENCES sube (id);
ALTER TABLE lesson_group ADD COLUMN sube_id BIGINT REFERENCES sube (id);

CREATE INDEX idx_rooms_tenant_sube ON rooms (tenant_id, sube_id);
CREATE INDEX idx_lesson_group_tenant_sube ON lesson_group (tenant_id, sube_id);
