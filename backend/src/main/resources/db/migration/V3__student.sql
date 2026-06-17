-- V3: Ogrenci Islemleri (2c-1) — ilk gercek is modulu.
-- Tenant-aware: her satir tenant_id (UUID, NOT NULL) tasir ve global Hibernate
-- tenant filtresine tabidir (bkz. multi-tenancy skill). tenant_id ASLA istemciden
-- gelmez; yazma sirasinda TenantContext'ten otomatik set edilir.
--
-- Veli bilgisi ogrenci ICINDE tutulur (ayri tablo YOK); iki veli olabilir (anne + baba).
-- Kardesler veli TC'si uzerinden eslesir (anne_tc_kimlik_no / baba_tc_kimlik_no).
--
-- Silme YOK: kayit silinmez, statu PASIF'e alinir (veri korunur).
-- Seed YOK.

CREATE TABLE students (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           UUID         NOT NULL,

    -- Zorunlu temel alanlar
    ad                  VARCHAR(100) NOT NULL,
    soyad               VARCHAR(100) NOT NULL,
    tc_kimlik_no        VARCHAR(11)  NOT NULL,
    dogum_tarihi        DATE         NOT NULL,

    -- Opsiyonel
    telefon             VARCHAR(20),
    yetiskin_mi         BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Statu (enum: AKTIF, PASIF, DENEME, DONDURULMUS); yeni kayit varsayilan DENEME
    status              VARCHAR(20)  NOT NULL,

    -- Veli bilgisi (ogrenci icinde, hepsi opsiyonel)
    anne_ad             VARCHAR(100),
    anne_tc_kimlik_no   VARCHAR(11),
    anne_telefon        VARCHAR(20),
    baba_ad             VARCHAR(100),
    baba_tc_kimlik_no   VARCHAR(11),
    baba_telefon        VARCHAR(20),
    veli_meslek         VARCHAR(150),
    ev_adresi           VARCHAR(500),
    veli_mail           VARCHAR(255),

    -- Sistem alanlari (Hibernate @CreationTimestamp / @UpdateTimestamp)
    olusturulma_tarihi  TIMESTAMPTZ  NOT NULL,
    guncellenme_tarihi  TIMESTAMPTZ  NOT NULL
);

-- Cogu sorgu tenant_id ile filtrelendigi icin bilesik indekslerin ilk kolonu tenant_id.
CREATE INDEX idx_students_tenant ON students (tenant_id);

-- Statu filtresi (?status=AKTIF) tenant kapsaminda calisir.
CREATE INDEX idx_students_tenant_status ON students (tenant_id, status);

-- Kardes eslestirme veli TC'si uzerinden; tenant kapsaminda aranir.
CREATE INDEX idx_students_tenant_anne_tc ON students (tenant_id, anne_tc_kimlik_no);
CREATE INDEX idx_students_tenant_baba_tc ON students (tenant_id, baba_tc_kimlik_no);
