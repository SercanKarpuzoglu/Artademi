-- V24: Online on kayit (basvuru) formu.
--
-- Kurum, kendi public basvuru baglantisini paylasir; veli JWT OLMADAN form doldurur.
-- Gelen kayit ilgili kurumun basvuru listesine duser ve oradan ogrenciye donusturulebilir.
--
-- ⚠️ GUVENLIK NOTU: basvuru ucu kimliksizdir ve tenant URL'deki SLUG'dan cozulur.
-- Bu, "tenant yalnizca JWT'den okunur" kuralinin BILINCLI ve DAR bir istisnasidir;
-- gerekcesi ve sinirlari PublicBasvuruController javadoc'unda yazilidir.

-- 1) Kurumun public baglanti adi. NULL = kurum bu ozelligi ACMAMIS (form kapali).
ALTER TABLE tenant ADD COLUMN basvuru_slug VARCHAR(60);

-- Kismi unique indeks: slug benzersiz olmali ama NULL'lar cakismamalidir
-- (ozelligi acmayan kurumlarin hepsi NULL tasir).
CREATE UNIQUE INDEX uq_tenant_basvuru_slug ON tenant (basvuru_slug)
    WHERE basvuru_slug IS NOT NULL;

-- 2) Basvuru kayitlari (tenant is tablosu).
CREATE TABLE basvuru (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    ad                 VARCHAR(100) NOT NULL,
    soyad              VARCHAR(100) NOT NULL,
    telefon            VARCHAR(30)  NOT NULL,
    email              VARCHAR(255),
    veli_adi           VARCHAR(200),
    -- Ilgilenilen brans: form acilirken kurumun AKTIF branslari listelenir.
    -- NULLABLE: veli "bilmiyorum" diyebilir ya da kurum hic brans tanimlamamis olabilir.
    brans_id           BIGINT       REFERENCES branches (id),
    mesaj              VARCHAR(1000),
    durum              VARCHAR(20)  NOT NULL DEFAULT 'YENI',
    -- Ogrenciye donusturulduyse olusan ogrenci. Basvuru SILINMEZ; izi korunur.
    ogrenci_id         BIGINT       REFERENCES students (id),
    -- Kotuye kullanim incelemesi icin kaynak IP (KVKK: kisisel veri, disa aktarmada yer alir).
    kaynak_ip          VARCHAR(45),
    olusturulma_tarihi TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi TIMESTAMPTZ  NOT NULL
);

-- Bilesik indekslerin ilk kolonu tenant_id (cogu sorgu onunla filtrelenir).
CREATE INDEX idx_basvuru_tenant ON basvuru (tenant_id);
CREATE INDEX idx_basvuru_tenant_durum ON basvuru (tenant_id, durum);
-- Liste varsayilan siralamasi: en yeni basvuru once.
CREATE INDEX idx_basvuru_tenant_tarih ON basvuru (tenant_id, olusturulma_tarihi DESC);

-- ⚠️ FK'ler ayni tenant'i GARANTI ETMEZ; brans_id/ogrenci_id atamalari serviste
-- findScopedById ile dogrulanir (capraz-tenant referans kurali).
