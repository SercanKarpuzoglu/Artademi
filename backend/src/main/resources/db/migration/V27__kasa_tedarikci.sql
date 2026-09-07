-- V27: Kasa (nakit/banka hesabi) + kasa hareketi + tedarikci.
--
-- Rekabet analizindeki finans derinligi farki: rakipte kasa tanimi, tedarikci/cari ve
-- donemsel kar-zarar var; bizde yalnizca tahakkuk/tahsilat/gider vardi.
--
-- ⚠️ BAKIYE SAKLANMAZ, HESAPLANIR. Saklanan bakiye zamanla gercekten sapar (bir tahsilat
-- elle duzeltilir, bir gider silinir, guncelleme kacar). Kasa bakiyesi her zaman
-- acilis + tahsilatlar - giderler + hareket girisleri - hareket cikislari olarak sorgulanir.

CREATE TABLE kasa (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID           NOT NULL,
    ad                 VARCHAR(150)   NOT NULL,
    -- NAKIT | BANKA. Banka hesabinda IBAN tutulabilsin diye ayri alan var.
    tip                VARCHAR(20)    NOT NULL,
    iban               VARCHAR(34),
    -- Sisteme gecmeden onceki mevcut bakiye. Gecmis hareketleri girmek zorunda
    -- kalmadan dogru bakiye gostermenin tek yolu budur.
    acilis_bakiyesi    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    aktif              BOOLEAN        NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ    NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_kasa_tenant ON kasa (tenant_id);
CREATE UNIQUE INDEX uq_kasa_tenant_ad ON kasa (tenant_id, ad);

-- Tahsilat ve gider DISINDAKI kasa hareketleri: kasalar arasi transfer ve elle duzeltme.
--
-- ⚠️ Transfer TEK satir DEGIL, IKI satirdir (kaynakta CIKIS, hedefte GIRIS) ve ayni
-- transfer_grubu degerini tasirlar. Boylece bakiye sorgusu duz bir toplamdir; tek satirla
-- yapilsaydi her bakiye sorgusu "bu satir bana giris mi cikis mi" diye iki yone bakmak
-- zorunda kalirdi. Silme de grup uzerinden yapilir, yarim transfer kalmaz.
CREATE TABLE kasa_hareketi (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID           NOT NULL,
    kasa_id            BIGINT         NOT NULL REFERENCES kasa (id),
    -- GIRIS | CIKIS  (yon bu alanda; tutar HER ZAMAN pozitiftir)
    yon                VARCHAR(10)    NOT NULL,
    -- TRANSFER | DUZELTME
    tip                VARCHAR(20)    NOT NULL,
    tutar              NUMERIC(12, 2) NOT NULL CHECK (tutar > 0),
    tarih              DATE           NOT NULL,
    aciklama           VARCHAR(500),
    -- Transferin iki bacagini birbirine baglar; DUZELTME'de NULL.
    transfer_grubu     UUID,
    olusturulma_tarihi TIMESTAMPTZ    NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_kasa_hareketi_tenant ON kasa_hareketi (tenant_id);
CREATE INDEX idx_kasa_hareketi_kasa ON kasa_hareketi (tenant_id, kasa_id);
CREATE INDEX idx_kasa_hareketi_grup ON kasa_hareketi (transfer_grubu);

CREATE TABLE tedarikci (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ad                 VARCHAR(200) NOT NULL,
    telefon            VARCHAR(30),
    email              VARCHAR(255),
    vergi_no           VARCHAR(20),
    aciklama           VARCHAR(500),
    aktif              BOOLEAN      NOT NULL DEFAULT TRUE,
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_tedarikci_tenant ON tedarikci (tenant_id);
CREATE UNIQUE INDEX uq_tedarikci_tenant_ad ON tedarikci (tenant_id, ad);

-- Tahsilat ve gider hangi kasaya islendi? Gider kime odendi?
--
-- ⚠️ NULLABLE, bilincli: (a) mevcut kayitlarin kasasi yoktur, NOT NULL migration'i calisan
-- kurumlari bozar; (b) kasa kullanmak ZORUNLU degil — tek kasayla calisan kurum hic kasa
-- tanimlamadan devam edebilmelidir.
--
-- ⚠️ FK ayni tenant'i GARANTI ETMEZ; atamalar serviste findScopedById ile dogrulanir
-- (capraz-tenant referans kurali).
ALTER TABLE payment ADD COLUMN kasa_id BIGINT REFERENCES kasa (id);
ALTER TABLE expense ADD COLUMN kasa_id BIGINT REFERENCES kasa (id);
ALTER TABLE expense ADD COLUMN tedarikci_id BIGINT REFERENCES tedarikci (id);

CREATE INDEX idx_payment_tenant_kasa ON payment (tenant_id, kasa_id);
CREATE INDEX idx_expense_tenant_kasa ON expense (tenant_id, kasa_id);
CREATE INDEX idx_expense_tenant_tedarikci ON expense (tenant_id, tedarikci_id);
